/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.patches.lyrics.ai;

import org.json.JSONObject;
import java.util.Arrays;

/** Token-response validation shared by exchange and serialized refresh. */
public final class SessionTokens {
    private SessionTokens() { }
    public static boolean direct(JSONObject record) {
        return Arrays.asList(record.optString("scope").split("\\s+")).contains(OAuthProtocol.DIRECT);
    }
    public static JSONObject replace(JSONObject previous, JSONObject response, long now, boolean refresh) throws Exception {
        if (!"Bearer".equalsIgnoreCase(response.getString("token_type"))
                || response.getString("access_token").isEmpty() || response.getLong("expires_in") <= 0)
            throw OAuthProtocol.protocol("invalid_token_response");
        // The previous refresh token may already be consumed, so a missing rotation ends the session.
        if (refresh && response.optString("refresh_token").isEmpty())
            throw new AiException(AiException.Kind.AUTH, 401, "missing_rotated_refresh_token", null);
        JSONObject next = new JSONObject(previous.toString());
        next.put("access_token", response.getString("access_token"));
        next.put("expires_at", now + response.getLong("expires_in"));
        next.put("scope", response.has("scope") ? response.getString("scope") : refresh ? previous.getString("scope") : OAuthProtocol.SCOPES);
        next.put("refresh_token", response.optString("refresh_token", ""));
        if (response.has("id_token")) next.put("id_token", response.getString("id_token"));
        if (response.has("earliest_refresh_at")) next.put("earliest_refresh_at", response.get("earliest_refresh_at"));
        return next;
    }
    public static boolean terminal(String code) {
        return Arrays.asList("invalid_grant", "invalid_refresh_token", "token_expired", "refresh_token_expired",
                "refresh_token_invalidated", "refresh_token_reused", "missing_rotated_refresh_token").contains(code);
    }
    public static void clear(JSONObject record) {
        for (String key : new String[]{"access_token", "refresh_token", "id_token", "scope", "expires_at", "earliest_refresh_at"})
            record.remove(key);
    }
}
