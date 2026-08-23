package com.betterstreamflix.download

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Download manager — tracks download tasks, progress, and file locations.
 * Uses SharedPreferences for persistence (no Room dependency needed).
 */
object DownloadManager {

    private const val PREFS_NAME = "downloads"
    private const val KEY_DOWNLOADS = "download_list"

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

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Add a download task.
     */
    fun addDownload(context: Context, task: DownloadTask) {
        val downloads = getAllDownloads(context).toMutableList()
        downloads.removeAll { it.id == task.id }
        downloads.add(0, task)
        saveDownloads(context, downloads)
    }

    /**
     * Update a download task.
     */
    fun updateDownload(context: Context, id: String, updater: (DownloadTask) -> DownloadTask) {
        val downloads = getAllDownloads(context).toMutableList()
        val index = downloads.indexOfFirst { it.id == id }
        if (index >= 0) {
            downloads[index] = updater(downloads[index])
            saveDownloads(context, downloads)
        }
    }

    /**
     * Update download progress.
     */
    fun updateProgress(context: Context, id: String, downloadedBytes: Long) {
        updateDownload(context, id) { it.copy(downloadedBytes = downloadedBytes) }
    }

    /**
     * Mark download as completed.
     */
    fun markCompleted(context: Context, id: String) {
        updateDownload(context, id) {
            it.copy(status = DownloadStatus.COMPLETED, completedAt = System.currentTimeMillis())
        }
    }

    /**
     * Mark download as failed.
     */
    fun markFailed(context: Context, id: String, error: String) {
        updateDownload(context, id) { it.copy(status = DownloadStatus.FAILED, errorMessage = error) }
    }

    /**
     * Get all downloads.
     */
    fun getAllDownloads(context: Context): List<DownloadTask> {
        val json = getPrefs(context).getString(KEY_DOWNLOADS, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DownloadTask(
                    id = obj.getString("id"),
                    videoId = obj.getString("videoId"),
                    title = obj.getString("title"),
                    providerName = obj.getString("providerName"),
                    url = obj.getString("url"),
                    filePath = obj.getString("filePath"),
                    fileSize = obj.getLong("fileSize"),
                    downloadedBytes = obj.getLong("downloadedBytes"),
                    status = DownloadStatus.valueOf(obj.getString("status")),
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

    /**
     * Get active downloads (pending or downloading).
     */
    fun getActiveDownloads(context: Context): List<DownloadTask> {
        return getAllDownloads(context).filter {
            it.status == DownloadStatus.PENDING || it.status == DownloadStatus.DOWNLOADING
        }
    }

    /**
     * Get completed downloads.
     */
    fun getCompletedDownloads(context: Context): List<DownloadTask> {
        return getAllDownloads(context).filter { it.status == DownloadStatus.COMPLETED }
    }

    /**
     * Remove a download.
     */
    fun removeDownload(context: Context, id: String) {
        val downloads = getAllDownloads(context).toMutableList()
        downloads.removeAll { it.id == id }
        saveDownloads(context, downloads)
    }

    /**
     * Clear all downloads.
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit { remove(KEY_DOWNLOADS) }
    }

    private fun saveDownloads(context: Context, downloads: List<DownloadTask>) {
        val arr = org.json.JSONArray()
        downloads.forEach { task ->
            arr.put(org.json.JSONObject().apply {
                put("id", task.id)
                put("videoId", task.videoId)
                put("title", task.title)
                put("providerName", task.providerName)
                put("url", task.url)
                put("filePath", task.filePath)
                put("fileSize", task.fileSize)
                put("downloadedBytes", task.downloadedBytes)
                put("status", task.status.name)
                put("createdAt", task.createdAt)
                task.completedAt?.let { put("completedAt", it) }
                task.errorMessage?.let { put("errorMessage", it) }
            })
        }
        getPrefs(context).edit { putString(KEY_DOWNLOADS, arr.toString()) }
    }
}
