/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.patches.lyrics.ai;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class ApiTransport {
    public interface Decoder<T> { T read(InputStream input) throws Exception; }
    private ApiTransport() { }

    public static Map<String, String> fields(String... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Pairs required");
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) fields.put(pairs[i], pairs[i + 1]);
        return fields;
    }
    public static String form(Map<String, String> values) {
        StringBuilder out = new StringBuilder();
        values.forEach((key, value) -> {
            if (out.length() > 0) out.append('&');
            out.append(encode(key)).append('=').append(encode(value));
        });
        return out.toString();
    }
    private static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
    public static String text(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; int n;
        while ((n = input.read(buffer)) != -1) {
            if (out.size() + n > 2_000_000) throw new IOException("Response too large");
            out.write(buffer, 0, n);
        }
        return out.toString("UTF-8");
    }
    public static JSONObject json(String url, String body, String type, String bearer,
                                  RequestCancellation cancel, boolean retry) throws Exception {
        return request(url, body, type, bearer, cancel, retry,
                input -> new JSONObject(text(input)));
    }
    public static <T> T request(String url, String body, String type, String bearer,
            RequestCancellation cancel, boolean retry, Decoder<T> decoder) throws Exception {
        if (!url.startsWith("https://")) throw new IOException("HTTPS required");
        for (int attempt = 0; ; attempt++) {
            cancel.check();
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false); // Never forward bearer credentials on redirect.
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(60_000);
            conn.setRequestMethod(body == null ? "GET" : "POST");
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer " + bearer);
            if (body != null) conn.setRequestProperty("Content-Type", type);
            try {
                cancel.attach(conn);
                if (body != null) {
                    conn.setDoOutput(true);
                    try (OutputStream out = conn.getOutputStream()) {
                        out.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                }
                int status = conn.getResponseCode();
                if (status < 200 || status >= 300) {
                    String code = "http_" + status;
                    InputStream error = conn.getErrorStream();
                    if (error != null) try (InputStream in = error) {
                        JSONObject json = new JSONObject(text(in));
                        Object field = json.opt("error");
                        if (field instanceof JSONObject object) code = object.optString("code", code);
                        else if (field instanceof String string) code = string;
                    } catch (Exception ignored) { }
                    if (retry && status >= 500 && attempt < 2) {
                        conn.disconnect(); cancel.detach(); cancel.backoff(attempt); continue;
                    }
                    throw AiException.remote(status, code, conn.getHeaderField("x-request-id"));
                }
                try (InputStream input = conn.getInputStream()) { return decoder.read(input); }
            } finally { conn.disconnect(); cancel.detach(); }
        }
    }
}
