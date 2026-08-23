package com.betterstreamflix.polish

import android.content.Context
import androidx.core.content.edit

/**
 * Settings validator — validates user settings and provides defaults
 * for invalid or missing values.
 */
object SettingsValidator {

    /**
     * Validate and fix all settings.
     */
    fun validateAndFix(context: Context): List<SettingIssue> {
        val issues = mutableListOf<SettingIssue>()
        val prefs = com.betterstreamflix.utils.UserPreferences.prefs

        // Validate playback speed
        val speed = prefs.getFloat("playback_speed", 1.0f)
        if (speed !in 0.25f..4.0f) {
            prefs.edit { putFloat("playback_speed", 1.0f) }
            issues.add(SettingIssue("playback_speed", "Invalid playback speed, reset to 1.0x"))
        }

        // Validate seek amount
        val seekAmount = prefs.getInt("seek_amount_ms", 10000)
        if (seekAmount !in 1_000..120_000) {
            prefs.edit { putInt("seek_amount_ms", 10_000) }
            issues.add(SettingIssue("seek_amount", "Invalid seek amount, reset to 10s"))
        }

        // Validate provider URL
        val providerUrl = com.betterstreamflix.utils.UserPreferences.providerUrl
        if (providerUrl.isNullOrEmpty()) {
            issues.add(SettingIssue("provider_url", "No provider URL configured"))
        }

        // Validate cache size
        val cacheSize = prefs.getInt("cache_size_mb", 200)
        if (cacheSize <= 0 || cacheSize > 1024) {
            prefs.edit { putInt("cache_size_mb", 200) }
            issues.add(SettingIssue("cache_size", "Invalid cache size, reset to 200MB"))
        }

        return issues
    }

    /**
     * Reset all settings to defaults.
     */
    fun resetToDefaults(context: Context) {
        val prefs = com.betterstreamflix.utils.UserPreferences.prefs
        prefs.edit {
            putFloat("playback_speed", 1.0f)
            putInt("seek_amount_ms", 10_000)
            putInt("cache_size_mb", 200)
            putBoolean("subtitle_enabled", true)
            putBoolean("auto_play_next", false)
        }
    }

    /**
     * Check if all required settings are configured.
     */
    fun areRequiredSettingsConfigured(context: Context): Boolean {
        val providerUrl = com.betterstreamflix.utils.UserPreferences.providerUrl
        return !providerUrl.isNullOrEmpty()
    }

    data class SettingIssue(
        val key: String,
        val message: String,
    )
}
