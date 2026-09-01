package com.betterstreamflix.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager as Media3DownloadManager
import com.betterstreamflix.StreamFlixApp
import kotlinx.coroutines.launch

/**
 * Syncs Media3 offline download state into Room (single source of truth).
 */
@UnstableApi
object Media3DownloadSync {

    @Volatile
    private var attached = false

    fun attach(context: Context) = ensureAttached(context)

    fun ensureAttached(context: Context) {
        synchronized(this) {
            if (attached) return
            val appContext = context.applicationContext
            val manager = Media3OfflineDownloads.downloadManagerOrNull() ?: return
            val repository = DownloadRepository(appContext)
            manager.addListener(
                object : Media3DownloadManager.Listener {
                    override fun onDownloadChanged(
                        downloadManager: Media3DownloadManager,
                        download: Download,
                        finalException: Exception?,
                    ) {
                        StreamFlixApp.instance.applicationScope.launch {
                            syncDownload(repository, download, finalException)
                        }
                    }

                    override fun onDownloadRemoved(downloadManager: Media3DownloadManager, download: Download) {
                        StreamFlixApp.instance.applicationScope.launch {
                            repository.delete(download.request.id)
                        }
                    }

                    override fun onIdle(downloadManager: Media3DownloadManager) = Unit
                },
            )
            attached = true
        }
    }

    private suspend fun syncDownload(
        repository: DownloadRepository,
        download: Download,
        finalException: Exception?,
    ) {
        val existing = repository.getById(download.request.id) ?: return
        val status = when (download.state) {
            Download.STATE_COMPLETED -> DownloadManager.DownloadStatus.COMPLETED
            Download.STATE_DOWNLOADING -> DownloadManager.DownloadStatus.DOWNLOADING
            Download.STATE_QUEUED, Download.STATE_RESTARTING -> DownloadManager.DownloadStatus.PENDING
            Download.STATE_STOPPED -> DownloadManager.DownloadStatus.PAUSED
            Download.STATE_FAILED -> DownloadManager.DownloadStatus.FAILED
            else -> DownloadManager.DownloadStatus.PENDING
        }
        val percent = download.percentDownloaded
        val downloadedBytes = if (percent > 0 && existing.fileSize > 0) {
            (existing.fileSize * (percent / 100f)).toLong()
        } else {
            existing.downloadedBytes
        }
        repository.upsert(
            existing.copy(
                status = status.name,
                downloadedBytes = downloadedBytes,
                completedAt = if (status == DownloadManager.DownloadStatus.COMPLETED) {
                    System.currentTimeMillis()
                } else {
                    existing.completedAt
                },
                errorMessage = finalException?.message ?: existing.errorMessage,
            ),
        )
        if (status == DownloadManager.DownloadStatus.COMPLETED) {
            DownloadNotificationBuilder.ensureChannel(repository.context)
            val notificationId = download.request.id.hashCode()
            val nm = repository.context.getSystemService(android.app.NotificationManager::class.java)
            nm?.notify(
                notificationId,
                DownloadNotificationBuilder.buildCompleteNotification(repository.context, existing.title),
            )
        }
    }
}
