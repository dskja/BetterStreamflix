package com.betterstreamflix.download

import android.content.Context
import androidx.core.content.edit

/**
 * Download resume manager — manages download resume points for
 * interrupted downloads.
 */
object DownloadResumeManager {

    private const val PREFS_NAME = "download_resume"

    /**
     * Save resume point for a download.
     */
    fun saveResumePoint(context: Context, downloadId: String, bytesDownloaded: Long, etag: String?, lastModified: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong("${downloadId}_bytes", bytesDownloaded)
            .putString("${downloadId}_etag", etag)
            .putString("${downloadId}_modified", lastModified)
            .putLong("${downloadId}_timestamp", System.currentTimeMillis())
            .apply()
    }

    /**
     * Get resume point for a download.
     */
    fun getResumePoint(context: Context, downloadId: String): ResumePoint? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val bytes = prefs.getLong("${downloadId}_bytes", -1)
        if (bytes < 0) return null

        return ResumePoint(
            bytesDownloaded = bytes,
            etag = prefs.getString("${downloadId}_etag", null),
            lastModified = prefs.getString("${downloadId}_modified", null),
            timestamp = prefs.getLong("${downloadId}_timestamp", 0),
        )
    }

    /**
     * Clear resume point for a download.
     */
    fun clearResumePoint(context: Context, downloadId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove("${downloadId}_bytes")
            .remove("${downloadId}_etag")
            .remove("${downloadId}_modified")
            .remove("${downloadId}_timestamp")
            .apply()
    }

    /**
     * Check if a download can be resumed.
     */
    fun canResume(context: Context, downloadId: String): Boolean {
        val point = getResumePoint(context, downloadId) ?: return false
        // Resume points expire after 7 days
        val maxAge = 7 * 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - point.timestamp < maxAge && point.bytesDownloaded > 0
    }

    /**
     * Clear all resume points.
     */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }

    data class ResumePoint(
        val bytesDownloaded: Long,
        val etag: String?,
        val lastModified: String?,
        val timestamp: Long,
    )
}
