package com.betterstreamflix.i18n

import java.util.Locale

/**
 * Language detector — auto-detects the best language based on system locale
 * and available provider languages.
 */
object LanguageDetector {

    /**
     * Detect the best language for the app based on system locale.
     */
    fun detectBestLanguage(systemLocale: Locale = Locale.getDefault()): String {
        val systemLang = systemLocale.language
        val supported = LocaleManager.getSupportedLanguages().map { it.first }
        return when {
            supported.contains(systemLang) -> systemLang
            supported.contains("${systemLang}-${systemLocale.country.lowercase()}") -> systemLang
            else -> "en"
        }
    }

    /**
     * Check if a language is RTL (right-to-left).
     */
    fun isRtl(languageCode: String): Boolean {
        return languageCode in listOf("ar", "he", "fa", "ur")
    }

    /**
     * Get the text direction for a language.
     */
    fun getTextDirection(languageCode: String): String {
        return if (isRtl(languageCode)) "rtl" else "ltr"
    }

    /**
     * Get provider language from app language.
     */
    fun getProviderLanguage(appLanguage: String): String {
        return appLanguage.substringBefore("-")
    }
}
