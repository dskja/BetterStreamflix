package com.betterstreamflix.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.betterstreamflix.utils.Logger
import java.io.File

/**
 * Download queue processor — manages the download queue and processes tasks sequentially.
 */
@UnstableApi
class DownloadQueueProcessor(private val context: Context) {

    private val executor = DownloadExecutor(context)
    private val hlsEngine = HlsDownloadEngine(context)
    private val dashEngine = DashDownloadEngine(context)
    private val repository = DownloadRepository(context)
    private var isProcessing = false

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

    private suspend fun processTask(task: DownloadManager.DownloadTask) {
        val notificationId = task.id.hashCode()
        DownloadManager.updateDownload(context, task.id) {
            it.copy(status = DownloadManager.DownloadStatus.DOWNLOADING)
        }
        DownloadNotificationBuilder.ensureChannel(context)
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        nm?.notify(
            notificationId,
            DownloadNotificationBuilder.buildProgressNotification(context, task.title, 0),
        )

        val outputFile = File(task.filePath)
        val outputDir = outputFile.parentFile ?: DownloadStorageManager.getDownloadDir(context)
        val streamType = StreamTypeDetector.detect(task.url)

        when (streamType) {
            StreamType.HLS -> {
                hlsEngine.download(task.url, task.id, task.title) { _, _, _ -> }
                    .onSuccess { mediaPath ->
                        DownloadManager.updateDownload(context, task.id) {
                            it.copy(filePath = mediaPath)
                        }
                    }
                StreamflixDownloadService.start(context)
            }
            StreamType.DASH -> {
                dashEngine.download(task.url, task.id, task.title) { _, _, _ -> }
                    .onSuccess { mediaPath ->
                        DownloadManager.updateDownload(context, task.id) {
                            it.copy(filePath = mediaPath)
                        }
                    }
                StreamflixDownloadService.start(context)
            }
            StreamType.HTTP -> {
                val result = executor.download(task.url, outputFile) { percent, downloaded, _ ->
                    DownloadManager.updateProgress(context, task.id, downloaded)
                    nm?.notify(
                        notificationId,
                        DownloadNotificationBuilder.buildProgressNotification(context, task.title, percent),
                    )
                }
                result.onSuccess {
                    DownloadManager.markCompleted(context, task.id)
                    repository.updateStatus(task.id, DownloadManager.DownloadStatus.COMPLETED.name)
                    nm?.notify(
                        notificationId,
                        DownloadNotificationBuilder.buildCompleteNotification(context, task.title),
                    )
                }.onFailure { error ->
                    DownloadManager.markFailed(context, task.id, error.message ?: "Unknown error")
                    repository.updateStatus(task.id, DownloadManager.DownloadStatus.FAILED.name)
                    nm?.notify(
                        notificationId,
                        DownloadNotificationBuilder.buildFailedNotification(
                            context,
                            task.title,
                            error.message ?: "Unknown error",
                        ),
                    )
                    Logger.e("DownloadQueue", "Download failed: ${task.title}", error)
                }
            }
        }
    }

    fun cancelAll() {
        DownloadManager.getActiveDownloads(context).forEach { task ->
            DownloadManager.cancelDownload(context, task.id)
            context.getSystemService(android.app.NotificationManager::class.java)
                ?.cancel(task.id.hashCode())
        }
    }
}
