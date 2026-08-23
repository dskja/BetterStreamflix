package com.betterstreamflix.resilience

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException

/**
 * Playback retry handler — manages retry logic for failed playback
 * with quality reduction and server fallback.
 */
class PlaybackRetryHandler(
    private val maxRetries: Int = 3,
) {
    private var retryCount = 0
    private var lastMediaItem: MediaItem? = null
    private var lastPosition: Long = 0

    /**
     * Save the current playback state before a retry.
     */
    fun saveState(player: ExoPlayer) {
        lastMediaItem = player.currentMediaItem
        lastPosition = player.currentPosition
    }

    /**
     * Attempt a retry based on the playback exception.
     */
    fun shouldRetry(exception: PlaybackException): RetryDecision {
        if (retryCount >= maxRetries) return RetryDecision.GiveUp

        val action = CrashRecoveryHelper.handlePlaybackException(exception, ExoPlayer.Builder(android.app.Application()).build())

        retryCount++
        return when (action) {
            CrashRecoveryHelper.RecoveryAction.Retry -> RetryDecision.RetrySameSource
            CrashRecoveryHelper.RecoveryAction.RetryWithLowerQuality -> RetryDecision.RetryLowerQuality
            CrashRecoveryHelper.RecoveryAction.SkipToNext -> RetryDecision.SkipToNext
            CrashRecoveryHelper.RecoveryAction.ShowError -> RetryDecision.GiveUp
        }
    }

    /**
     * Reset retry count on successful playback.
     */
    fun reset() {
        retryCount = 0
        lastMediaItem = null
        lastPosition = 0
    }

    /**
     * Get the saved position for resume.
     */
    fun getSavedPosition(): Long = lastPosition

    /**
     * Get the saved media item.
     */
    fun getSavedMediaItem(): MediaItem? = lastMediaItem

    sealed class RetryDecision {
        data object RetrySameSource : RetryDecision()
        data object RetryLowerQuality : RetryDecision()
        data object SkipToNext : RetryDecision()
        data object GiveUp : RetryDecision()
    }
}
