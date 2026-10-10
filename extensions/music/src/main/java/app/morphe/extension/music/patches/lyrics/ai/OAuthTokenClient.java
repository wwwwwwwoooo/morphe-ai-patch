/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.patches.lyrics.ai;

import org.json.JSONObject;
import java.util.Map;

/** Public-client token grants. No secret, scope escalation or automatic retries of rotating grants. */
public final class OAuthTokenClient {
    public interface Exchange { JSONObject post(String form, RequestCancellation cancel) throws Exception; }
    private final Exchange exchange;
    public OAuthTokenClient() {
        this((form, cancel) -> ApiTransport.json(OAuthProtocol.TOKEN, form,
                "application/x-www-form-urlencoded", null, cancel, false));
    }
    public OAuthTokenClient(Exchange exchange) { this.exchange = exchange; }
    public JSONObject code(OAuthProtocol.Attempt attempt, Map<String, String> callback,
                           RequestCancellation cancel) throws Exception {
        String issued = OAuthProtocol.callbackClient(attempt, callback);
        return exchange.post(ApiTransport.form(ApiTransport.fields("grant_type", "authorization_code", "client_id", issued,
                "code", callback.get("code"), "code_verifier", attempt.verifier(),
                "redirect_uri", attempt.redirect(), "resource", OAuthProtocol.RESOURCE)), cancel);
    }
    public JSONObject refresh(JSONObject record, RequestCancellation cancel) throws Exception {
        String client = record.getString("client_id");
        if (OAuthProtocol.DYNAMIC.equals(client)) throw OAuthProtocol.protocol("unregistered_client");
        return exchange.post(ApiTransport.form(ApiTransport.fields("grant_type", "refresh_token", "client_id", client,
                "refresh_token", record.getString("refresh_token"), "resource", OAuthProtocol.RESOURCE)), cancel);
    }
}
