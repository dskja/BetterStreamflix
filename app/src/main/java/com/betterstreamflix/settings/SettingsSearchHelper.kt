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

    fun search(query: String): List<SettingsSearchResult> {
        if (query.isBlank()) return emptyList()
        val lowerQuery = query.lowercase()
        return getAllSettings().filter { result ->
            result.title.lowercase().contains(lowerQuery) ||
                result.category.title.lowercase().contains(lowerQuery) ||
                result.currentValue.lowercase().contains(lowerQuery)
        }
    }

    private fun getAllSettings(): List<SettingsSearchResult> = listOf(
        SettingsSearchResult(SettingsCategories.Category.PLAYBACK, "autoplay", "Autoplay", UserPreferences.autoplay.toString()),
        SettingsSearchResult(SettingsCategories.Category.PLAYBACK, "autoplay_buffer", "Autoplay Buffer", "${UserPreferences.autoplayBuffer}s"),
        SettingsSearchResult(SettingsCategories.Category.VIDEO, "quality", "Video Quality", UserPreferences.qualityHeight?.let { "${it}p" } ?: "Auto"),
        SettingsSearchResult(SettingsCategories.Category.PROVIDER, "provider", "Current Provider", UserPreferences.currentProvider?.name ?: "None"),
        SettingsSearchResult(SettingsCategories.Category.LANGUAGE, "app_language", "App Language", UserPreferences.appLanguage),
        SettingsSearchResult(SettingsCategories.Category.SUBTITLES, "subtitle_language", "Subtitle Language", UserPreferences.subtitleLanguage),
        SettingsSearchResult(SettingsCategories.Category.PROVIDER, "streamingcommunity_domain", "StreamingCommunity Domain", UserPreferences.streamingcommunityDomain),
        SettingsSearchResult(SettingsCategories.Category.PROVIDER, "serienstream_domain", "SerienStream Domain", UserPreferences.serienstreamDomain),
        SettingsSearchResult(SettingsCategories.Category.PROVIDER, "aniworld_domain", "AniWorld Domain", UserPreferences.aniworldDomain),
        SettingsSearchResult(SettingsCategories.Category.PROVIDER, "moflix_domain", "Moflix Domain", UserPreferences.moflixDomain),
        SettingsSearchResult(SettingsCategories.Category.PROVIDER, "cuevana_domain", "Cuevana Domain", UserPreferences.cuevanaDomain),
        SettingsSearchResult(SettingsCategories.Category.PROVIDER, "poseidon_domain", "Poseidon Domain", UserPreferences.poseidonDomain),
        SettingsSearchResult(SettingsCategories.Category.ADVANCED, "doh", "DNS over HTTPS", UserPreferences.dohProviderUrl),
        SettingsSearchResult(SettingsCategories.Category.APPEARANCE, "theme", "Theme", UserPreferences.selectedTheme),
        SettingsSearchResult(SettingsCategories.Category.ADVANCED, "tmdb", "TMDB Enabled", UserPreferences.enableTmdb.toString()),
        SettingsSearchResult(SettingsCategories.Category.ADVANCED, "parental_pin", "Parental PIN", if (UserPreferences.parentalControlPin.isNullOrBlank()) "Off" else "On"),
    )
}
