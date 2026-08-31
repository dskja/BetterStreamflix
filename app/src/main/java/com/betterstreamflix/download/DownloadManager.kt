package com.betterstreamflix.download

import android.content.Context
import androidx.media3.exoplayer.offline.Download
import com.betterstreamflix.download.DownloadRepository.Companion.toTask
import kotlinx.coroutines.runBlocking

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
    )

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

    fun updateProgress(context: Context, id: String, downloadedBytes: Long) {
        runBlocking { repository(context).updateProgress(id, downloadedBytes) }
    }

    fun markCompleted(context: Context, id: String) {
        updateDownload(context, id) {
            it.copy(status = DownloadStatus.COMPLETED, completedAt = System.currentTimeMillis())
        }
    }

    fun markFailed(context: Context, id: String, error: String) {
        updateDownload(context, id) {
            it.copy(status = DownloadStatus.FAILED, errorMessage = error)
        }
    }

    fun getAllDownloads(context: Context): List<DownloadTask> =
        repository(context).getAllBlocking()

    fun getActiveDownloads(context: Context): List<DownloadTask> =
        getAllDownloads(context).filter {
            it.status == DownloadStatus.PENDING || it.status == DownloadStatus.DOWNLOADING
        }

    fun getCompletedDownloads(context: Context): List<DownloadTask> =
        getAllDownloads(context).filter { it.status == DownloadStatus.COMPLETED }

    fun removeDownload(context: Context, id: String) {
        runBlocking { repository(context).delete(id) }
    }

    fun clearAll(context: Context) {
        runBlocking {
            repository(context).getAll().forEach { entity ->
                repository(context).delete(entity.id)
            }
        }
    }

    fun pauseDownload(context: Context, id: String) {
        updateDownload(context, id) { it.copy(status = DownloadStatus.PAUSED) }
        runCatching {
            Media3OfflineDownloads.requireManager(context).setStopReason(id, 1)
        }
    }

    fun resumeDownload(context: Context, id: String) {
        updateDownload(context, id) { it.copy(status = DownloadStatus.PENDING) }
        runCatching {
            Media3OfflineDownloads.requireManager(context).setStopReason(id, Download.STOP_REASON_NONE)
            StreamflixDownloadService.start(context)
        }
    }

    fun cancelDownload(context: Context, id: String) {
        runCatching {
            Media3OfflineDownloads.requireManager(context).removeDownload(id)
        }
        removeDownload(context, id)
    }
}
