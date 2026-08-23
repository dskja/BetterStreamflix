package com.betterstreamflix.settings

import com.betterstreamflix.utils.UserPreferences

/**
 * Quality settings helper — manages video quality preferences.
 */
object QualitySettingsHelper {

    val QUALITY_OPTIONS = listOf(
        "Auto" to null,
        "360p" to 360,
        "480p" to 480,
        "720p" to 720,
        "1080p" to 1080,
        "1440p" to 1440,
        "2160p (4K)" to 2160,
    )

    /**
     * Get current quality setting.
     */
    fun getCurrentQuality(): Pair<String, Int?> {
        val height = UserPreferences.qualityHeight
        val label = QUALITY_OPTIONS.firstOrNull { it.second == height }?.first ?: "Auto"
        return label to height
    }

    /**
     * Set quality by height.
     */
    fun setQuality(height: Int?) {
        UserPreferences.qualityHeight = height
    }

    /**
     * Get recommended quality based on connection type.
     */
    fun getRecommendedQuality(isWifi: Boolean, isTv: Boolean): Int? {
        return when {
            isTv -> 1080
            isWifi -> 720
            else -> 480
        }
    }
}
