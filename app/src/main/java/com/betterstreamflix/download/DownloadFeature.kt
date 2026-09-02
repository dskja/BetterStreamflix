package com.betterstreamflix.download

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Public entry point for the offline download feature.
 */
object DownloadFeature {

    private const val TAG = "DownloadFeature"
    private const val UNIQUE_QUEUE_WORK = "bsf_download_queue"
    const val NOTIFICATION_PERMISSION_REQUEST = 4217

    private val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observe(context: Context): Flow<List<DownloadManager.DownloadTask>> =
        DownloadRepository(context).observeTasks()

    fun enqueue(
        context: Context,
        videoId: String,
        title: String,
        url: String,
        providerName: String,
        filePath: String = "",
        artworkUrl: String? = null,
        scheduleWorker: Boolean = true,
    ): Boolean {
        val appContext = context.applicationContext
        if (StreamTypeDetector.isDrmProtected(url)) {
            Log.w(TAG, "DRM stream not downloadable: $title")
            return false
        }
        val existing = findExisting(appContext, videoId, providerName)
        if (existing != null) {
            Log.i(TAG, "Already queued: $providerName/$videoId (${existing.status})")
            return false
        }
        val decision = DownloadScheduler.shouldStartDownloads(appContext)
        if (decision is DownloadScheduler.ScheduleDecision.Wait) {
            Log.i(TAG, "Download blocked: $decision")
            return false
        }
        if (decision is DownloadScheduler.ScheduleDecision.Defer) {
            Log.i(TAG, "Download deferred but queued: $decision")
        }
        return runCatching {
            ensureNotificationPermission(context)
            val taskId = UUID.randomUUID().toString()
            val task = DownloadManager.DownloadTask(
                id = taskId,
                videoId = videoId,
                title = title,
                providerName = providerName,
                url = url,
                filePath = filePath.ifBlank {
                    DownloadFileManager.getDownloadFile(appContext, taskId).absolutePath
                },
                artworkUrl = artworkUrl?.takeIf { it.isNotBlank() },
            )
            DownloadManager.addDownload(appContext, task)
            ensureArtworkCached(appContext, taskId, artworkUrl)
            if (scheduleWorker) scheduleQueueWorker(appContext)
            true
        }.onFailure { e ->
            Log.e(TAG, "enqueue failed", e)
        }.getOrDefault(false)
    }

    /**
     * Persist remote poster into durable storage and rewrite [DownloadTask.artworkUrl]
     * to a local absolute path so the library UI works offline.
     */
    fun ensureArtworkCached(context: Context, downloadId: String, artworkUrl: String?) {
        val source = artworkUrl?.takeIf { it.isNotBlank() } ?: return
        if (!DownloadArtworkStore.needsRemoteFetch(source)) {
            // Already local (or missing remote) — still normalize path if file exists under id.
            val existing = DownloadArtworkStore.artworkFile(context, downloadId)
            if (existing.exists() && existing.length() > 0L && source != existing.absolutePath) {
                DownloadManager.updateDownload(context, downloadId) {
                    it.copy(artworkUrl = existing.absolutePath)
                }
            }
            return
        }
        cacheArtworkAsync(context.applicationContext, downloadId, source)
    }

    /** Backfill local artwork for tasks that still point at remote URLs. */
    fun ensureArtworkCached(context: Context, tasks: List<DownloadManager.DownloadTask>) {
        tasks.forEach { ensureArtworkCached(context, it.id, it.artworkUrl) }
    }

    private fun cacheArtworkAsync(context: Context, downloadId: String, artworkUrl: String) {
        artworkScope.launch {
            val local = DownloadArtworkStore.cache(context, downloadId, artworkUrl) ?: return@launch
            DownloadManager.updateDownload(context, downloadId) { it.copy(artworkUrl = local) }
        }
    }

    fun list(context: Context): List<DownloadManager.DownloadTask> =
        DownloadRepository(context).getAllBlocking()

    fun findExisting(
        context: Context,
        videoId: String,
        providerName: String = UserPreferencesProviderName.current(),
    ): DownloadManager.DownloadTask? =
        DownloadManager.getAllDownloads(context)
            .firstOrNull {
                it.videoId == videoId &&
                    it.providerName.equals(providerName, ignoreCase = true) &&
                    it.status != DownloadManager.DownloadStatus.CANCELLED
            }

    fun retry(context: Context, id: String) {
        DownloadManager.resumeDownload(context, id)
        scheduleQueueWorker(context)
    }

    fun pauseAllActive(context: Context) {
        DownloadManager.getAllDownloads(context)
            .filter {
                it.status == DownloadManager.DownloadStatus.DOWNLOADING ||
                    it.status == DownloadManager.DownloadStatus.PENDING
            }
            .forEach { DownloadManager.pauseDownload(context, it.id) }
    }

    fun resumeAllPaused(context: Context) {
        val targets = DownloadManager.getAllDownloads(context)
            .filter {
                it.status == DownloadManager.DownloadStatus.PAUSED ||
                    it.status == DownloadManager.DownloadStatus.FAILED
            }
        if (targets.isEmpty()) return
        targets.forEach { DownloadManager.resumeDownload(context, it.id) }
        scheduleQueueWorker(context)
    }

    fun retryAllFailed(context: Context) {
        val targets = DownloadManager.getAllDownloads(context)
            .filter { it.status == DownloadManager.DownloadStatus.FAILED }
        if (targets.isEmpty()) return
        targets.forEach { DownloadManager.resumeDownload(context, it.id) }
        scheduleQueueWorker(context)
    }

    fun clearFailed(context: Context) {
        DownloadManager.getAllDownloads(context)
            .filter { it.status == DownloadManager.DownloadStatus.FAILED }
            .forEach { DownloadManager.cancelDownload(context, it.id) }
    }

    fun clearCompleted(context: Context) {
        DownloadManager.getAllDownloads(context)
            .filter { it.status == DownloadManager.DownloadStatus.COMPLETED }
            .forEach { DownloadManager.cancelDownload(context, it.id) }
    }

    /**
     * Coalesce queue processing onto one unique WorkManager chain so concurrent
     * workers cannot truncate the same progressive file.
     */
    fun scheduleQueueWorker(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_QUEUE_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<DownloadWorker>().build(),
        )
    }

    fun ensureNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        val activity = (context as? Activity) ?: return
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_REQUEST,
        )
    }
}

/** Thin indirection so unit tests can stub provider name without Android prefs. */
internal object UserPreferencesProviderName {
    fun current(): String =
        com.betterstreamflix.utils.UserPreferences.currentProvider?.name ?: "unknown"
}
