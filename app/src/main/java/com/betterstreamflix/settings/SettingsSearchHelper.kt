package com.betterstreamflix.settings

import com.betterstreamflix.utils.UserPreferences

/**
 * Settings search helper — searches across all settings for quick access.
 */
object SettingsSearchHelper {

    data class SettingsSearchResult(
        val category: SettingsCategories.Category,
        val settingKey: String,
        val title: String,
        val currentValue: String,
    )

    /**
     * Search settings by query.
     */
    fun search(query: String): List<SettingsSearchResult> {
        if (query.isBlank()) return emptyList()
        val lowerQuery = query.lowercase()
        return getAllSettings().filter { result ->
            result.title.lowercase().contains(lowerQuery) ||
            result.category.title.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Get all searchable settings with current values.
     */
    private fun getAllSettings(): List<SettingsSearchResult> {
        return listOf(
            SettingsSearchResult(SettingsCategories.Category.PLAYBACK, "autoplay", "Autoplay", UserPreferences.autoplay.toString()),
            SettingsSearchResult(SettingsCategories.Category.PLAYBACK, "autoplay_buffer", "Autoplay Buffer", "${UserPreferences.autoplayBuffer}s"),
            SettingsSearchResult(SettingsCategories.Category.VIDEO, "quality", "Video Quality", UserPreferences.qualityHeight?.let { "${it}p" } ?: "Auto"),
            SettingsSearchResult(SettingsCategories.Category.PROVIDER, "provider", "Current Provider", UserPreferences.currentProvider?.name ?: "None"),
            SettingsSearchResult(SettingsCategories.Category.LANGUAGE, "app_language", "App Language", UserPreferences.appLanguage),
            SettingsSearchResult(SettingsCategories.Category.SUBTITLES, "subtitle_language", "Subtitle Language", UserPreferences.subtitleLanguage),
        )
    }
}
