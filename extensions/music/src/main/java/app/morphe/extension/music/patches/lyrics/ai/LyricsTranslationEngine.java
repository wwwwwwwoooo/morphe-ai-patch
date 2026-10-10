/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.patches.lyrics.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import app.morphe.extension.music.patches.lyrics.TrackInfo;
import app.morphe.extension.music.settings.Settings;
import app.morphe.extension.shared.Utils;
import static app.morphe.extension.shared.StringRef.str;

/** One active song request, strict ID mapping and a bounded private disk cache. */
public final class LyricsTranslationEngine {
    public interface Callback { void done(List<String> lines, String model); }
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static TranslationDiskCache cache;
    private static Job active;
    private static final class Job {
        final String key;
        final String model;
        final RequestCancellation cancel = new RequestCancellation();
        final List<Callback> callbacks = new ArrayList<>();
        Future<?> future;
        Job(String key, String model, Callback callback) { this.key = key; this.model = model; callbacks.add(callback); }
    }
    private LyricsTranslationEngine() { }
    private static synchronized TranslationDiskCache cache() {
        if (cache == null) cache = new TranslationDiskCache(new File(Utils.getContext().getCacheDir(), "lyricbridge-translations"));
        return cache;
    }
    public static synchronized void cancel() {
        if (active == null) return;
        Job cancelled = active;
        cancelled.cancel.cancel();
        if (cancelled.future != null) cancelled.future.cancel(true);
        active = null;
        List<Callback> callbacks = new ArrayList<>(cancelled.callbacks);
        Utils.runOnMainThread(() -> {
            for (Callback callback : callbacks) callback.done(null, cancelled.model);
        });
    }
    public static void clearCache() throws Exception { cancel(); cache().clear(); }
    public static void translate(String trackId, TrackInfo track, String source, List<String> lines,
                                 String language, boolean fresh, Callback callback) {
        final boolean chatgpt = "chatgpt".equals(Settings.LYRICS_AI_AUTH.get());
        final String model = chatgpt ? Settings.LYRICS_CHATGPT_MODEL.get() : Settings.LYRICS_AI_SYNC_MODEL.get();
        final String mode = Settings.LYRICS_AI_STYLE.get();
        final String prompt = Settings.LYRICS_AI_SYSTEM_PROMPT.get();
        final String endpoint = chatgpt ? OAuthProtocol.RESOURCE + "/responses" : Settings.LYRICS_AI_SYNC_BASE_URL.get();
        final String apiKey = chatgpt ? null : Settings.LYRICS_AI_SYNC_API_TOKEN.get();
        final String key;
        try { key = TranslationFormat.cacheKey(trackId + "|" + track.cacheKey(), source, lines, language,
                model, mode, prompt, endpoint); }
        catch (Exception ex) { callback.done(null, model); return; }
        final Job job;
        synchronized (LyricsTranslationEngine.class) {
            if (!fresh && active != null && active.key.equals(key)) { active.callbacks.add(callback); return; }
            cancel(); active = job = new Job(key, model, callback);
            job.future = WORK.submit(() -> {
                List<String> result = null;
                try {
                    job.cancel.check();
                    TranslationDiskCache disk = cache(); long generation = disk.generation();
                    if (!fresh) result = disk.read(key, lines);
                    if (result == null) {
                        // Bounded whole-song requests preserve context without unbounded allocations.
                        if (lines.size() > 1200 || lines.stream().mapToInt(String::length).sum() > 80_000)
                            throw OAuthProtocol.protocol("lyrics_too_large");
                        if (model.isEmpty()) throw OAuthProtocol.protocol("select_model");
                        String instructions = TranslationFormat.instructions(prompt, language, mode);
                        String input = TranslationFormat.input(track.title(), track.artist(), lines);
                        String response;
                        if (chatgpt) {
                            String token = ChatGptSession.get().accessToken(job.cancel);
                            response = ResponsesClient.request(token, model, instructions, input, job.cancel);
                        } else {
                            JSONArray messages = new JSONArray().put(new JSONObject().put("role", "system").put("content", instructions))
                                    .put(new JSONObject().put("role", "user").put("content", input));
                            JSONObject body = new JSONObject().put("model", model).put("messages", messages);
                            JSONObject json = ApiTransport.json(endpoint, body.toString(), "application/json",
                                    apiKey == null || apiKey.isEmpty() ? null : apiKey, job.cancel, true);
                            response = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                        }
                        result = TranslationFormat.parse(response, lines);
                        job.cancel.check();
                        try { disk.write(key, response, lines, generation); } catch (Exception ignored) { /* Display even if disk is full. */ }
                    }
                } catch (AiException ex) {
                    if (!job.cancel.isCancelled()) showError(ex);
                } catch (JSONException ex) {
                    if (!job.cancel.isCancelled()) showError(OAuthProtocol.protocol("invalid_json"));
                } catch (Exception ex) {
                    if (!job.cancel.isCancelled()) Utils.runOnMainThread(() -> Utils.showToastShort(str("morphe_music_lyrics_ai_error_network")));
                }
                List<String> translated = result;
                Utils.runOnMainThread(() -> {
                    List<Callback> callbacks;
                    synchronized (LyricsTranslationEngine.class) {
                        if (active != job || job.cancel.isCancelled()) return;
                        callbacks = new ArrayList<>(job.callbacks); active = null;
                    }
                    for (Callback item : callbacks) item.done(translated, model);
                });
            });
        }
    }
    private static void showError(AiException error) {
        String resource = switch (error.kind) {
            case AUTH -> "morphe_music_lyrics_ai_error_auth";
            case PERMISSION -> "morphe_music_lyrics_ai_error_permission";
            case LIMIT -> "morphe_music_lyrics_ai_error_limit";
            case NETWORK -> "morphe_music_lyrics_ai_error_network";
            case PROTOCOL -> "morphe_music_lyrics_ai_error_format";
        };
        Utils.runOnMainThread(() -> Utils.showToastShort(str(resource)));
    }
}
