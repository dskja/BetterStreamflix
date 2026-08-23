package com.betterstreamflix.search

import android.content.Context
import androidx.core.content.edit

/**
 * Search history manager — persists recent searches and provides
 * suggestions based on past queries.
 */
object SearchHistoryManager {

    private const val PREFS_NAME = "search_history"
    private const val KEY_HISTORY = "recent_searches"
    private const val MAX_HISTORY = 20

    data class SearchEntry(
        val query: String,
        val timestamp: Long,
        val resultCount: Int,
    )

    /**
     * Add a search to history.
     */
    fun addSearch(context: Context, query: String, resultCount: Int = 0) {
        if (query.isBlank()) return
        val history = getHistory(context).toMutableList()

        // Remove duplicate
        history.removeAll { it.query.equals(query, ignoreCase = true) }

        // Add at front
        history.add(0, SearchEntry(query.trim(), System.currentTimeMillis(), resultCount))

        // Trim
        if (history.size > MAX_HISTORY) {
            history.subList(MAX_HISTORY, history.size).clear()
        }

        saveHistory(context, history)
    }

    /**
     * Get search history.
     */
    fun getHistory(context: Context): List<SearchEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                SearchEntry(
                    query = obj.getString("query"),
                    timestamp = obj.getLong("timestamp"),
                    resultCount = obj.getInt("resultCount"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get search suggestions based on partial input.
     */
    fun getSuggestions(context: Context, partial: String): List<String> {
        if (partial.isBlank()) return getHistory(context).map { it.query }.take(5)
        return getHistory(context)
            .filter { it.query.contains(partial, ignoreCase = true) }
            .map { it.query }
            .take(5)
    }

    /**
     * Clear search history.
     */
    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_HISTORY)
        }
    }

    /**
     * Remove a specific search entry.
     */
    fun removeSearch(context: Context, query: String) {
        val history = getHistory(context).toMutableList()
        history.removeAll { it.query.equals(query, ignoreCase = true) }
        saveHistory(context, history)
    }

    private fun saveHistory(context: Context, history: List<SearchEntry>) {
        val array = org.json.JSONArray()
        history.forEach { entry ->
            array.put(org.json.JSONObject().apply {
                put("query", entry.query)
                put("timestamp", entry.timestamp)
                put("resultCount", entry.resultCount)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_HISTORY, array.toString())
        }
    }
}
