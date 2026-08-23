package com.betterstreamflix.settings

import com.betterstreamflix.utils.UserPreferences

/**
 * Subtitle settings helper — manages subtitle appearance and language preferences.
 */
object SubtitleSettingsHelper {

    val SUBTITLE_LANGUAGES = listOf(
        "en" to "English",
        "de" to "Deutsch",
        "es" to "Español",
        "fr" to "Français",
        "it" to "Italiano",
        "pt" to "Português",
        "tr" to "Türkçe",
        "ja" to "日本語",
        "ko" to "한국어",
        "ar" to "العربية",
    )

    /**
     * Get current subtitle language.
     */
    fun getCurrentSubtitleLanguage(): String {
        return UserPreferences.subtitleLanguage
    }

    /**
     * Set subtitle language.
     */
    fun setSubtitleLanguage(lang: String) {
        UserPreferences.subtitleLanguage = lang
    }

    /**
     * Get display name for a language code.
     */
    fun getLanguageDisplayName(code: String): String {
        return SUBTITLE_LANGUAGES.firstOrNull { it.first == code }?.second ?: code
    }

    /**
     * Get all available subtitle languages.
     */
    fun getAvailableLanguages(): List<Pair<String, String>> = SUBTITLE_LANGUAGES
}
