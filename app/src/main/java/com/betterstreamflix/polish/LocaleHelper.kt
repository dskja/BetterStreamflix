package com.betterstreamflix.polish

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Locale helper — manages app locale and language preferences.
 */
object LocaleHelper {

    /**
     * Get the current app locale.
     */
    fun getCurrentLocale(context: Context): Locale {
        return context.resources.configuration.locales[0]
    }

    /**
     * Get supported languages.
     */
    fun getSupportedLanguages(): List<SupportedLanguage> {
        return listOf(
            SupportedLanguage("en", "English", "English"),
            SupportedLanguage("de", "Deutsch", "German"),
            SupportedLanguage("es", "Español", "Spanish"),
            SupportedLanguage("fr", "Français", "French"),
            SupportedLanguage("it", "Italiano", "Italian"),
            SupportedLanguage("pt", "Português", "Portuguese"),
            SupportedLanguage("ru", "Русский", "Russian"),
            SupportedLanguage("ja", "日本語", "Japanese"),
            SupportedLanguage("ko", "한국어", "Korean"),
            SupportedLanguage("zh", "中文", "Chinese"),
        )
    }

    /**
     * Get a supported language by code.
     */
    fun getLanguageByCode(code: String): SupportedLanguage? {
        return getSupportedLanguages().find { it.code == code }
    }

    /**
     * Apply a locale to the app configuration.
     */
    fun applyLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    /**
     * Format a locale for display.
     */
    fun formatLocale(locale: Locale): String {
        return locale.getDisplayName(locale)
    }

    data class SupportedLanguage(
        val code: String,
        val nativeName: String,
        val englishName: String,
    )
}
