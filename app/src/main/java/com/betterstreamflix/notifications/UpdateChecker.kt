package com.betterstreamflix.notifications

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Update checker — checks for app updates from GitHub releases.
 */
object UpdateChecker {

    private const val PREFS_NAME = "update_check"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_LATEST_VERSION = "latest_version"
    private const val KEY_UPDATE_URL = "update_url"
    private const val KEY_UPDATE_NOTIFIED = "update_notified"

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
        val releaseNotes: String,
        val isPreRelease: Boolean,
        val publishedAt: Long,
    )

    /**
     * Check if an update check is needed.
     */
    fun shouldCheckForUpdate(context: Context, intervalMs: Long = 24 * 60 * 60 * 1000L): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
        return System.currentTimeMillis() - lastCheck > intervalMs
    }

    /**
     * Record an update check.
     */
    fun recordCheck(context: Context, latestVersion: String?, updateUrl: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            latestVersion?.let { putString(KEY_LATEST_VERSION, it) }
            updateUrl?.let { putString(KEY_UPDATE_URL, it) }
            putBoolean(KEY_UPDATE_NOTIFIED, false)
        }
    }

    /**
     * Check if an update is available.
     */
    fun isUpdateAvailable(context: Context, currentVersionName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val latestVersion = prefs.getString(KEY_LATEST_VERSION, null) ?: return false
        return compareVersions(latestVersion, currentVersionName) > 0
    }

    /**
     * Check if the user has been notified about the update.
     */
    fun hasBeenNotified(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_UPDATE_NOTIFIED, false)
    }

    /**
     * Mark that the user has been notified.
     */
    fun markNotified(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_UPDATE_NOTIFIED, true)
        }
    }

    /**
     * Get the stored update info.
     */
    fun getStoredUpdateInfo(context: Context): Pair<String?, String?> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LATEST_VERSION, null) to prefs.getString(KEY_UPDATE_URL, null)
    }

    /**
     * Compare version strings.
     * Returns positive if v1 > v2, negative if v1 < v2, 0 if equal.
     */
    fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 > p2) return 1
            if (p1 < p2) return -1
        }
        return 0
    }
}
