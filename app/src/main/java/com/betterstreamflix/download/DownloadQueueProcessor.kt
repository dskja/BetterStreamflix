package com.betterstreamflix.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.betterstreamflix.download.DownloadRepository.Companion.toTask
import com.betterstreamflix.utils.Logger
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Download queue processor — processes pending/active tasks.
 */
@UnstableApi
class DownloadQueueProcessor(private val context: Context) {

    private val executor = DownloadExecutor(context)
    private val hlsEngine = HlsDownloadEngine(context)
    private val dashEngine = DashDownloadEngine(context)
    private val repository = DownloadRepository(context)

    suspend fun processQueue() {
        if (!processing.compareAndSet(false, true)) return
        try {
            val activeDownloads = DownloadManager.getActiveDownloads(context)
            for (task in activeDownloads) {
                val latest = repository.getById(task.id)?.toTask() ?: continue
                if (latest.status == DownloadManager.DownloadStatus.PAUSED ||
                    latest.status == DownloadManager.DownloadStatus.CANCELLED ||
                    latest.status == DownloadManager.DownloadStatus.COMPLETED
                ) {
                    continue
                }
                processTask(latest)
            }
        } finally {
            processing.set(false)
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
        val streamType = StreamTypeDetector.detect(task.url)

        when (streamType) {
            StreamType.HLS -> {
                hlsEngine.download(task.url, task.id, task.title) { _, _, _ -> }
                    .onSuccess { mediaPath ->
                        DownloadManager.updateDownload(context, task.id) {
                            it.copy(filePath = mediaPath, status = DownloadManager.DownloadStatus.DOWNLOADING)
                        }
                        StreamflixDownloadService.start(context)
                    }
                    .onFailure { error ->
                        DownloadManager.markFailed(context, task.id, error.message ?: "HLS enqueue failed")
                        nm?.notify(
                            notificationId,
                            DownloadNotificationBuilder.buildFailedNotification(
                                context,
                                task.title,
                                error.message ?: "HLS enqueue failed",
                            ),
                        )
                    }
            }
            StreamType.DASH -> {
                dashEngine.download(task.url, task.id, task.title) { _, _, _ -> }
                    .onSuccess { mediaPath ->
                        DownloadManager.updateDownload(context, task.id) {
                            it.copy(filePath = mediaPath, status = DownloadManager.DownloadStatus.DOWNLOADING)
                        }
                        StreamflixDownloadService.start(context)
                    }
                    .onFailure { error ->
                        DownloadManager.markFailed(context, task.id, error.message ?: "DASH enqueue failed")
                        nm?.notify(
                            notificationId,
                            DownloadNotificationBuilder.buildFailedNotification(
                                context,
                                task.title,
                                error.message ?: "DASH enqueue failed",
                            ),
                        )
                    }
            }
            StreamType.HTTP -> {
                DownloadManager.armHttpControl(task.id)
                val result = executor.download(
                    url = task.url,
                    outputFile = outputFile,
                    shouldAbort = { DownloadManager.httpShouldAbort(task.id) },
                    deleteOnAbort = false,
                ) { progress ->
                    DownloadManager.updateProgress(
                        context,
                        task.id,
                        progress.downloadedBytes,
                        progress.totalBytes.coerceAtLeast(0L),
                    )
                    nm?.notify(
                        notificationId,
                        DownloadNotificationBuilder.buildProgressNotification(
                            context,
                            task.title,
                            progress.percent,
                        ),
                    )
                }
                result.onSuccess {
                    DownloadManager.markCompleted(context, task.id)
                    nm?.notify(
                        notificationId,
                        DownloadNotificationBuilder.buildCompleteNotification(context, task.title),
                    )
                }.onFailure { error ->
                    if (error is DownloadExecutor.AbortedException) {
                        // Pause/cancel already updated Room status.
                        nm?.cancel(notificationId)
                    } else {
                        DownloadManager.markFailed(context, task.id, error.message ?: "Unknown error")
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
                DownloadManager.clearHttpControl(task.id)
            }
        }
    }

    fun cancelAll() {
        DownloadManager.getActiveDownloads(context).forEach { task ->
            DownloadManager.cancelDownload(context, task.id)
        }
    }

    companion object {
        /** Process-wide gate so concurrent workers cannot truncate the same file. */
        private val processing = AtomicBoolean(false)
    }
}
