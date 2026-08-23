package com.betterstreamflix.providers

import com.betterstreamflix.utils.UserPreferences
import java.util.Locale

/**
 * Provider language manager — handles multi-language provider selection
 * and language-based filtering.
 */
object ProviderLanguageManager {

    /**
     * Get all available languages from registered providers.
     */
    fun getAvailableLanguages(): List<String> {
        return Provider.providers.keys
            .map { it.language }
            .distinct()
            .sorted()
    }

    /**
     * Get providers for a specific language.
     */
    fun getProvidersForLanguage(language: String): List<Provider> {
        return Provider.providers.keys.filter { provider ->
            provider.language.equals(language, ignoreCase = true) ||
            provider.language.startsWith("${language}-")
        }
    }

    /**
     * Get the current provider's language.
     */
    fun getCurrentLanguage(): String {
        return UserPreferences.currentProvider?.language ?: "en"
    }

    /**
     * Get a display name for a language code.
     */
    fun getLanguageDisplayName(languageCode: String): String {
        val locale = Locale.forLanguageTag(languageCode)
        return locale.getDisplayLanguage(Locale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    /**
     * Get providers grouped by language.
     */
    fun getProvidersGroupedByLanguage(): Map<String, List<Provider>> {
        return Provider.providers.keys
            .groupBy { it.language.substringBefore("-") }
    }
}
