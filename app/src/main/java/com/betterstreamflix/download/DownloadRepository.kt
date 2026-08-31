package com.betterstreamflix.download

import android.content.Context
import com.betterstreamflix.database.AppLevelDatabase
import com.betterstreamflix.database.dao.DownloadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Room-backed download store — single source of truth for offline content.
 */
class DownloadRepository(val context: Context) {

    private val dao = AppLevelDatabase.getInstance(context).downloadDao()
    private val appContext = context.applicationContext

    fun observeAll(): Flow<List<DownloadEntity>> = dao.getAll()

    fun observeTasks(): Flow<List<DownloadManager.DownloadTask>> =
        dao.getAll().map { entities -> entities.map { it.toTask() } }

    suspend fun getAll(): List<DownloadEntity> = dao.getAll().first()

    suspend fun getById(id: String): DownloadEntity? = dao.get(id)

    suspend fun upsert(entity: DownloadEntity) = dao.insert(entity)

    suspend fun upsertTask(task: DownloadManager.DownloadTask) = dao.insert(task.toEntity())

    suspend fun updateProgress(id: String, bytes: Long) = dao.updateProgress(id, bytes)

    suspend fun updateStatus(id: String, status: String) = dao.updateStatus(id, status)

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun migrateFromSharedPrefsIfNeeded() {
        val legacy = LegacyDownloadPrefs.readAll(appContext)
        if (legacy.isEmpty()) return
        legacy.forEach { dao.insert(it.toEntity()) }
        LegacyDownloadPrefs.clear(appContext)
    }

    fun getAllBlocking(): List<DownloadManager.DownloadTask> = runBlocking {
        getAll().map { it.toTask() }
    }

    companion object {
        fun fromTask(task: DownloadManager.DownloadTask): DownloadEntity = task.toEntity()

        fun DownloadEntity.toTask(): DownloadManager.DownloadTask = DownloadManager.DownloadTask(
            id = id,
            videoId = videoId,
            title = title,
            providerName = providerName,
            url = url,
            filePath = filePath,
            fileSize = fileSize,
            downloadedBytes = downloadedBytes,
            status = runCatching { DownloadManager.DownloadStatus.valueOf(status) }
                .getOrDefault(DownloadManager.DownloadStatus.PENDING),
            createdAt = createdAt,
            completedAt = completedAt,
            errorMessage = errorMessage,
        )

        fun DownloadManager.DownloadTask.toEntity(): DownloadEntity = DownloadEntity(
            id = id,
            videoId = videoId,
            title = title,
            providerName = providerName,
            url = url,
            filePath = filePath,
            fileSize = fileSize,
            downloadedBytes = downloadedBytes,
            status = status.name,
            createdAt = createdAt,
            completedAt = completedAt,
            errorMessage = errorMessage,
        )
    }
}

/** One-time migration from legacy SharedPreferences download list. */
private object LegacyDownloadPrefs {
    private const val PREFS_NAME = "downloads"
    private const val KEY_DOWNLOADS = "download_list"

    fun readAll(context: Context): List<DownloadManager.DownloadTask> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DOWNLOADS, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DownloadManager.DownloadTask(
                    id = obj.getString("id"),
                    videoId = obj.getString("videoId"),
                    title = obj.getString("title"),
                    providerName = obj.getString("providerName"),
                    url = obj.getString("url"),
                    filePath = obj.getString("filePath"),
                    fileSize = obj.getLong("fileSize"),
                    downloadedBytes = obj.getLong("downloadedBytes"),
                    status = DownloadManager.DownloadStatus.valueOf(obj.getString("status")),
                    createdAt = obj.getLong("createdAt"),
                    completedAt = obj.optLong("completedAt", 0).takeIf { it > 0 },
                    errorMessage = obj.optString("errorMessage", null),
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptyList()
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DOWNLOADS)
            .apply()
    }
}
