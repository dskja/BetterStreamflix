package com.betterstreamflix.download

import android.content.Context
import com.betterstreamflix.utils.Logger

/**
 * Download queue processor — manages the download queue and processes tasks sequentially.
 */
class DownloadQueueProcessor(private val context: Context) {

    private val executor = DownloadExecutor(context)
    private val notificationHelper = DownloadNotificationHelper(context)
    private var isProcessing = false

    /**
     * Process the download queue.
     */
    suspend fun processQueue() {
        if (isProcessing) return
        isProcessing = true

        try {
            val activeDownloads = DownloadManager.getActiveDownloads(context)
            for (task in activeDownloads) {
                processTask(task)
            }
        } finally {
            isProcessing = false
        }
    }

    /**
     * Process a single download task.
     */
    private suspend fun processTask(task: DownloadManager.DownloadTask) {
        val notificationId = task.id.hashCode()

        DownloadManager.updateDownload(context, task.id) {
            it.copy(status = DownloadManager.DownloadStatus.DOWNLOADING)
        }

        notificationHelper.showProgressNotification(task.title, 0, notificationId)

        val outputFile = java.io.File(task.filePath)

        val result = executor.download(task.url, outputFile) { percent, downloaded, total ->
            DownloadManager.updateProgress(context, task.id, downloaded)
            notificationHelper.showProgressNotification(task.title, percent, notificationId)
        }

        result.onSuccess {
            DownloadManager.markCompleted(context, task.id)
            notificationHelper.showCompleteNotification(task.title, notificationId)
        }.onFailure { error ->
            DownloadManager.markFailed(context, task.id, error.message ?: "Unknown error")
            notificationHelper.showFailedNotification(task.title, error.message ?: "Unknown error", notificationId)
            Logger.e("DownloadQueue", "Download failed: ${task.title}", error)
        }
    }

    /**
     * Cancel all active downloads.
     */
    fun cancelAll() {
        val active = DownloadManager.getActiveDownloads(context)
        active.forEach { task ->
            DownloadManager.updateDownload(context, task.id) {
                it.copy(status = DownloadManager.DownloadStatus.CANCELLED)
            }
            notificationHelper.cancelNotification(task.id.hashCode())
        }
    }
}
