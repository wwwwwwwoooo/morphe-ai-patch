/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.patches.music.layout.lyrics

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.music.misc.settings.PreferenceScreen
import app.morphe.patches.music.shared.Constants.COMPATIBILITY_YOUTUBE_MUSIC
import app.morphe.patches.shared.misc.settings.preference.InputType
import app.morphe.patches.shared.misc.settings.preference.ListPreference
import app.morphe.patches.shared.misc.settings.preference.NonInteractivePreference
import app.morphe.patches.shared.misc.settings.preference.PreferenceCategory
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.shared.misc.settings.preference.TextPreference

/** Adds optional settings; all playback and rendering hooks remain owned by Third-party lyrics. */
@Suppress("unused")
val aiLyricsTranslationPatch = resourcePatch(
    name = "AI synced lyrics translation",
    description = "Adds ChatGPT account sign-in and AI translation with verified line mapping and original timing.",
    default = false
) {
    dependsOn(lyricsPatch)
    compatibleWith(COMPATIBILITY_YOUTUBE_MUSIC)
    execute {
        PreferenceScreen.LYRICS.addPreferences(
            PreferenceCategory(
                key = "morphe_music_lyrics_section_ai_sync",
                sorting = Sorting.UNSORTED,
                preferences = setOf(
                    SwitchPreference("morphe_music_lyrics_ai_sync_translation", summary = true),
                    ListPreference("morphe_music_lyrics_ai_auth"),
                    NonInteractivePreference(
                        key = "morphe_music_lyrics_chatgpt_account",
                        tag = "app.morphe.extension.music.settings.preference.ChatGptAccountPreference",
                        selectable = true
                    ),
                    NonInteractivePreference(
                        key = "morphe_music_lyrics_chatgpt_model",
                        tag = "app.morphe.extension.music.settings.preference.ChatGptModelPreference",
                        selectable = true
                    ),
                    ListPreference(
                        key = "morphe_music_lyrics_ai_target_language",
                        entriesKey = "morphe_language_entries",
                        entryValuesKey = "morphe_language_entry_values"
                    ),
                    ListPreference("morphe_music_lyrics_ai_style"),
                    TextPreference("morphe_music_lyrics_ai_system_prompt", inputType = InputType.TEXT_MULTI_LINE),
                    SwitchPreference("morphe_music_lyrics_ai_auto_translate", summary = true),
                    TextPreference("morphe_music_lyrics_ai_sync_base_url", inputType = InputType.TEXT),
                    TextPreference("morphe_music_lyrics_ai_sync_api_token", inputType = InputType.TEXT_PASSWORD),
                    TextPreference("morphe_music_lyrics_ai_sync_model"),
                    NonInteractivePreference(
                        key = "morphe_music_lyrics_ai_clear_cache",
                        tag = "app.morphe.extension.music.settings.preference.AiTranslationCachePreference",
                        selectable = true
                    )
                )
            )
        )
    }
}
