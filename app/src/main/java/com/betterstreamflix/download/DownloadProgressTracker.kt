package com.betterstreamflix.download

import android.content.Context
import androidx.core.content.edit

/**
 * Download progress tracker — tracks download progress and provides
 * reactive updates.
 */
object DownloadProgressTracker {

    private const val PREFS_NAME = "download_progress"
    private val progressMap = mutableMapOf<String, DownloadProgress>()
    private val listeners = mutableListOf<(String, DownloadProgress) -> Unit>()

    data class DownloadProgress(
        val downloadId: String,
        val contentId: String,
        val title: String,
        val totalBytes: Long,
        val downloadedBytes: Long,
        val speedBytesPerSec: Long,
        val etaSeconds: Long,
        val percent: Int,
        val status: DownloadQueueManager.DownloadStatus,
    )

    /**
     * Update progress for a download.
     */
    fun updateProgress(progress: DownloadProgress) {
        progressMap[progress.downloadId] = progress
        listeners.forEach { it(progress.downloadId, progress) }
        // Persist periodically
    }

    /**
     * Get progress for a download.
     */
    fun getProgress(downloadId: String): DownloadProgress? = progressMap[downloadId]

    /**
     * Get all active progress.
     */
    fun getAllProgress(): List<DownloadProgress> = progressMap.values.toList()

    /**
     * Add a progress listener.
     */
    fun addListener(listener: (String, DownloadProgress) -> Unit) {
        listeners.add(listener)
    }

    /**
     * Remove a listener.
     */
    fun removeListener(listener: (String, DownloadProgress) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Remove progress for a download.
     */
    fun removeProgress(downloadId: String) {
        progressMap.remove(downloadId)
    }

    /**
     * Clear all progress.
     */
    fun clearAll() {
        progressMap.clear()
    }

    /**
     * Calculate download speed.
     */
    fun calculateSpeed(downloadedBytes: Long, startTimeMs: Long): Long {
        val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000.0
        return if (elapsedSec > 0) (downloadedBytes / elapsedSec).toLong() else 0
    }

    /**
     * Calculate ETA.
     */
    fun calculateEta(remainingBytes: Long, speedBytesPerSec: Long): Long {
        return if (speedBytesPerSec > 0) remainingBytes / speedBytesPerSec else 0
    }

    /**
     * Calculate percentage.
     */
    fun calculatePercent(downloaded: Long, total: Long): Int {
        return if (total > 0) ((downloaded.toDouble() / total) * 100).toInt() else 0
    }

    /**
     * Format speed for display.
     */
    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / 1024.0 / 1024)
            bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }

    /**
     * Format ETA for display.
     */
    fun formatEta(seconds: Long): String {
        return when {
            seconds >= 3600 -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
            seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}
