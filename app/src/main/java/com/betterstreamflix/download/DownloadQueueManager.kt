package com.betterstreamflix.download

import android.content.Context
import androidx.core.content.edit

/**
 * Download queue manager — manages the download queue with priority,
 * pause/resume, and concurrent download limits.
 */
object DownloadQueueManager {

    private const val PREFS_NAME = "download_queue"
    private const val KEY_MAX_CONCURRENT = "max_concurrent"
    private const val KEY_AUTO_DOWNLOAD = "auto_download"

    private val queue = mutableListOf<DownloadTask>()
    private val activeDownloads = mutableMapOf<String, DownloadTask>()

    data class DownloadTask(
        val id: String,
        val contentId: String,
        val title: String,
        val providerName: String,
        val url: String,
        val priority: Int = 0,
        val totalBytes: Long = 0,
        val downloadedBytes: Long = 0,
        val status: DownloadStatus = DownloadStatus.QUEUED,
        val createdAt: Long = System.currentTimeMillis(),
    )

    enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }

    /**
     * Add a task to the queue.
     */
    fun enqueue(task: DownloadTask) {
        if (queue.none { it.id == task.id }) {
            queue.add(task)
            queue.sortByDescending { it.priority }
        }
    }

    /**
     * Get the next task to download.
     */
    fun dequeue(): DownloadTask? {
        return queue.firstOrNull { it.status == DownloadStatus.QUEUED }
    }

    /**
     * Remove a task from the queue.
     */
    fun remove(taskId: String) {
        queue.removeAll { it.id == taskId }
        activeDownloads.remove(taskId)
    }

    /**
     * Get all queued tasks.
     */
    fun getQueue(): List<DownloadTask> = queue.toList()

    /**
     * Get active downloads.
     */
    fun getActiveDownloads(): List<DownloadTask> = activeDownloads.values.toList()

    /**
     * Update task progress.
     */
    fun updateProgress(taskId: String, downloadedBytes: Long) {
        queue.indexOfFirst { it.id == taskId }.let { index ->
            if (index >= 0) {
                queue[index] = queue[index].copy(downloadedBytes = downloadedBytes)
            }
        }
    }

    /**
     * Mark a task as active.
     */
    fun markActive(task: DownloadTask) {
        activeDownloads[task.id] = task
        queue.indexOfFirst { it.id == task.id }.let { index ->
            if (index >= 0) {
                queue[index] = queue[index].copy(status = DownloadStatus.DOWNLOADING)
            }
        }
    }

    /**
     * Mark a task as completed.
     */
    fun markCompleted(taskId: String) {
        activeDownloads.remove(taskId)
        queue.indexOfFirst { it.id == taskId }.let { index ->
            if (index >= 0) {
                queue[index] = queue[index].copy(status = DownloadStatus.COMPLETED)
            }
        }
    }

    /**
     * Mark a task as failed.
     */
    fun markFailed(taskId: String) {
        activeDownloads.remove(taskId)
        queue.indexOfFirst { it.id == taskId }.let { index ->
            if (index >= 0) {
                queue[index] = queue[index].copy(status = DownloadStatus.FAILED)
            }
        }
    }

    /**
     * Pause a download.
     */
    fun pause(taskId: String) {
        activeDownloads.remove(taskId)
        queue.indexOfFirst { it.id == taskId }.let { index ->
            if (index >= 0) {
                queue[index] = queue[index].copy(status = DownloadStatus.PAUSED)
            }
        }
    }

    /**
     * Resume a paused download.
     */
    fun resume(taskId: String) {
        queue.indexOfFirst { it.id == taskId }.let { index ->
            if (index >= 0) {
                queue[index] = queue[index].copy(status = DownloadStatus.QUEUED)
                queue.sortByDescending { it.priority }
            }
        }
    }

    /**
     * Clear completed downloads from queue.
     */
    fun clearCompleted() {
        queue.removeAll { it.status == DownloadStatus.COMPLETED }
    }

    /**
     * Clear all downloads.
     */
    fun clearAll() {
        queue.clear()
        activeDownloads.clear()
    }

    /**
     * Get max concurrent downloads setting.
     */
    fun getMaxConcurrent(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MAX_CONCURRENT, 3)
    }

    /**
     * Set max concurrent downloads.
     */
    fun setMaxConcurrent(context: Context, max: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_MAX_CONCURRENT, max).apply()
    }

    /**
     * Check if auto-download is enabled.
     */
    fun isAutoDownloadEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_DOWNLOAD, false)
    }

    /**
     * Set auto-download enabled.
     */
    fun setAutoDownload(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_DOWNLOAD, enabled).apply()
    }

    /**
     * Get queue size.
     */
    fun getQueueSize(): Int = queue.size
}
