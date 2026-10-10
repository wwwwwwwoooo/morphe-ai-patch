/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.settings.preference;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import app.morphe.extension.music.patches.lyrics.ai.LyricsTranslationEngine;
import app.morphe.extension.shared.Utils;
import static app.morphe.extension.shared.StringRef.str;

@SuppressWarnings({"unused", "deprecation"})
public final class AiTranslationCachePreference extends Preference {
    public AiTranslationCachePreference(Context context, AttributeSet attrs) { super(context, attrs); }
    public AiTranslationCachePreference(Context context) { super(context); }
    @Override protected void onClick() {
        Utils.runOnBackgroundThread(() -> {
            boolean cleared;
            try { LyricsTranslationEngine.clearCache(); cleared = true; } catch (Exception ex) { cleared = false; }
            String key = cleared ? "morphe_music_lyrics_ai_cache_cleared" : "morphe_music_lyrics_ai_cache_failed";
            Utils.runOnMainThread(() -> Utils.showToastShort(str(key)));
        });
    }
}
