package com.betterstreamflix.player

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Tracks player error history for diagnostics and retry decisions.
 */
object PlayerErrorTracker {

    private const val PREFS_NAME = "player_errors"
    private const val KEY_ERRORS = "error_list"
    private const val MAX_ERRORS = 50

    data class PlayerError(
        val timestamp: Long,
        val videoUrl: String,
        val errorMessage: String,
        val errorType: String,
    )

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Record a player error.
     */
    fun recordError(context: Context, videoUrl: String, errorMessage: String, errorType: String) {
        val errors = getErrors(context).toMutableList()
        errors.add(0, PlayerError(System.currentTimeMillis(), videoUrl, errorMessage, errorType))
        if (errors.size > MAX_ERRORS) {
            errors.subList(MAX_ERRORS, errors.size).clear()
        }
        saveErrors(context, errors)
    }

    /**
     * Get error history.
     */
    fun getErrors(context: Context): List<PlayerError> {
        val json = getPrefs(context).getString(KEY_ERRORS, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PlayerError(
                    timestamp = obj.getLong("timestamp"),
                    videoUrl = obj.getString("videoUrl"),
                    errorMessage = obj.getString("errorMessage"),
                    errorType = obj.getString("errorType"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get error count for a specific URL.
     */
    fun getErrorCountForUrl(context: Context, videoUrl: String): Int {
        return getErrors(context).count { it.videoUrl == videoUrl }
    }

    /**
     * Clear all errors.
     */
    fun clearErrors(context: Context) {
        getPrefs(context).edit { remove(KEY_ERRORS) }
    }

    private fun saveErrors(context: Context, errors: List<PlayerError>) {
        val arr = org.json.JSONArray()
        errors.forEach { err ->
            arr.put(org.json.JSONObject().apply {
                put("timestamp", err.timestamp)
                put("videoUrl", err.videoUrl)
                put("errorMessage", err.errorMessage)
                put("errorType", err.errorType)
            })
        }
        getPrefs(context).edit { putString(KEY_ERRORS, arr.toString()) }
    }
}
