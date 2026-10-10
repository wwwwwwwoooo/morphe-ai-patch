/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.patches.lyrics.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/** Only RS256 signatures using the trusted issuer's HTTPS JWKS are accepted. */
public final class IdTokenValidator {
    public record Identity(String subject, String email) { }
    private IdTokenValidator() { }
    private static byte[] decode(String value) { return Base64.getUrlDecoder().decode(value); }
    public static Identity validate(String jwt, JSONObject jwks, String client, String nonce,
                                    String expectedSubject, long now) throws Exception {
        String[] parts = jwt.split("\\.", -1);
        if (parts.length != 3 || jwt.length() > 65_536) throw OAuthProtocol.protocol("invalid_id_token");
        JSONObject header = new JSONObject(new String(decode(parts[0]), StandardCharsets.UTF_8));
        if (!"RS256".equals(header.optString("alg")) || header.has("crit"))
            throw OAuthProtocol.protocol("unsupported_id_token_algorithm");
        String kid = header.getString("kid");
        JSONArray keys = jwks.getJSONArray("keys"); PublicKey key = null;
        for (int i = 0; i < keys.length(); i++) {
            JSONObject jwk = keys.getJSONObject(i);
            if (!kid.equals(jwk.optString("kid")) || !"RSA".equals(jwk.optString("kty"))) continue;
            if (!"sig".equals(jwk.optString("use", "sig"))
                    || !"RS256".equals(jwk.optString("alg", "RS256"))) continue;
            key = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                    new BigInteger(1, decode(jwk.getString("n"))), new BigInteger(1, decode(jwk.getString("e")))));
            break;
        }
        if (key == null) throw OAuthProtocol.protocol("unknown_signing_key");
        Signature verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(key);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!verifier.verify(decode(parts[2]))) throw OAuthProtocol.protocol("invalid_signature");
        JSONObject claims = new JSONObject(new String(decode(parts[1]), StandardCharsets.UTF_8));
        if (!OAuthProtocol.ISSUER.equals(claims.getString("iss"))) throw OAuthProtocol.protocol("issuer_mismatch");
        Object aud = claims.get("aud"); boolean matched = client.equals(aud);
        if (aud instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) matched |= client.equals(array.getString(i));
            if (array.length() > 1 && !client.equals(claims.optString("azp"))) matched = false;
        }
        if (!matched) throw OAuthProtocol.protocol("audience_mismatch");
        if (claims.has("azp") && !client.equals(claims.getString("azp"))) throw OAuthProtocol.protocol("azp_mismatch");
        if (claims.getLong("exp") <= now || claims.optLong("nbf", 0) > now + 30
                || claims.optLong("iat", 0) > now + 30) throw OAuthProtocol.protocol("expired_id_token");
        if (nonce != null && !OAuthProtocol.equal(nonce, claims.optString("nonce")))
            throw OAuthProtocol.protocol("nonce_mismatch");
        String sub = claims.getString("sub");
        if (sub.isEmpty() || (expectedSubject != null && !OAuthProtocol.equal(sub, expectedSubject)))
            throw OAuthProtocol.protocol("account_mismatch");
        return new Identity(sub, claims.optString("email", ""));
    }
}
