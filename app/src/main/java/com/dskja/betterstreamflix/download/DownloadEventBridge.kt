package com.dskja.betterstreamflix.download

import android.content.Context
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object DownloadEventBridge : DownloadManager.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appContext: Context? = null

    private val lastBytes = mutableMapOf<String, Pair<Long, Long>>()

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    override fun onDownloadChanged(
        downloadManager: DownloadManager,
        download: Download,
        finalException: Exception?,
    ) {
        val context = appContext ?: return
        scope.launch {
            val repo = DownloadRepository.get(context)
            val entity = repo.getByMedia3Id(download.request.id) ?: return@launch
            val now = System.currentTimeMillis()
            val prev = lastBytes[download.request.id]
            val speed = if (prev != null && now > prev.second) {
                val deltaBytes = (download.bytesDownloaded - prev.first).coerceAtLeast(0L)
                val deltaMs = (now - prev.second).coerceAtLeast(1L)
                (deltaBytes * 1000L) / deltaMs
            } else {
                0L
            }
            lastBytes[download.request.id] = download.bytesDownloaded to now

            val pct = if (download.percentDownloaded >= 0f) {
                download.percentDownloaded.toInt().coerceIn(0, 100)
            } else {
                entity.progressPct
            }
            val remaining = (download.contentLength - download.bytesDownloaded).coerceAtLeast(0L)
            val eta = if (speed > 0L && remaining > 0L) remaining / speed else -1L

            val state = when (download.state) {
                Download.STATE_QUEUED -> DownloadItemState.QUEUED
                Download.STATE_STOPPED -> DownloadItemState.PAUSED
                Download.STATE_DOWNLOADING -> DownloadItemState.DOWNLOADING
                Download.STATE_COMPLETED -> DownloadItemState.COMPLETED
                Download.STATE_FAILED -> DownloadItemState.FAILED
                Download.STATE_REMOVING, Download.STATE_RESTARTING -> DownloadItemState.REMOVING
                else -> DownloadItemState.QUEUED
            }

            val localUri = if (state == DownloadItemState.COMPLETED) {
                download.request.uri.toString()
            } else {
                entity.localUri
            }

            val errorMessage = finalException?.message
                ?: download.failureReason.takeIf { it != Download.FAILURE_REASON_NONE }?.toString().orEmpty()

            repo.updateProgress(
                id = entity.id,
                state = state,
                bytesDownloaded = download.bytesDownloaded,
                contentLength = download.contentLength.coerceAtLeast(0L),
                progressPct = pct,
                speedBytesPerSec = speed,
                etaSeconds = eta,
                localUri = localUri,
                errorCode = if (state == DownloadItemState.FAILED) DownloadErrorCode.NETWORK.name else "",
                errorMessage = if (state == DownloadItemState.FAILED) errorMessage else "",
            )

            if (state == DownloadItemState.COMPLETED) {
                DownloadNotifier.notifyCompleted(context, entity.title)
            }

            refreshAggregateNotification(context, downloadManager)
            entity.seasonPackId?.let { repo.refreshSeasonPack(it) }
        }
    }

    override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
        val context = appContext ?: return
        scope.launch {
            lastBytes.remove(download.request.id)
            refreshAggregateNotification(context, downloadManager)
        }
    }

    override fun onIdle(downloadManager: DownloadManager) {
        val context = appContext ?: return
        DownloadNotifier.cancelActive(context)
    }

    private fun refreshAggregateNotification(context: Context, downloadManager: DownloadManager) {
        val active = downloadManager.currentDownloads.filter {
            it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED
        }
        if (active.isEmpty()) {
            DownloadNotifier.cancelActive(context)
            return
        }
        val downloading = active.firstOrNull { it.state == Download.STATE_DOWNLOADING } ?: active.first()
        val pct = downloading.percentDownloaded.toInt().coerceAtLeast(0)
        val title = if (active.size == 1) {
            context.getString(com.dskja.betterstreamflix.R.string.download_notification_active_one)
        } else {
            context.getString(
                com.dskja.betterstreamflix.R.string.download_notification_active_many,
                active.size,
            )
        }
        val text = if (downloading.percentDownloaded >= 0f) {
            context.getString(
                com.dskja.betterstreamflix.R.string.download_notification_progress,
                pct,
            )
        } else {
            context.getString(com.dskja.betterstreamflix.R.string.download_notification_indeterminate)
        }
        DownloadNotifier.notifyActive(
            context = context,
            title = title,
            progressPct = pct,
            indeterminate = downloading.percentDownloaded < 0f,
            contentText = text,
        )
    }
}
