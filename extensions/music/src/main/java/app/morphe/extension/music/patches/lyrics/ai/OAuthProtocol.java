/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.patches.lyrics.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OAuthProtocol {
    public static final String ISSUER = "https://auth.openai.com";
    public static final String AUTHORIZE = ISSUER + "/api/accounts/authorize";
    public static final String TOKEN = ISSUER + "/api/accounts/oauth/token";
    public static final String RESOURCE = "https://api.openai.com/v1";
    public static final String DIRECT = "chatgpt.tokens.use.direct";
    public static final String SCOPES = "openid profile email offline_access resource.invoke " + DIRECT;
    public static final String DYNAMIC = "dynamic_agent_client";
    public record Attempt(String state, String nonce, String verifier, String redirect,
                          String clientId, String subject) { }
    private OAuthProtocol() { }
    public static String random() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String challenge(String verifier) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    }
    public static boolean equal(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
    public static String authorize(Attempt a, String host, String idHint, boolean consent) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("client_id", a.clientId());
        if (DYNAMIC.equals(a.clientId())) values.put("agent_name_hint", "LyricBridge for YouTube Music");
        values.put("ext_agent_host_id", host);
        if (idHint != null && !idHint.isEmpty()) values.put("id_token_hint", idHint);
        values.put("response_type", "code"); values.put("redirect_uri", a.redirect());
        values.put("scope", SCOPES); values.put("resource", RESOURCE);
        values.put("state", a.state()); values.put("nonce", a.nonce());
        values.put("code_challenge_method", "S256"); values.put("code_challenge", challenge(a.verifier()));
        if (consent) values.put("prompt", "consent");
        return AUTHORIZE + "?" + ApiTransport.form(values);
    }
    public static String callbackClient(Attempt a, Map<String, String> query) throws AiException {
        if (!equal(a.state(), query.get("state"))) throw protocol("state_mismatch");
        if (query.containsKey("error")) throw new AiException(AiException.Kind.PERMISSION, 0, "oauth_denied", null);
        String id = query.get("client_id");
        if (DYNAMIC.equals(a.clientId())) {
            if (id == null || id.isEmpty() || DYNAMIC.equals(id)) throw protocol("registration_incomplete");
        } else {
            if (id != null && !equal(id, a.clientId())) throw protocol("client_mismatch");
            id = a.clientId();
        }
        if (query.getOrDefault("code", "").isEmpty()) throw protocol("missing_code");
        return id;
    }
    public static AiException protocol(String code) {
        return new AiException(AiException.Kind.PROTOCOL, 0, code, null);
    }
}
