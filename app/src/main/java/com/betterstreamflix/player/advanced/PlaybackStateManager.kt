package com.betterstreamflix.player.advanced

import androidx.media3.exoplayer.ExoPlayer

/**
 * Playback state manager — tracks and persists playback state
 * including position, speed, and track selections.
 */
object PlaybackStateManager {

    data class PlaybackState(
        val positionMs: Long,
        val durationMs: Long,
        val playbackSpeed: Float,
        val isPlaying: Boolean,
        val repeatMode: Int,
        val shuffleMode: Boolean,
        val subtitleEnabled: Boolean,
        val audioLanguage: String?,
        val subtitleLanguage: String?,
    )

    /**
     * Capture current playback state from player.
     */
    fun captureState(player: ExoPlayer): PlaybackState {
        return PlaybackState(
            positionMs = player.currentPosition,
            durationMs = player.duration.coerceAtLeast(0),
            playbackSpeed = player.playbackParameters.speed,
            isPlaying = player.isPlaying,
            repeatMode = player.repeatMode,
            shuffleMode = player.shuffleModeEnabled,
            subtitleEnabled = player.trackSelectionParameters.maxSize > 0,
            audioLanguage = null,
            subtitleLanguage = null,
        )
    }

    /**
     * Restore playback state to player.
     */
    fun restoreState(player: ExoPlayer, state: PlaybackState) {
        player.seekTo(state.positionMs)
        player.setPlaybackSpeed(state.playbackSpeed)
        player.repeatMode = state.repeatMode
        player.shuffleModeEnabled = state.shuffleMode
    }

    /**
     * Calculate progress percentage.
     */
    fun getProgressPercent(state: PlaybackState): Float {
        if (state.durationMs <= 0) return 0f
        return (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Check if playback is near the end.
     */
    fun isNearEnd(state: PlaybackState, thresholdMs: Long = 60_000): Boolean {
        return state.durationMs > 0 && (state.durationMs - state.positionMs) < thresholdMs
    }

    /**
     * Format position as time string.
     */
    fun formatPosition(state: PlaybackState): String {
        val totalSeconds = state.positionMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
