package com.betterstreamflix.player.advanced

import androidx.media3.exoplayer.ExoPlayer

/**
 * Buffer manager — manages player buffer configuration for
 * optimal playback under varying network conditions.
 */
object BufferManager {

    /**
     * Buffer configuration presets.
     */
    data class BufferConfig(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val bufferForPlaybackMs: Int,
        val bufferForPlaybackAfterRebufferMs: Int,
    )

    val BUFFER_PRESETS = mapOf(
        BufferMode.INSTANT to BufferConfig(5_000, 10_000, 500, 1_000),
        BufferMode.BALANCED to BufferConfig(15_000, 30_000, 1_000, 2_000),
        BufferMode.SMOOTH to BufferConfig(30_000, 60_000, 2_000, 5_000),
        BufferMode.DATA_SAVER to BufferConfig(3_000, 5_000, 1_000, 3_000),
    )

    /**
     * Apply buffer configuration to player.
     */
    fun applyBufferConfig(player: ExoPlayer, config: BufferConfig) {
        // Would use LoadControl in real implementation
        // ExoPlayer.Builder().setLoadControl(DefaultLoadControl.Builder()
        //     .setBufferDurationsMs(config.minBufferMs, config.maxBufferMs,
        //         config.bufferForPlaybackMs, config.bufferForPlaybackAfterRebufferMs)
        //     .build())
    }

    /**
     * Get recommended buffer mode based on network type.
     */
    fun getRecommendedBufferMode(
        isWifi: Boolean,
        isMetered: Boolean,
        connectionQuality: com.betterstreamflix.resilience.ConnectionStateManager.ConnectionQuality,
    ): BufferMode {
        return when {
            isMetered -> BufferMode.DATA_SAVER
            connectionQuality == com.betterstreamflix.resilience.ConnectionStateManager.ConnectionQuality.POOR -> BufferMode.INSTANT
            isWifi && connectionQuality == com.betterstreamflix.resilience.ConnectionStateManager.ConnectionQuality.GOOD -> BufferMode.SMOOTH
            else -> BufferMode.BALANCED
        }
    }

    /**
     * Get current buffer health in milliseconds.
     */
    fun getBufferHealthMs(player: ExoPlayer): Long {
        val bufferedPosition = player.bufferedPosition
        val currentPosition = player.currentPosition
        return (bufferedPosition - currentPosition).coerceAtLeast(0)
    }

    /**
     * Check if buffer is healthy.
     */
    fun isBufferHealthy(player: ExoPlayer, thresholdMs: Long = 5_000): Boolean {
        return getBufferHealthMs(player) >= thresholdMs
    }

    enum class BufferMode { INSTANT, BALANCED, SMOOTH, DATA_SAVER }
}
