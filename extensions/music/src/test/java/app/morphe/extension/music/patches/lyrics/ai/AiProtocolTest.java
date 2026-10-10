package app.morphe.extension.music.patches.lyrics.ai;

import org.json.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AiProtocolTest {
    @TempDir Path temp;
    static OAuthProtocol.Attempt attempt(String client) {
        return new OAuthProtocol.Attempt("state", "nonce", "verifier", "http://127.0.0.1:5555/auth/callback", client, null);
    }
    @Test void pkceUsesS256KnownVector() throws Exception {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                OAuthProtocol.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
        assertEquals(43, OAuthProtocol.random().length());
    }
    @Test void registrationAndReturningClientAreDistinct() throws Exception {
        var dynamic = attempt(OAuthProtocol.DYNAMIC);
        var callback = Map.of("state", "state", "code", "code", "client_id", "oaiapp_test");
        assertEquals("oaiapp_test", OAuthProtocol.callbackClient(dynamic, callback));
        assertThrows(AiException.class, () -> OAuthProtocol.callbackClient(dynamic, Map.of("state", "state", "code", "code")));
        assertThrows(AiException.class, () -> OAuthProtocol.callbackClient(dynamic,
                Map.of("state", "wrong", "code", "code", "client_id", "oaiapp_test")));
        assertThrows(AiException.class, () -> OAuthProtocol.callbackClient(dynamic,
                Map.of("state", "state", "code", "code", "client_id", OAuthProtocol.DYNAMIC)));
        assertThrows(AiException.class, () -> OAuthProtocol.callbackClient(attempt("oaiapp_other"), callback));
        assertEquals("oaiapp_test", OAuthProtocol.callbackClient(attempt("oaiapp_test"), Map.of("state", "state", "code", "code")));
    }
    @Test void consentErrorValidatesStateFirst() {
        assertEquals("state_mismatch", assertThrows(AiException.class, () -> OAuthProtocol.callbackClient(
                attempt("client"), Map.of("state", "bad", "error", "access_denied"))).code);
        assertEquals(AiException.Kind.PERMISSION, assertThrows(AiException.class, () -> OAuthProtocol.callbackClient(
                attempt("client"), Map.of("state", "state", "error", "access_denied"))).kind);
    }
    @Test void authorizationIncludesOfficialParametersAndFreshConsentOnlyWhenRequested() throws Exception {
        String url = OAuthProtocol.authorize(attempt(OAuthProtocol.DYNAMIC), "urn:uuid:host", null, false);
        var query = LoopbackReceiver.query(new URI(url).getRawQuery());
        assertEquals(OAuthProtocol.DYNAMIC, query.get("client_id"));
        assertEquals("S256", query.get("code_challenge_method"));
        assertEquals(OAuthProtocol.RESOURCE, query.get("resource"));
        assertEquals(attempt("x").redirect(), query.get("redirect_uri"));
        assertFalse(query.containsKey("prompt")); assertTrue(query.containsKey("agent_name_hint"));
        query = LoopbackReceiver.query(new URI(OAuthProtocol.authorize(attempt("issued"), "host", "hint", true)).getRawQuery());
        assertFalse(query.containsKey("agent_name_hint")); assertEquals("hint", query.get("id_token_hint"));
        assertEquals("consent", query.get("prompt"));
        assertThrows(AiException.class, () -> LoopbackReceiver.query("state=a&state=b"));
    }
    static String b64(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    static String sign(JSONObject claims, KeyPair key) throws Exception {
        String payload = b64(new JSONObject().put("alg", "RS256").put("kid", "test").toString().getBytes(StandardCharsets.UTF_8))
                + "." + b64(claims.toString().getBytes(StandardCharsets.UTF_8));
        Signature sig = Signature.getInstance("SHA256withRSA");sig.initSign(key.getPrivate());sig.update(payload.getBytes(StandardCharsets.US_ASCII));
        return payload + "." + b64(sig.sign());
    }
    static JSONObject jwks(KeyPair key) throws Exception {
        RSAPublicKey rsa = (RSAPublicKey) key.getPublic();
        return new JSONObject().put("keys", new JSONArray().put(new JSONObject().put("kid", "test").put("kty", "RSA")
                .put("n", b64(rsa.getModulus().toByteArray())).put("e", b64(rsa.getPublicExponent().toByteArray()))));
    }
    @Test void validatesSignatureNonceIssuerAudienceExpiryAndAccount() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);var key = generator.generateKeyPair();
        var keys = jwks(key);
        var claims = new JSONObject().put("iss", OAuthProtocol.ISSUER).put("sub", "subject")
                .put("aud", "client").put("nonce", "nonce").put("exp", 2000).put("iat", 900);
        assertEquals("subject", IdTokenValidator.validate(sign(claims,key),keys,"client","nonce",null,1000).subject());
        assertThrows(AiException.class, () -> IdTokenValidator.validate(sign(claims,key),keys,"client","bad",null,1000));
        assertThrows(AiException.class, () -> IdTokenValidator.validate(sign(claims,key),keys,"other","nonce",null,1000));
        assertThrows(AiException.class, () -> IdTokenValidator.validate(sign(claims,key),keys,"client","nonce",null,2000));
        assertThrows(AiException.class, () -> IdTokenValidator.validate(sign(claims,key),keys,"client","nonce","other",1000));
        assertThrows(AiException.class, () -> IdTokenValidator.validate(sign(claims,generator.generateKeyPair()),keys,"client","nonce",null,1000));
        claims.put("iss", "https://attacker.example");
        assertThrows(AiException.class, () -> IdTokenValidator.validate(sign(claims,key),keys,"client","nonce",null,1000));
        claims.put("iss",OAuthProtocol.ISSUER).put("aud",new JSONArray().put("client").put("another"));
        assertThrows(AiException.class, () -> IdTokenValidator.validate(sign(claims,key),keys,"client","nonce",null,1000));
        claims.put("azp","client");
        assertEquals("subject",IdTokenValidator.validate(sign(claims,key),keys,"client","nonce",null,1000).subject());
    }
    @Test void loopbackReceivesExactRedirectRejectsBadStateAndCloses() throws Exception {
        var cancel = new RequestCancellation();
        try (var server = new LoopbackReceiver()) {
            var executor = Executors.newSingleThreadExecutor();
            try {
                Future<Map<String,String>> future = executor.submit(() -> server.await("state",cancel));
                URI uri = URI.create(server.redirect()); assertEquals("127.0.0.1",uri.getHost());
                assertEquals("/auth/callback",uri.getPath());
                assertEquals(400, callback(uri,"/auth/callback?state=wrong&code=x"));
                assertFalse(future.isDone());
                assertEquals(400, callback(uri,"/callback?state=state&code=x"));
                assertEquals(200, callback(uri,"/auth/callback?state=state&code=code&client_id=issued"));
                assertEquals("code",future.get(3,TimeUnit.SECONDS).get("code"));
            } finally { executor.shutdownNow(); }
        }
    }
    static int callback(URI uri,String target) throws Exception {
        try (Socket socket = new Socket(uri.getHost(),uri.getPort())) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(("GET " + target + " HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String status = new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
            return Integer.parseInt(status.split(" ")[1]);
        }
    }
    @Test void cancellationClosesListener() throws Exception {
        try (var server = new LoopbackReceiver()) {
            var cancel = new RequestCancellation(); cancel.attach(server); cancel.cancel();
            assertThrows(IOException.class, () -> server.await("state",cancel));
            assertThrows(IOException.class, cancel::check);
        }
    }
    @Test void refreshRotatesAtomicallyRetainsScopeAndChecksTerminalErrors() throws Exception {
        var old = new JSONObject().put("client_id","issued").put("scope",OAuthProtocol.SCOPES).put("refresh_token","old")
                .put("id_token","retained").put("access_token","old-access");
        var response = new JSONObject().put("token_type","Bearer").put("access_token","new-access")
                .put("refresh_token","rotated").put("expires_in",3600);
        var next = SessionTokens.replace(old,response,1000,true);
        assertEquals("rotated",next.getString("refresh_token")); assertEquals(4600,next.getLong("expires_at"));
        assertTrue(SessionTokens.direct(next)); assertEquals("retained",next.getString("id_token"));
        assertEquals("old",old.getString("refresh_token"));
        response.put("scope","openid profile email"); assertFalse(SessionTokens.direct(SessionTokens.replace(old,response,1000,true)));
        response.remove("refresh_token"); assertThrows(AiException.class, () -> SessionTokens.replace(old,response,1000,true));
        var unrotated = assertThrows(AiException.class, () -> SessionTokens.replace(old,response,1000,true));
        assertEquals(AiException.Kind.AUTH, unrotated.kind); assertTrue(SessionTokens.terminal(unrotated.code));
        assertTrue(SessionTokens.terminal("refresh_token_reused")); assertFalse(SessionTokens.terminal("http_503"));
        SessionTokens.clear(next); assertEquals("issued",next.getString("client_id")); assertFalse(next.has("access_token"));
    }
    @Test void omittedScopeMeansRequestedScope() throws Exception {
        var response = new JSONObject().put("token_type","Bearer").put("access_token","a")
                .put("refresh_token","r").put("expires_in",3600);
        assertTrue(SessionTokens.direct(SessionTokens.replace(new JSONObject(), response, 1000, false)));
    }
    @Test void restartedDeadlineAndFailingDisconnectAreSafe() throws Exception {
        var cancel = new RequestCancellation(); cancel.restartDeadline(); cancel.check();
        var conn = new java.net.HttpURLConnection(new java.net.URL("https://example.invalid")) {
            @Override public void disconnect() { throw new IllegalStateException("main thread"); }
            @Override public boolean usingProxy() { return false; }
            @Override public void connect() { }
        };
        cancel.attach(conn); cancel.cancel(); assertTrue(cancel.isCancelled());
    }
    @Test void unwrapsMarkdownFenceOnly() throws Exception {
        assertEquals(List.of("번역","","번역","번역"), TranslationFormat.parse(
                TranslationFormat.unwrap("```json\n" + translation() + "\n```"), LINES));
        assertEquals(translation(), TranslationFormat.unwrap(translation()));
        assertThrows(AiException.class, () -> TranslationFormat.parse(
                TranslationFormat.unwrap("Here you go:\n```json\n" + translation() + "\n```"), LINES));
    }
    @Test void tokenClientUsesIssuedIdExactRedirectAndDoesNotRetryRefresh() throws Exception {
        List<String> requests = new ArrayList<>();
        OAuthTokenClient client = new OAuthTokenClient((form, cancel) -> {
            requests.add(form);
            return new JSONObject().put("access_token", "new").put("refresh_token", "rotated")
                    .put("token_type", "Bearer").put("expires_in", 3600);
        });
        client.code(attempt(OAuthProtocol.DYNAMIC), Map.of("state", "state", "code", "code", "client_id", "issued"), new RequestCancellation());
        var form = LoopbackReceiver.query(requests.get(0));
        assertEquals("issued", form.get("client_id"));
        assertEquals(attempt("issued").redirect(), form.get("redirect_uri"));
        assertEquals("verifier", form.get("code_verifier"));
        assertFalse(form.containsKey("client_secret"));
        JSONObject record = new JSONObject().put("client_id", "issued").put("refresh_token", "old").put("scope", OAuthProtocol.SCOPES);
        var response = client.refresh(record, new RequestCancellation());
        form = LoopbackReceiver.query(requests.get(1));
        assertEquals(Set.of("grant_type", "client_id", "refresh_token", "resource"), form.keySet());
        assertEquals("refresh_token", form.get("grant_type"));
        assertEquals("old", record.getString("refresh_token"));
        assertEquals("rotated", SessionTokens.replace(record, response, 1000, true).getString("refresh_token"));
        List<String> failures = new ArrayList<>();
        OAuthTokenClient failing = new OAuthTokenClient((body, cancel) -> {
            failures.add(body); throw AiException.remote(503, "http_503", null);
        });
        assertThrows(AiException.class, () -> failing.refresh(record, new RequestCancellation()));
        assertEquals(1, failures.size());
    }
    @Test void rejectsTrailingTranslationContentAndNonTextValues() throws Exception {
        assertThrows(AiException.class, () -> TranslationFormat.parse(translation() + " extra", LINES));
        JSONObject object = new JSONObject(translation());
        object.getJSONArray("segments").getJSONObject(0).put("text", 123);
        assertThrows(AiException.class, () -> TranslationFormat.parse(object.toString(), LINES));
    }
    @Test void remoteErrorsKeepAuthPermissionLimitsAndTemporaryFailuresDistinct() {
        assertEquals(AiException.Kind.AUTH, AiException.remote(401, "http_401", "request").kind);
        assertEquals(AiException.Kind.PERMISSION, AiException.remote(403, "subscription_sharing_user_not_eligible", null).kind);
        assertEquals(AiException.Kind.LIMIT, AiException.remote(429, "subscription_sharing_usage_limit_exceeded", null).kind);
        assertEquals(AiException.Kind.LIMIT, AiException.remote(0, "rate_limit_exceeded", null).kind);
        assertEquals(AiException.Kind.LIMIT, AiException.remote(0, "insufficient_quota", null).kind);
        assertEquals(AiException.Kind.NETWORK, AiException.remote(0, "subscription_sharing_usage_unavailable", null).kind);
        assertEquals(AiException.Kind.PROTOCOL, AiException.remote(400, "subscription_sharing_unsupported_capability", null).kind);
    }
    static String event(String type,String extra) { return "event: " + type + "\r\ndata: {\"type\":\"" + type + "\"" + extra + "}\r\n\r\n"; }
    static String stream(String text) throws Exception { return ResponsesClient.readSse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),new RequestCancellation()); }
    @Test void sseHandlesUtf8CrLfCommentsMultilineDataAndReasoningOutputIndex() throws Exception {
        String sse = "\uFEFF: heartbeat\r\n\r\n" + event("response.created","")
                + "data: {\"type\":\"response.output_text.delta\",\n"
                + "data: \"output_index\":1,\"delta\":\"한국\"}\n\n"
                + event("response.output_text.delta",",\"output_index\":1,\"delta\":\"어\"") + event("response.completed","");
        assertEquals("한국어",stream(sse));
    }
    @Test void sseNeverReturnsPartialFailedOrIncompleteOutput() {
        String partial = event("response.output_text.delta",",\"delta\":\"partial\"");
        assertThrows(AiException.class, () -> stream(partial));
        assertThrows(AiException.class, () -> stream(partial + event("response.incomplete","")));
        var ex=assertThrows(AiException.class, () -> stream(partial + event("response.failed",",\"response\":{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\"}}")));
        assertEquals(AiException.Kind.LIMIT,ex.kind);
        assertThrows(AiException.class, () -> stream(partial + event("response.output_text.delta",",\"output_index\":1,\"delta\":\"second\"") + event("response.completed","")));
    }
    static Set<String> jsonKeys(JSONObject object) {
        Set<String> keys = new HashSet<>(); object.keys().forEachRemaining(keys::add); return keys;
    }
    @Test void responseBodyUsesOnlySupportedFields() throws Exception {
        JSONObject body=ResponsesClient.body("model","instructions","lyrics");
        assertFalse(body.getBoolean("store")); assertTrue(body.getBoolean("stream"));
        assertEquals(Set.of("model","instructions","input","store","stream"),jsonKeys(body));
        assertEquals("user",body.getJSONArray("input").getJSONObject(0).getString("role"));
    }
    static final List<String> LINES=List.of("one", "", "one", "日本語");
    static String translation() throws Exception {
        JSONArray array=new JSONArray();
        for(int i=0;i<LINES.size();i++) array.put(new JSONObject().put("id",TranslationFormat.id(i,LINES.get(i)))
                .put("text",i==1 ? "" : "번역"));
        return new JSONObject().put("segments",array).toString();
    }
    @Test void segmentIdsPreserveBlankLinesAndRepeatedChorus() throws Exception {
        assertEquals(List.of("번역","","번역","번역"),TranslationFormat.parse(translation(),LINES));
        assertNotEquals(TranslationFormat.id(0,"one"),TranslationFormat.id(2,"one"));
        assertFalse(TranslationFormat.input("title","artist",LINES).contains("timestamp"));
        var parsed=new JSONObject(translation());var segments=parsed.getJSONArray("segments");
        segments.getJSONObject(2).put("id",segments.getJSONObject(0).getString("id"));
        assertThrows(AiException.class, () -> TranslationFormat.parse(parsed.toString(),LINES));
    }
    @Test void rejectsMissingReorderedAndMultilineSegments() throws Exception {
        var parsed=new JSONObject(translation());var array=parsed.getJSONArray("segments");
        array.remove(0);String missing=parsed.toString();assertThrows(AiException.class, () -> TranslationFormat.parse(missing,LINES));
        parsed=new JSONObject(translation());array=parsed.getJSONArray("segments");
        var first=array.getJSONObject(0);array.put(0,array.getJSONObject(2));array.put(2,first);
        String reordered=parsed.toString();assertThrows(AiException.class, () -> TranslationFormat.parse(reordered,LINES));
        parsed=new JSONObject(translation());parsed.getJSONArray("segments").getJSONObject(0).put("text","a\nb");
        String multiline=parsed.toString();assertThrows(AiException.class, () -> TranslationFormat.parse(multiline,LINES));
    }
    @Test void cacheKeyChangesWithEveryTranslationInput() throws Exception {
        String key=TranslationFormat.cacheKey("track","provider",LINES,"ko","m","natural","prompt","endpoint");
        for(String changed:List.of(
                TranslationFormat.cacheKey("track2","provider",LINES,"ko","m","natural","prompt","endpoint"),
                TranslationFormat.cacheKey("track","provider",List.of("changed"),"ko","m","natural","prompt","endpoint"),
                TranslationFormat.cacheKey("track","provider",LINES,"en","m","natural","prompt","endpoint"),
                TranslationFormat.cacheKey("track","provider",LINES,"ko","m2","natural","prompt","endpoint"),
                TranslationFormat.cacheKey("track","provider",LINES,"ko","m","literal","prompt","endpoint"),
                TranslationFormat.cacheKey("track","provider",LINES,"ko","m","natural","prompt2","endpoint"))) assertNotEquals(key,changed);
        assertEquals(64,key.length());
    }
    @Test void diskCacheValidatesCorruptionAndClearRejectsStaleWriters() throws Exception {
        var cache=new TranslationDiskCache(temp.toFile());long generation=cache.generation();
        String key=TranslationFormat.hash("song");cache.write(key,translation(),LINES,generation);
        assertEquals(4,cache.read(key,LINES).size());
        java.nio.file.Files.write(temp.resolve(key+".json"),"broken".getBytes(StandardCharsets.UTF_8));assertNull(cache.read(key,LINES));
        cache.clear();cache.write(key,translation(),LINES,generation);assertNull(cache.read(key,LINES));
        cache.write(key,translation(),LINES,cache.generation());assertNotNull(cache.read(key,LINES));
    }
}
