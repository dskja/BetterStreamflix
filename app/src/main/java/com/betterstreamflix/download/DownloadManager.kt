package com.betterstreamflix.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.betterstreamflix.download.DownloadRepository.Companion.toTask
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Download manager — tracks download tasks via Room (single source of truth).
 */
object DownloadManager {

    enum class DownloadStatus { PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }

    data class DownloadTask(
        val id: String,
        val videoId: String,
        val title: String,
        val providerName: String,
        val url: String,
        val filePath: String,
        val fileSize: Long = 0,
        val downloadedBytes: Long = 0,
        val status: DownloadStatus = DownloadStatus.PENDING,
        val createdAt: Long = System.currentTimeMillis(),
        val completedAt: Long? = null,
        val errorMessage: String? = null,
        val artworkUrl: String? = null,
    ) {
        val progressFraction: Float
            get() = when {
                fileSize > 0L -> (downloadedBytes.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f)
                status == DownloadStatus.COMPLETED -> 1f
                else -> 0f
            }

        val isActive: Boolean
            get() = status == DownloadStatus.PENDING || status == DownloadStatus.DOWNLOADING

        val canOpen: Boolean
            get() = status == DownloadStatus.COMPLETED
    }

    /** Cooperative cancel/pause flags for progressive HTTP downloads. */
    private val httpControlFlags = ConcurrentHashMap<String, AtomicBoolean>()

    fun httpShouldAbort(id: String): Boolean =
        httpControlFlags[id]?.get() == true

    fun armHttpControl(id: String) {
        httpControlFlags[id] = AtomicBoolean(false)
    }

    fun signalHttpAbort(id: String) {
        httpControlFlags[id]?.set(true)
    }

    fun clearHttpControl(id: String) {
        httpControlFlags.remove(id)
    }

    private fun repository(context: Context) = DownloadRepository(context.applicationContext)

    private fun persist(context: Context, task: DownloadTask) {
        runBlocking { repository(context).upsertTask(task) }
    }

    fun addDownload(context: Context, task: DownloadTask) {
        persist(context, task)
    }

    fun updateDownload(context: Context, id: String, updater: (DownloadTask) -> DownloadTask) {
        runBlocking {
            val existing = repository(context).getById(id)?.toTask() ?: return@runBlocking
            repository(context).upsertTask(updater(existing))
        }
    }

    fun updateProgress(context: Context, id: String, downloadedBytes: Long, fileSize: Long = -1L) {
        runBlocking {
            if (fileSize >= 0L) {
                repository(context).updateProgress(id, downloadedBytes, fileSize)
            } else {
                repository(context).updateProgress(id, downloadedBytes)
            }
        }
    }

    fun markCompleted(context: Context, id: String) {
        updateDownload(context, id) {
            it.copy(
                status = DownloadStatus.COMPLETED,
                completedAt = System.currentTimeMillis(),
                errorMessage = null,
            )
        }
        clearHttpControl(id)
    }

    fun markFailed(context: Context, id: String, error: String) {
        updateDownload(context, id) {
            it.copy(status = DownloadStatus.FAILED, errorMessage = error)
        }
        clearHttpControl(id)
    }

    fun getAllDownloads(context: Context): List<DownloadTask> =
        repository(context).getAllBlocking()

    fun getActiveDownloads(context: Context): List<DownloadTask> =
        getAllDownloads(context).filter { it.isActive }

    fun getCompletedDownloads(context: Context): List<DownloadTask> =
        getAllDownloads(context).filter { it.status == DownloadStatus.COMPLETED }

    fun removeDownload(context: Context, id: String) {
        clearHttpControl(id)
        runBlocking { repository(context).delete(id) }
    }

    fun clearAll(context: Context) {
        runBlocking {
            repository(context).getAll().forEach { entity ->
                cancelDownload(context, entity.id)
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun pauseDownload(context: Context, id: String) {
        signalHttpAbort(id)
        updateDownload(context, id) { it.copy(status = DownloadStatus.PAUSED) }
        runCatching {
            Media3OfflineDownloads.requireManager(context).setStopReason(id, 1)
        }
    }

    @OptIn(UnstableApi::class)
    fun resumeDownload(context: Context, id: String) {
        clearHttpControl(id)
        updateDownload(context, id) {
            it.copy(status = DownloadStatus.PENDING, errorMessage = null)
        }
        runCatching {
            Media3OfflineDownloads.requireManager(context)
                .setStopReason(id, Download.STOP_REASON_NONE)
            StreamflixDownloadService.start(context)
        }
    }

    @OptIn(UnstableApi::class)
    fun cancelDownload(context: Context, id: String) {
        signalHttpAbort(id)
        val task = runBlocking { repository(context).getById(id)?.toTask() }
        runCatching {
            Media3OfflineDownloads.requireManager(context).removeDownload(id)
        }
        task?.let { deleteTaskFiles(it) }
        DownloadArtworkStore.delete(context, id)
        removeDownload(context, id)
        context.getSystemService(android.app.NotificationManager::class.java)
            ?.cancel(id.hashCode())
    }

    private fun deleteTaskFiles(task: DownloadTask) {
        if (OfflineMediaPaths.parseDownloadId(task.filePath) != null) {
            return
        }
        runCatching { File(task.filePath).delete() }
        runCatching {
            val parent = File(task.filePath).parentFile ?: return@runCatching
            File(parent, "${task.id}.m3u8").delete()
            File(parent, "${task.id}.mpd").delete()
        }
        // Also remove local artwork if artworkUrl points into our download tree.
        task.artworkUrl?.takeIf { it.startsWith("/") }?.let { path ->
            runCatching { File(path).delete() }
        }
    }
}
