package com.betterstreamflix.player.advanced

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.VideoSize

/**
 * Video quality manager — manages adaptive quality selection and
 * provides manual quality override.
 */
object VideoQualityManager {

    /**
     * Available quality levels.
     */
    data class QualityLevel(
        val height: Int,
        val label: String,
        val bitrate: Int,
    )

    val QUALITY_LEVELS = listOf(
        QualityLevel(2160, "4K", 25_000_000),
        QualityLevel(1080, "1080p", 8_000_000),
        QualityLevel(720, "720p", 5_000_000),
        QualityLevel(480, "480p", 2_500_000),
        QualityLevel(360, "360p", 1_000_000),
        QualityLevel(240, "240p", 500_000),
    )

    /**
     * Get quality level by height.
     */
    fun getQualityByHeight(height: Int): QualityLevel? {
        return QUALITY_LEVELS.firstOrNull { it.height == height }
            ?: QUALITY_LEVELS.minByOrNull { kotlin.math.abs(it.height - height) }
    }

    /**
     * Get the best quality for current network conditions.
     */
    fun getRecommendedQuality(
        isWifi: Boolean,
        isMetered: Boolean,
        userPreference: Int? = null,
    ): QualityLevel {
        if (userPreference != null) {
            return getQualityByHeight(userPreference) ?: QUALITY_LEVELS[1]
        }

        return when {
            isMetered -> QUALITY_LEVELS.first { it.height == 480 }
            isWifi -> QUALITY_LEVELS.first { it.height == 1080 }
            else -> QUALITY_LEVELS.first { it.height == 720 }
        }
    }

    /**
     * Get current video quality from player.
     */
    fun getCurrentQuality(player: ExoPlayer): QualityLevel? {
        val videoSize: VideoSize = player.videoSize
        if (videoSize.height == 0) return null
        return getQualityByHeight(videoSize.height)
    }

    /**
     * Format quality label with resolution.
     */
    fun formatQualityLabel(player: ExoPlayer): String {
        val quality = getCurrentQuality(player) ?: return "Auto"
        val width = player.videoSize.width
        val height = player.videoSize.height
        return if (width > 0 && height > 0) "${quality.label} (${width}x${height})" else quality.label
    }
}
