package com.betterstreamflix.resilience

import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.PlaybackException

/**
 * Crash recovery helper — saves player state for recovery after crash
 * and restores playback position after restart.
 */
object CrashRecoveryHelper {

    private const val KEY_LAST_VIDEO_ID = "last_video_id"
    private const val KEY_LAST_POSITION = "last_position"
    private const val KEY_LAST_PROVIDER = "last_provider"
    private const val KEY_LAST_TITLE = "last_title"
    private const val KEY_CRASH_TIMESTAMP = "crash_timestamp"

    /**
     * Save current playback state for crash recovery.
     */
    fun savePlaybackState(
        prefs: android.content.SharedPreferences,
        videoId: String,
        positionMs: Long,
        providerName: String,
        title: String,
    ) {
        prefs.edit {
            putString(KEY_LAST_VIDEO_ID, videoId)
            putLong(KEY_LAST_POSITION, positionMs)
            putString(KEY_LAST_PROVIDER, providerName)
            putString(KEY_LAST_TITLE, title)
            putLong(KEY_CRASH_TIMESTAMP, System.currentTimeMillis())
        }
    }

    /**
     * Check if there's a recoverable playback session.
     */
    fun hasRecoverableSession(prefs: android.content.SharedPreferences): Boolean {
        val videoId = prefs.getString(KEY_LAST_VIDEO_ID, null) ?: return false
        val timestamp = prefs.getLong(KEY_CRASH_TIMESTAMP, 0)
        // Only offer recovery within 30 minutes
        return System.currentTimeMillis() - timestamp < 30 * 60 * 1000 && videoId.isNotBlank()
    }

    /**
     * Get the saved playback state.
     */
    fun getSavedState(prefs: android.content.SharedPreferences): SavedPlaybackState? {
        val videoId = prefs.getString(KEY_LAST_VIDEO_ID, null) ?: return null
        return SavedPlaybackState(
            videoId = videoId,
            positionMs = prefs.getLong(KEY_LAST_POSITION, 0),
            providerName = prefs.getString(KEY_LAST_PROVIDER, null),
            title = prefs.getString(KEY_LAST_TITLE, null),
            timestamp = prefs.getLong(KEY_CRASH_TIMESTAMP, 0),
        )
    }

    /**
     * Clear the saved playback state.
     */
    fun clearSavedState(prefs: android.content.SharedPreferences) {
        prefs.edit {
            remove(KEY_LAST_VIDEO_ID)
            remove(KEY_LAST_POSITION)
            remove(KEY_LAST_PROVIDER)
            remove(KEY_LAST_TITLE)
            remove(KEY_CRASH_TIMESTAMP)
        }
    }

    /**
     * Handle a playback exception and decide on recovery action.
     */
    fun handlePlaybackException(
        exception: PlaybackException,
        player: ExoPlayer,
    ): RecoveryAction {
        return when (exception.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> RecoveryAction.Retry
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED -> RecoveryAction.RetryWithLowerQuality
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> RecoveryAction.SkipToNext
            else -> RecoveryAction.ShowError
        }
    }

    data class SavedPlaybackState(
        val videoId: String,
        val positionMs: Long,
        val providerName: String?,
        val title: String?,
        val timestamp: Long,
    )

    enum class RecoveryAction {
        Retry,
        RetryWithLowerQuality,
        SkipToNext,
        ShowError,
    }
}
