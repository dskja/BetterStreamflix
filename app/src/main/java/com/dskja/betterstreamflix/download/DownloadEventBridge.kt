package com.dskja.betterstreamflix.download

import android.content.Context
import android.util.Log
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object DownloadEventBridge : DownloadManager.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    @Volatile
    private var appContext: Context? = null

    private val lastBytes = mutableMapOf<String, Pair<Long, Long>>()

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    /** Pull live Media3 progress into Room so the Downloads UI updates in real time. */
    suspend fun syncAllActive(context: Context) {
        val dm = StreamflixDownloadManager.get(context)
        val downloads = dm.currentDownloads.toList()
        if (downloads.isEmpty()) return
        syncMutex.withLock {
            for (download in downloads) {
                applyDownload(context.applicationContext, dm, download, finalException = null)
            }
        }
    }

    override fun onDownloadChanged(
        downloadManager: DownloadManager,
        download: Download,
        finalException: Exception?,
    ) {
        val context = appContext ?: return
        scope.launch {
            syncMutex.withLock {
                applyDownload(context, downloadManager, download, finalException)
            }
        }
    }

    override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
        val context = appContext ?: return
        scope.launch {
            lastBytes.remove(download.request.id)
            val entity = DownloadRepository.get(context).getByMedia3Id(download.request.id)
            if (entity != null) DownloadNotifier.cancelFailed(context, entity.id)
            refreshAggregateNotification(context, downloadManager)
        }
    }

    override fun onIdle(downloadManager: DownloadManager) {
        val context = appContext ?: return
        DownloadNotifier.cancelActive(context)
    }

    private suspend fun applyDownload(
        context: Context,
        downloadManager: DownloadManager,
        download: Download,
        finalException: Exception?,
    ) {
        val repo = DownloadRepository.get(context)
        val entity = repo.getByMedia3Id(download.request.id) ?: return
        val now = System.currentTimeMillis()
        val prev = lastBytes[download.request.id]
        val speed = if (prev != null && now > prev.second) {
            val deltaBytes = (download.bytesDownloaded - prev.first).coerceAtLeast(0L)
            val deltaMs = (now - prev.second).coerceAtLeast(1L)
            if (deltaBytes > 0L) (deltaBytes * 1000L) / deltaMs else entity.speedBytesPerSec
        } else {
            entity.speedBytesPerSec
        }
        lastBytes[download.request.id] = download.bytesDownloaded to now

        val pct = when {
            download.percentDownloaded >= 0f ->
                download.percentDownloaded.toInt().coerceIn(0, 100)
            download.contentLength > 0L ->
                ((download.bytesDownloaded * 100L) / download.contentLength).toInt().coerceIn(0, 100)
            else -> entity.progressPct
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
        // Terminal states no longer need speed sampling — otherwise lastBytes grows forever.
        if (state != DownloadItemState.DOWNLOADING && state != DownloadItemState.QUEUED) {
            lastBytes.remove(download.request.id)
        }

        val localUri = if (state == DownloadItemState.COMPLETED) {
            download.request.uri.toString()
        } else {
            entity.localUri
        }

        val errorMessage = finalException?.message
            ?: download.failureReason.takeIf { it != Download.FAILURE_REASON_NONE }?.toString().orEmpty()

        val contentLength = download.contentLength.coerceAtLeast(0L)
        val nextSpeed = if (state == DownloadItemState.DOWNLOADING) speed else 0L
        val nextEta = if (state == DownloadItemState.DOWNLOADING) eta else -1L
        val unchanged = entity.state == state.name &&
            entity.bytesDownloaded == download.bytesDownloaded &&
            entity.progressPct == pct &&
            entity.contentLength == contentLength &&
            entity.speedBytesPerSec == nextSpeed
        if (unchanged) {
            refreshAggregateNotification(context, downloadManager)
            return
        }

        repo.updateProgress(
            id = entity.id,
            state = state,
            bytesDownloaded = download.bytesDownloaded,
            contentLength = contentLength,
            progressPct = pct,
            speedBytesPerSec = nextSpeed,
            etaSeconds = nextEta,
            localUri = localUri,
            errorCode = if (state == DownloadItemState.FAILED) {
                classifyFailure(finalException).name
            } else {
                ""
            },
            errorMessage = if (state == DownloadItemState.FAILED) errorMessage else "",
        )

        if (state == DownloadItemState.COMPLETED && entity.state != DownloadItemState.COMPLETED.name) {
            DownloadNotifier.notifyCompleted(context, entity.title)
            lastBytes.remove(download.request.id)
            scope.launch {
                runCatching {
                    SubtitleFetch.fetchFor(context, repo.getById(entity.id) ?: entity)
                }.onFailure { Log.w("DownloadEventBridge", "subtitle fetch failed", it) }
            }
        }
        if (state == DownloadItemState.FAILED && entity.state != DownloadItemState.FAILED.name) {
            DownloadNotifier.notifyFailed(context, entity.id, entity.title)
        } else if (state != DownloadItemState.FAILED && entity.state == DownloadItemState.FAILED.name) {
            DownloadNotifier.cancelFailed(context, entity.id)
        }

        refreshAggregateNotification(context, downloadManager)
        entity.seasonPackId?.let { repo.refreshSeasonPack(it) }
    }

    private fun classifyFailure(e: Exception?): DownloadErrorCode {
        val msg = e?.message?.lowercase().orEmpty()
        val chain = generateSequence(e as Throwable?) { it.cause }
            .mapNotNull { it.message?.lowercase() }
            .joinToString(" ")
        val hay = "$msg $chain"
        return when {
            "no space" in hay || "enospc" in hay || "space left" in hay ->
                DownloadErrorCode.NOSPACE
            "403" in hay || "410" in hay || "expired" in hay || "forbidden" in hay ->
                DownloadErrorCode.EXPIRED
            "cleartext" in hay || "ssl" in hay || "certificate" in hay ->
                DownloadErrorCode.UNSUPPORTED
            else -> DownloadErrorCode.NETWORK
        }
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
        val pct = when {
            downloading.percentDownloaded >= 0f -> downloading.percentDownloaded.toInt().coerceAtLeast(0)
            downloading.contentLength > 0L ->
                ((downloading.bytesDownloaded * 100L) / downloading.contentLength).toInt().coerceAtLeast(0)
            else -> 0
        }
        val title = if (active.size == 1) {
            context.getString(com.dskja.betterstreamflix.R.string.download_notification_active_one)
        } else {
            context.getString(
                com.dskja.betterstreamflix.R.string.download_notification_active_many,
                active.size,
            )
        }
        val text = if (downloading.percentDownloaded >= 0f || downloading.contentLength > 0L) {
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
            indeterminate = downloading.percentDownloaded < 0f && downloading.contentLength <= 0L,
            contentText = text,
        )
    }
}
