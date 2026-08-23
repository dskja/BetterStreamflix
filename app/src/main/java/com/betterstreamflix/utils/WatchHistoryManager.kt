package com.betterstreamflix.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Watch history manager — tracks recently watched content
 * for "continue watching" and history features.
 */
object WatchHistoryManager {

    private const val PREFS_NAME = "watch_history"
    private const val KEY_HISTORY = "history_items"
    private const val MAX_HISTORY_ITEMS = 100

    data class HistoryItem(
        val videoId: String,
        val title: String,
        val providerName: String,
        val thumbnailUrl: String?,
        val watchedAt: Long,
        val progressPercent: Float,
    )

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Add or update a history item.
     */
    fun add(context: Context, item: HistoryItem) {
        val items = getAll(context).toMutableList()
        items.removeAll { it.videoId == item.videoId && it.providerName == item.providerName }
        items.add(0, item)
        if (items.size > MAX_HISTORY_ITEMS) {
            items.subList(MAX_HISTORY_ITEMS, items.size).clear()
        }
        saveItems(context, items)
    }

    /**
     * Get all history items, newest first.
     */
    fun getAll(context: Context): List<HistoryItem> {
        val json = getPrefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                HistoryItem(
                    videoId = obj.getString("videoId"),
                    title = obj.getString("title"),
                    providerName = obj.getString("providerName"),
                    thumbnailUrl = obj.optString("thumbnailUrl", null),
                    watchedAt = obj.getLong("watchedAt"),
                    progressPercent = obj.getDouble("progressPercent").toFloat(),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get continue watching items (progress > 0 and < 90%).
     */
    fun getContinueWatching(context: Context): List<HistoryItem> {
        return getAll(context).filter {
            it.progressPercent > 0.01f && it.progressPercent < 0.9f
        }
    }

    /**
     * Remove a specific history item.
     */
    fun remove(context: Context, videoId: String, providerName: String) {
        val items = getAll(context).toMutableList()
        items.removeAll { it.videoId == videoId && it.providerName == providerName }
        saveItems(context, items)
    }

    /**
     * Clear all history.
     */
    fun clear(context: Context) {
        getPrefs(context).edit { remove(KEY_HISTORY) }
    }

    private fun saveItems(context: Context, items: List<HistoryItem>) {
        val arr = org.json.JSONArray()
        items.forEach { item ->
            arr.put(org.json.JSONObject().apply {
                put("videoId", item.videoId)
                put("title", item.title)
                put("providerName", item.providerName)
                put("thumbnailUrl", item.thumbnailUrl)
                put("watchedAt", item.watchedAt)
                put("progressPercent", item.progressPercent)
            })
        }
        getPrefs(context).edit { putString(KEY_HISTORY, arr.toString()) }
    }
}
