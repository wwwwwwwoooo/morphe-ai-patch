/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.patches.lyrics.ai;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import app.morphe.extension.shared.Utils;

/** All credential mutations/refreshes are serialized; callbacks never publish an unverified identity. */
public final class ChatGptSession {
    public interface Callback { void done(String errorCode); }
    public record Account(String id, String label) { }
    private static final ExecutorService AUTH = Executors.newSingleThreadExecutor();
    private static ChatGptSession instance;
    private final Context context;
    private final SecureSessionStore store;
    private volatile JSONObject state;
    private volatile RequestCancellation login;
    private volatile boolean disconnecting;
    private volatile String lastError = "";
    private volatile String provisionalClient;

    private ChatGptSession(Context context) {
        this.context = context.getApplicationContext(); store = new SecureSessionStore(this.context);
    }
    public static synchronized ChatGptSession get() {
        if (instance == null) instance = new ChatGptSession(Utils.getContext());
        return instance;
    }
    private synchronized void load() throws Exception {
        if (state != null) return;
        JSONObject next = store.read();
        if (!next.has("host")) next.put("host", "urn:uuid:" + UUID.randomUUID());
        if (!next.has("accounts")) next.put("accounts", new JSONObject());
        store.write(next);
        state = next;
    }
    private JSONObject active() throws Exception {
        load(); return state.getJSONObject("accounts").optJSONObject(state.optString("active"));
    }
    private void save(JSONObject record) throws Exception {
        JSONObject next = new JSONObject(state.toString());
        next.getJSONObject("accounts").put(record.getString("client_id"), record);
        store.write(next); state = next;
    }
    public synchronized List<Account> accounts() throws Exception {
        load(); List<Account> out = new ArrayList<>(); JSONObject records = state.getJSONObject("accounts");
        Iterator<String> keys = records.keys();
        while (keys.hasNext()) {
            String id = keys.next(); JSONObject a = records.getJSONObject(id);
            String label = a.optString("email", a.optString("subject"));
            out.add(new Account(id, label + " · " + id.substring(Math.max(0, id.length() - 8))));
        }
        return out;
    }
    public synchronized void select(String id) throws Exception {
        cancelLogin(); LyricsTranslationEngine.cancel(); load();
        if (!state.getJSONObject("accounts").has(id)) throw OAuthProtocol.protocol("unknown_account");
        JSONObject next = new JSONObject(state.toString()); next.put("active", id);
        store.write(next); state = next;
    }
    public String status() {
        try {
            loadIfNeeded();
            JSONObject snapshot = state;
            JSONObject a = snapshot.getJSONObject("accounts").optJSONObject(snapshot.optString("active"));
            if (disconnecting) return "disconnecting";
            if (login != null) return "signing_in";
            if (a == null || a.optString("access_token").isEmpty()) return "signed_out";
            if (!SessionTokens.direct(a)) return "identity_only";
            return "connected";
        } catch (Exception ex) { return "storage_error"; }
    }
    public String accountId() {
        JSONObject snapshot = state;
        return snapshot == null ? "" : snapshot.optString("active");
    }
    public String label() {
        try {
            loadIfNeeded();
            JSONObject snapshot = state;
            JSONObject a = snapshot.getJSONObject("accounts").optJSONObject(snapshot.optString("active"));
            return a == null ? "" : a.optString("email", a.optString("subject"));
        }
        catch (Exception ignored) { return ""; }
    }
    private void loadIfNeeded() throws Exception { if (state == null) load(); }
    public String lastError() { return lastError; }
    public void cancelLogin() {
        RequestCancellation pending = login;
        if (pending != null) pending.cancel();
        login = null;
    }
    public void signIn(boolean newAccount, boolean consent, Callback callback) {
        cancelLogin();
        if (newAccount) provisionalClient = null;
        RequestCancellation pending = new RequestCancellation(); login = pending;
        AUTH.execute(() -> {
            String error = null;
            try (LoopbackReceiver receiver = new LoopbackReceiver()) {
                pending.attach(receiver);
                OAuthProtocol.Attempt attempt; String host, hint;
                synchronized (this) {
                    JSONObject a = newAccount ? null : active(); load(); host = state.getString("host");
                    String client = a == null ? provisionalClient == null ? OAuthProtocol.DYNAMIC : provisionalClient
                            : a.getString("client_id");
                    attempt = new OAuthProtocol.Attempt(OAuthProtocol.random(), OAuthProtocol.random(), OAuthProtocol.random(),
                            receiver.redirect(), client, a == null ? null : a.getString("subject"));
                    hint = a == null ? null : a.optString("id_token", "");
                }
                String url = OAuthProtocol.authorize(attempt, host, hint, consent);
                Utils.runOnMainThread(() -> {
                    if (pending.isCancelled()) return;
                    try {
                        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception ex) {
                        Utils.showToastLong(app.morphe.extension.shared.StringRef.str("morphe_music_lyrics_chatgpt_login_failed"));
                        pending.cancel();
                    }
                });
                Map<String, String> query = receiver.await(attempt.state(), pending);
                pending.restartDeadline(); // Browser time must not consume the token exchange budget.
                String client = OAuthProtocol.callbackClient(attempt, query);
                synchronized (this) { if (attempt.subject() == null) provisionalClient = client; }
                JSONObject tokens = new OAuthTokenClient().code(attempt, query, pending);
                JSONObject jwks = ApiTransport.json(OAuthProtocol.ISSUER + "/.well-known/jwks.json", null, null, null, pending, true);
                IdTokenValidator.Identity identity = IdTokenValidator.validate(tokens.getString("id_token"), jwks,
                        client, attempt.nonce(), attempt.subject(), System.currentTimeMillis() / 1000);
                JSONObject record = new JSONObject().put("client_id", client).put("subject", identity.subject())
                        .put("email", identity.email());
                record = SessionTokens.replace(record, tokens, System.currentTimeMillis() / 1000, false);
                synchronized (this) {
                    pending.check();
                    JSONObject existing = state.getJSONObject("accounts").optJSONObject(client);
                    if (existing != null && !identity.subject().equals(existing.getString("subject")))
                        throw OAuthProtocol.protocol("account_mismatch");
                    JSONObject next = new JSONObject(state.toString());
                    next.getJSONObject("accounts").put(client, record); next.put("active", client);
                    store.write(next); state = next; provisionalClient = null;
                }
            } catch (AiException ex) { error = ex.code; }
            catch (Exception ex) { error = pending.isCancelled() ? "cancelled" : "login_failed"; }
            synchronized (this) { if (login == pending) login = null; }
            lastError = error == null ? "" : error;
            String result = error; Utils.runOnMainThread(() -> callback.done(result));
        });
    }
    public synchronized String accessToken(RequestCancellation cancel) throws Exception {
        if (disconnecting) throw new AiException(AiException.Kind.AUTH, 401, "signed_out", null);
        JSONObject record = active();
        if (record == null || record.optString("access_token").isEmpty())
            throw new AiException(AiException.Kind.AUTH, 401, "sign_in_required", null);
        if (!SessionTokens.direct(record)) throw new AiException(AiException.Kind.PERMISSION, 403, "plan_permission_missing", null);
        long now = System.currentTimeMillis() / 1000;
        if (record.optLong("expires_at") > now + 60) return record.getString("access_token");
        if (record.optString("refresh_token").isEmpty())
            throw new AiException(AiException.Kind.AUTH, 401, "sign_in_required", null);
        try {
            JSONObject response = new OAuthTokenClient().refresh(record, cancel);
            if (response.has("id_token")) IdTokenValidator.validate(response.getString("id_token"),
                    ApiTransport.json(OAuthProtocol.ISSUER + "/.well-known/jwks.json", null, null, null, cancel, true),
                    record.getString("client_id"), null, record.getString("subject"), now);
            JSONObject next = SessionTokens.replace(record, response, now, true);
            save(next); // Rotated credentials must be persisted before exposing the new access token.
            if (!SessionTokens.direct(next)) throw new AiException(AiException.Kind.PERMISSION, 403, "plan_permission_missing", null);
            return next.getString("access_token");
        } catch (AiException ex) {
            if (SessionTokens.terminal(ex.code)) {
                JSONObject cleared = new JSONObject(record.toString()); SessionTokens.clear(cleared); save(cleared);
            }
            throw ex;
        }
    }
    public void disconnect(Callback callback) {
        disconnecting = true;
        cancelLogin(); LyricsTranslationEngine.cancel();
        AUTH.execute(() -> {
            String error = null;
            synchronized (this) {
                try {
                    JSONObject record = active();
                    if (record != null) {
                        String refresh = record.optString("refresh_token");
                        if (!refresh.isEmpty()) try {
                            RequestCancellation cancel = new RequestCancellation();
                            JSONObject discovery = ApiTransport.json(OAuthProtocol.ISSUER + "/.well-known/openid-configuration",
                                    null, null, null, cancel, true);
                            String endpoint = discovery.getString("revocation_endpoint");
                            if (!endpoint.startsWith(OAuthProtocol.ISSUER + "/")) throw OAuthProtocol.protocol("invalid_revocation_endpoint");
                            ApiTransport.request(endpoint, ApiTransport.form(ApiTransport.fields("token", refresh,
                                    "token_type_hint", "refresh_token", "client_id", record.getString("client_id"))),
                                    "application/x-www-form-urlencoded", null, cancel, true, input -> ApiTransport.text(input));
                        } catch (Exception ex) { error = "revocation_unconfirmed"; }
                        JSONObject cleared = new JSONObject(record.toString()); SessionTokens.clear(cleared); save(cleared);
                    }
                } catch (Exception ex) { error = "storage_error"; }
            }
            disconnecting = false;
            lastError = error == null ? "" : error;
            String result = error; Utils.runOnMainThread(() -> callback.done(result));
        });
    }
}
