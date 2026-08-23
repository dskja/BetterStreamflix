package com.betterstreamflix.player

import androidx.media3.exoplayer.ExoPlayer

/**
 * Manages playback speed control with presets and custom speeds.
 */
object PlaybackSpeedHelper {

    val SPEED_PRESETS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

    const val DEFAULT_SPEED = 1.0f

    /**
     * Set playback speed on an ExoPlayer.
     */
    fun setSpeed(player: ExoPlayer, speed: Float) {
        player.setPlaybackSpeed(speed.coerceIn(0.25f, 4.0f))
    }

    /**
     * Get the next speed preset after the current speed.
     */
    fun getNextSpeed(currentSpeed: Float): Float {
        val next = SPEED_PRESETS.firstOrNull { it > currentSpeed }
        return next ?: SPEED_PRESETS.lastOrNull() ?: currentSpeed
    }

    /**
     * Get the previous speed preset before the current speed.
     */
    fun getPreviousSpeed(currentSpeed: Float): Float {
        val prev = SPEED_PRESETS.lastOrNull { it < currentSpeed }
        return prev ?: SPEED_PRESETS.firstOrNull() ?: currentSpeed
    }

    /**
     * Format speed for display (e.g., "1.0x", "1.5x").
     */
    fun formatSpeed(speed: Float): String {
        return if (speed == speed.toInt().toFloat()) {
            "${speed.toInt()}.0x"
        } else {
            "${speed}x"
        }
    }

    /**
     * Check if speed is at default (1.0x).
     */
    fun isDefaultSpeed(speed: Float): Boolean = speed == DEFAULT_SPEED
}
