/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.patches.lyrics.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ResponsesClient {
    public record Model(String slug, String name) { }
    private ResponsesClient() { }
    public static List<Model> models(String token, RequestCancellation cancel) throws Exception {
        JSONArray catalog = ApiTransport.json(OAuthProtocol.RESOURCE + "/models", null, null,
                token, cancel, true).getJSONArray("models");
        List<Model> out = new ArrayList<>();
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject model = catalog.getJSONObject(i);
            if ("list".equals(model.optString("visibility")))
                out.add(new Model(model.getString("slug"), model.getString("display_name")));
        }
        return out;
    }
    public static JSONObject body(String model, String instructions, String input) throws JSONException {
        return new JSONObject().put("model", model).put("instructions", instructions)
                .put("input", new JSONArray().put(new JSONObject().put("role", "user").put("content", input)))
                .put("store", false).put("stream", true);
    }
    public static String request(String token, String model, String instructions, String input,
                                 RequestCancellation cancel) throws Exception {
        return ApiTransport.request(OAuthProtocol.RESOURCE + "/responses",
                body(model, instructions, input).toString(), "application/json", token, cancel, true,
                stream -> readSse(stream, cancel));
    }
    public static String readSse(InputStream stream, RequestCancellation cancel) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder data = new StringBuilder(), output = new StringBuilder(); String line;
        boolean completed = false;
        Integer textOutput = null, textContent = null;
        boolean firstLine = true;
        while ((line = reader.readLine()) != null) {
            cancel.check();
            if (firstLine) {
                firstLine = false;
                if (line.startsWith("\uFEFF")) line = line.substring(1);
            }
            if (line.length() > 2_000_000) throw OAuthProtocol.protocol("sse_too_large");
            if (line.isEmpty()) {
                if (data.length() == 0) continue;
                JSONObject event = new JSONObject(data.toString()); data.setLength(0);
                String type = event.getString("type");
                if ("response.output_text.delta".equals(type)) {
                    // Translations are one output message. Reject ambiguous multiple outputs.
                    int index = event.optInt("output_index", 0), content = event.optInt("content_index", 0);
                    if (textOutput == null) { textOutput = index; textContent = content; }
                    else if (textOutput != index || textContent != content)
                        throw OAuthProtocol.protocol("multiple_response_outputs");
                    output.append(event.getString("delta"));
                    if (output.length() > 2_000_000) throw OAuthProtocol.protocol("sse_too_large");
                } else if ("response.completed".equals(type)) {
                    completed = true; break;
                } else if ("response.failed".equals(type) || "error".equals(type)) {
                    JSONObject response = event.optJSONObject("response");
                    JSONObject error = response == null ? event.optJSONObject("error") : response.optJSONObject("error");
                    String code = error == null ? event.optString("code", "stream_failed") : error.optString("code", "stream_failed");
                    throw AiException.remote(0, code, null);
                } else if ("response.incomplete".equals(type)) throw OAuthProtocol.protocol("response_incomplete");
            } else if (line.startsWith("data:")) {
                String value = line.substring(5); if (value.startsWith(" ")) value = value.substring(1);
                if (data.length() > 0) data.append('\n'); data.append(value);
                if (data.length() > 2_000_000) throw OAuthProtocol.protocol("sse_too_large");
            }
        }
        if (!completed) throw OAuthProtocol.protocol("interrupted_stream");
        if (output.length() == 0) throw OAuthProtocol.protocol("empty_response");
        return output.toString();
    }
}
