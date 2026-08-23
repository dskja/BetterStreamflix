package com.betterstreamflix.utils

import com.betterstreamflix.BuildConfig
import androidx.core.content.edit

/**
 * Feature flag system for enabling/disabling features at runtime.
 * Flags can be controlled by BuildConfig or UserPreferences.
 */
object FeatureFlags {

    // === Feature flag keys ===
    const val CLOUD_SYNC = "cloud_sync"
    const val IN_APP_UPDATER = "in_app_updater"
    const val ARTWORK_REPAIR = "artwork_repair"
    const val AUTO_CACHE_CLEAR = "auto_cache_clear"
    const val GLOBAL_SEARCH = "global_search"
    const val SUBTITLE_DOWNLOAD = "subtitle_download"
    const val PIP_MODE = "pip_mode"
    const val SKIP_INTRO = "skip_intro"
    const val NEXT_EPISODE_OVERLAY = "next_episode_overlay"
    const val DNS_OVER_HTTPS = "dns_over_https"
    const val PROXY_SUPPORT = "proxy_support"

    // === Default values ===
    private val defaults = mapOf(
        CLOUD_SYNC to true,
        IN_APP_UPDATER to true,
        ARTWORK_REPAIR to true,
        AUTO_CACHE_CLEAR to true,
        GLOBAL_SEARCH to true,
        SUBTITLE_DOWNLOAD to true,
        PIP_MODE to true,
        SKIP_INTRO to true,
        NEXT_EPISODE_OVERLAY to true,
        DNS_OVER_HTTPS to true,
        PROXY_SUPPORT to false,
    )

    /**
     * Check if a feature is enabled.
     */
    fun isEnabled(feature: String): Boolean {
        // Check UserPreferences override first
        val prefValue = UserPreferences.prefs.getBoolean("feature_$feature", false)
        if (UserPreferences.prefs.contains("feature_$feature")) return prefValue
        // Fall back to default
        return defaults[feature] ?: false
    }

    /**
     * Enable or disable a feature at runtime.
     */
    fun setEnabled(feature: String, enabled: Boolean) {
        UserPreferences.prefs.edit { putBoolean("feature_$feature", enabled) }
    }

    /**
     * Reset a feature flag to its default value.
     */
    fun reset(feature: String) {
        UserPreferences.prefs.edit { remove("feature_$feature") }
    }

    /**
     * Get all feature flags with their current state.
     */
    fun getAll(): Map<String, Boolean> {
        return defaults.mapValues { (key, _) -> isEnabled(key) }
    }
}
