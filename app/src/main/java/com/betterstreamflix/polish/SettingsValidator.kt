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
        val prefs = com.betterstreamflix.utils.UserPreferences.getInstance(context)

        // Validate playback speed
        val speed = prefs.playbackSpeed
        if (speed !in 0.25f..4.0f) {
            prefs.playbackSpeed = 1.0f
            issues.add(SettingIssue("playback_speed", "Invalid playback speed, reset to 1.0x"))
        }

        // Validate seek amount
        val seekAmount = prefs.seekAmountMs
        if (seekAmount !in 1_000..120_000) {
            prefs.seekAmountMs = 10_000
            issues.add(SettingIssue("seek_amount", "Invalid seek amount, reset to 10s"))
        }

        // Validate provider URL
        val providerUrl = prefs.providerUrl
        if (providerUrl.isNullOrEmpty()) {
            issues.add(SettingIssue("provider_url", "No provider URL configured"))
        }

        // Validate cache size
        val cacheSize = prefs.cacheSizeMb
        if (cacheSize <= 0 || cacheSize > 1024) {
            prefs.cacheSizeMb = 200
            issues.add(SettingIssue("cache_size", "Invalid cache size, reset to 200MB"))
        }

        return issues
    }

    /**
     * Reset all settings to defaults.
     */
    fun resetToDefaults(context: Context) {
        val prefs = com.betterstreamflix.utils.UserPreferences.getInstance(context)
        prefs.playbackSpeed = 1.0f
        prefs.seekAmountMs = 10_000
        prefs.cacheSizeMb = 200
        prefs.subtitleEnabled = true
        prefs.autoPlayNext = false
    }

    /**
     * Check if all required settings are configured.
     */
    fun areRequiredSettingsConfigured(context: Context): Boolean {
        val prefs = com.betterstreamflix.utils.UserPreferences.getInstance(context)
        return !prefs.providerUrl.isNullOrEmpty()
    }

    data class SettingIssue(
        val key: String,
        val message: String,
    )
}
