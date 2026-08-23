package com.betterstreamflix.player

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manages playback position persistence for resume-playback functionality.
 * Stores position per video+provider combination.
 */
object PlaybackPositionManager {

    private const val PREFS_NAME = "playback_positions"
    private const val KEY_PREFIX = "pos_"
    private const val KEY_DURATION_PREFIX = "dur_"
    private const val MAX_ENTRIES = 500

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save playback position for a video.
     */
    fun savePosition(context: Context, videoId: String, providerName: String, positionMs: Long, durationMs: Long) {
        val key = buildKey(videoId, providerName)
        getPrefs(context).edit {
            putLong(key, positionMs)
            putLong(KEY_DURATION_PREFIX + key, durationMs)
        }
    }

    /**
     * Get saved playback position for a video.
     */
    fun getPosition(context: Context, videoId: String, providerName: String): Long {
        return getPrefs(context).getLong(buildKey(videoId, providerName), 0)
    }

    /**
     * Get saved duration for a video.
     */
    fun getDuration(context: Context, videoId: String, providerName: String): Long {
        return getPrefs(context).getLong(KEY_DURATION_PREFIX + buildKey(videoId, providerName), 0)
    }

    /**
     * Check if a video has a saved position worth resuming (>30s and <90%).
     */
    fun hasResumePosition(context: Context, videoId: String, providerName: String): Boolean {
        val pos = getPosition(context, videoId, providerName)
        val dur = getDuration(context, videoId, providerName)
        if (pos < 30_000) return false
        if (dur > 0 && pos.toFloat() / dur > 0.9f) return false
        return true
    }

    /**
     * Get resume position in seconds.
     */
    fun getResumePositionSeconds(context: Context, videoId: String, providerName: String): Int {
        return (getPosition(context, videoId, providerName) / 1000).toInt()
    }

    /**
     * Clear saved position for a video.
     */
    fun clearPosition(context: Context, videoId: String, providerName: String) {
        val key = buildKey(videoId, providerName)
        getPrefs(context).edit {
            remove(key)
            remove(KEY_DURATION_PREFIX + key)
        }
    }

    /**
     * Clear all positions.
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit { clear() }
    }

    private fun buildKey(videoId: String, providerName: String) = "$KEY_PREFIX${providerName}_$videoId"
}
