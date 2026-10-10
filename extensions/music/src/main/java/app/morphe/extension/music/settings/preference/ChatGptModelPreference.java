/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.settings.preference;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import java.util.List;
import app.morphe.extension.music.patches.lyrics.ai.AiException;
import app.morphe.extension.music.patches.lyrics.ai.ChatGptSession;
import app.morphe.extension.music.patches.lyrics.ai.RequestCancellation;
import app.morphe.extension.music.patches.lyrics.ai.ResponsesClient;
import app.morphe.extension.music.settings.Settings;
import app.morphe.extension.shared.Utils;
import static app.morphe.extension.shared.StringRef.str;

@SuppressWarnings({"unused", "deprecation"})
public final class ChatGptModelPreference extends Preference {
    private boolean loading;
    public ChatGptModelPreference(Context context, AttributeSet attrs) { super(context, attrs); update(); }
    public ChatGptModelPreference(Context context) { super(context); update(); }
    private void update() { setSummary(Settings.LYRICS_CHATGPT_MODEL.get()); }
    @Override protected void onClick() {
        if (loading) return;
        loading = true; setSummary(str("morphe_music_lyrics_chatgpt_models_loading"));
        Utils.runOnBackgroundThread(() -> {
            try {
                ChatGptSession session = ChatGptSession.get();
                String account = session.accountId();
                RequestCancellation cancel = new RequestCancellation();
                List<ResponsesClient.Model> models = ResponsesClient.models(session.accessToken(cancel), cancel);
                Utils.runOnMainThread(() -> {
                    loading = false; update();
                    if (!account.equals(session.accountId())) return;
                    if (models.isEmpty()) { Utils.showToastLong(str("morphe_music_lyrics_ai_error_permission")); return; }
                    String[] names = models.stream().map(ResponsesClient.Model::name).toArray(String[]::new);
                    AiChoiceDialog.show(getContext(), str("morphe_music_lyrics_chatgpt_model_title"), names, which -> {
                        if (!account.equals(session.accountId())) return;
                        Settings.LYRICS_CHATGPT_MODEL.save(models.get(which).slug()); update();
                    });
                });
            } catch (Exception ex) {
                Utils.runOnMainThread(() -> {
                    loading = false; update();
                    String key = ex instanceof AiException ai ? switch (ai.kind) {
                        case AUTH -> "morphe_music_lyrics_ai_error_auth";
                        case PERMISSION -> "morphe_music_lyrics_ai_error_permission";
                        case LIMIT -> "morphe_music_lyrics_ai_error_limit";
                        default -> "morphe_music_lyrics_ai_error_network";
                    } : "morphe_music_lyrics_ai_error_network";
                    Utils.showToastLong(str(key));
                });
            }
        });
    }
}
