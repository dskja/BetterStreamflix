package com.betterstreamflix.download

import android.content.Context
import com.betterstreamflix.database.AppLevelDatabase
import com.betterstreamflix.database.dao.DownloadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Room-backed download store — single source of truth for offline content.
 */
class DownloadRepository(context: Context) {

    private val dao = AppLevelDatabase.getInstance(context).downloadDao()
    private val appContext = context.applicationContext

    fun observeAll(): Flow<List<DownloadEntity>> = dao.getAll()

    suspend fun getAll(): List<DownloadEntity> = dao.getAll().first()

    suspend fun upsert(entity: DownloadEntity) = dao.insert(entity)

    suspend fun updateProgress(id: String, bytes: Long) = dao.updateProgress(id, bytes)

    suspend fun updateStatus(id: String, status: String) = dao.updateStatus(id, status)

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun migrateFromSharedPrefsIfNeeded() {
        val legacy = DownloadManager.getAllDownloads(appContext)
        if (legacy.isEmpty()) return
        legacy.forEach { task ->
            dao.insert(
                DownloadEntity(
                    id = task.id,
                    videoId = task.videoId,
                    title = task.title,
                    providerName = task.providerName,
                    url = task.url,
                    filePath = task.filePath,
                    fileSize = task.fileSize,
                    downloadedBytes = task.downloadedBytes,
                    status = task.status.name,
                    createdAt = task.createdAt,
                    completedAt = task.completedAt,
                    errorMessage = task.errorMessage,
                ),
            )
        }
    }

    companion object {
        fun fromTask(task: DownloadManager.DownloadTask): DownloadEntity = DownloadEntity(
            id = task.id,
            videoId = task.videoId,
            title = task.title,
            providerName = task.providerName,
            url = task.url,
            filePath = task.filePath,
            fileSize = task.fileSize,
            downloadedBytes = task.downloadedBytes,
            status = task.status.name,
            createdAt = task.createdAt,
            completedAt = task.completedAt,
            errorMessage = task.errorMessage,
        )
    }
}
