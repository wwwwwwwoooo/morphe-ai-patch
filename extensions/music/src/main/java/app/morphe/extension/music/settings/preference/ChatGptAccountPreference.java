/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.settings.preference;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import java.util.List;
import app.morphe.extension.music.patches.lyrics.ai.ChatGptSession;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.music.settings.Settings;
import static app.morphe.extension.shared.StringRef.str;

@SuppressWarnings({"unused", "deprecation"})
public final class ChatGptAccountPreference extends Preference {
    private String loginAccount = "";
    public ChatGptAccountPreference(Context context, AttributeSet attrs) { super(context, attrs); update(); }
    public ChatGptAccountPreference(Context context) { super(context); update(); }
    private void update() {
        ChatGptSession session = ChatGptSession.get();
        setSummary(str("morphe_music_lyrics_chatgpt_status_" + session.status()) + " " + session.label());
    }
    private void completed(String error) {
        if (error == null && !loginAccount.equals(ChatGptSession.get().accountId())) {
            Settings.LYRICS_CHATGPT_MODEL.save("");
        }
        update();
        if (error != null && !"cancelled".equals(error)) {
            String key = "revocation_unconfirmed".equals(error) ? "morphe_music_lyrics_chatgpt_revocation_unconfirmed"
                    : "morphe_music_lyrics_chatgpt_login_failed";
            Utils.showToastLong(str(key));
        }
    }
    @Override protected void onClick() {
        update();
        String[] actions = {str("morphe_music_lyrics_chatgpt_continue"), str("morphe_music_lyrics_chatgpt_reconsent"),
                str("morphe_music_lyrics_chatgpt_add_account"), str("morphe_music_lyrics_chatgpt_select_account"),
                str("morphe_music_lyrics_chatgpt_disconnect"), str("morphe_music_lyrics_chatgpt_cancel")};
        AiChoiceDialog.show(getContext(), str("morphe_music_lyrics_chatgpt_account_title"), actions, which -> {
                    ChatGptSession session = ChatGptSession.get();
                    loginAccount = session.accountId();
                    switch (which) {
                        case 0 -> session.signIn(false, false, this::completed);
                        case 1 -> session.signIn(false, true, this::completed);
                        case 2 -> session.signIn(true, false, this::completed);
                        case 3 -> selectAccount();
                        case 4 -> session.disconnect(this::completed);
                        case 5 -> session.cancelLogin();
                    }
                    update();
                });
    }
    private void selectAccount() {
        Utils.runOnBackgroundThread(() -> {
            try {
                List<ChatGptSession.Account> accounts = ChatGptSession.get().accounts();
                String[] labels = accounts.stream().map(ChatGptSession.Account::label).toArray(String[]::new);
                Utils.runOnMainThread(() -> AiChoiceDialog.show(getContext(),
                        str("morphe_music_lyrics_chatgpt_select_account"), labels,
                        which -> Utils.runOnBackgroundThread(() -> {
                            try {
                                ChatGptSession session = ChatGptSession.get();
                                String previous = session.accountId();
                                session.select(accounts.get(which).id());
                                if (!previous.equals(session.accountId())) Settings.LYRICS_CHATGPT_MODEL.save("");
                            }
                            catch (Exception ignored) { }
                            Utils.runOnMainThread(this::update);
                        })));
            } catch (Exception ex) { Utils.runOnMainThread(() -> completed("storage_error")); }
        });
    }
}
