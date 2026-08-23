package com.betterstreamflix.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * Locale manager — handles app language switching and locale persistence.
 */
object LocaleManager {

    /**
     * Get the current app locale.
     */
    fun getCurrentLocale(context: Context): Locale {
        return context.resources.configuration.locales[0]
    }

    /**
     * Set the app locale.
     */
    fun setLocale(activity: AppCompatActivity, languageCode: String) {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(activity.resources.configuration)
        config.setLocale(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleListCompat.forLanguageTags(languageCode).toLocaleList())
        }

        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)

        // Store preference
        com.betterstreamflix.utils.UserPreferences.appLanguage = languageCode
    }

    /**
     * Apply saved locale on app start.
     */
    fun applySavedLocale(context: Context) {
        val lang = com.betterstreamflix.utils.UserPreferences.appLanguage
        if (lang.isNotEmpty() && lang != "en") {
            val locale = Locale.forLanguageTag(lang)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }

    /**
     * Get supported languages.
     */
    fun getSupportedLanguages(): List<Pair<String, String>> {
        return listOf(
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
            "ru" to "Русский",
            "pl" to "Polski",
            "nl" to "Nederlands",
            "cs" to "Čeština",
        )
    }

    /**
     * Get the display name for a language code.
     */
    fun getLanguageDisplayName(code: String): String {
        return getSupportedLanguages().firstOrNull { it.first == code }?.second ?: code
    }
}
