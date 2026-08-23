package com.betterstreamflix.download

import android.content.Context
import androidx.core.content.edit

/**
 * Download history manager — tracks completed downloads history
 * for analytics and re-download capability.
 */
object DownloadHistoryManager {

    private const val PREFS_NAME = "download_history"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 100

    data class DownloadHistoryEntry(
        val downloadId: String,
        val contentId: String,
        val title: String,
        val providerName: String,
        val fileSize: Long,
        val completedAt: Long,
        val filePath: String,
    )

    /**
     * Add a completed download to history.
     */
    fun addEntry(context: Context, entry: DownloadHistoryEntry) {
        val entries = getHistory(context).toMutableList()
        entries.add(0, entry)
        if (entries.size > MAX_ENTRIES) {
            entries.subList(MAX_ENTRIES, entries.size).clear()
        }
        saveHistory(context, entries)
    }

    /**
     * Get download history.
     */
    fun getHistory(context: Context): List<DownloadHistoryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                DownloadHistoryEntry(
                    downloadId = obj.getString("downloadId"),
                    contentId = obj.getString("contentId"),
                    title = obj.getString("title"),
                    providerName = obj.getString("providerName"),
                    fileSize = obj.getLong("fileSize"),
                    completedAt = obj.getLong("completedAt"),
                    filePath = obj.getString("filePath"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get download history for a specific content.
     */
    fun getHistoryForContent(context: Context, contentId: String): List<DownloadHistoryEntry> {
        return getHistory(context).filter { it.contentId == contentId }
    }

    /**
     * Remove a history entry.
     */
    fun removeEntry(context: Context, downloadId: String) {
        val entries = getHistory(context).filter { it.downloadId != downloadId }
        saveHistory(context, entries)
    }

    /**
     * Clear all download history.
     */
    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }

    /**
     * Get total downloaded size from history.
     */
    fun getTotalDownloadedSize(context: Context): Long {
        return getHistory(context).sumOf { it.fileSize }
    }

    /**
     * Get download count.
     */
    fun getDownloadCount(context: Context): Int {
        return getHistory(context).size
    }

    private fun saveHistory(context: Context, entries: List<DownloadHistoryEntry>) {
        val array = org.json.JSONArray()
        entries.forEach { e ->
            array.put(org.json.JSONObject().apply {
                put("downloadId", e.downloadId)
                put("contentId", e.contentId)
                put("title", e.title)
                put("providerName", e.providerName)
                put("fileSize", e.fileSize)
                put("completedAt", e.completedAt)
                put("filePath", e.filePath)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENTRIES, array.toString()).apply()
    }
}
