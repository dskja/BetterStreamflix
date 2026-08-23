package com.betterstreamflix.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Favorites manager — manages bookmarked/favorited content.
 */
object FavoritesManager {

    private const val PREFS_NAME = "favorites"
    private const val KEY_FAVORITES = "favorite_items"
    private const val MAX_FAVORITES = 500

    data class FavoriteItem(
        val videoId: String,
        val title: String,
        val providerName: String,
        val thumbnailUrl: String?,
        val type: String, // "movie" or "tvshow"
        val addedAt: Long = System.currentTimeMillis(),
    )

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Add a favorite.
     */
    fun add(context: Context, item: FavoriteItem) {
        val items = getAll(context).toMutableList()
        if (items.none { it.videoId == item.videoId && it.providerName == item.providerName }) {
            items.add(0, item)
            if (items.size > MAX_FAVORITES) {
                items.subList(MAX_FAVORITES, items.size).clear()
            }
            saveItems(context, items)
        }
    }

    /**
     * Remove a favorite.
     */
    fun remove(context: Context, videoId: String, providerName: String) {
        val items = getAll(context).toMutableList()
        items.removeAll { it.videoId == videoId && it.providerName == providerName }
        saveItems(context, items)
    }

    /**
     * Check if an item is favorited.
     */
    fun isFavorite(context: Context, videoId: String, providerName: String): Boolean {
        return getAll(context).any { it.videoId == videoId && it.providerName == providerName }
    }

    /**
     * Toggle favorite status. Returns true if now favorited.
     */
    fun toggle(context: Context, item: FavoriteItem): Boolean {
        return if (isFavorite(context, item.videoId, item.providerName)) {
            remove(context, item.videoId, item.providerName)
            false
        } else {
            add(context, item)
            true
        }
    }

    /**
     * Get all favorites.
     */
    fun getAll(context: Context): List<FavoriteItem> {
        val json = getPrefs(context).getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                FavoriteItem(
                    videoId = obj.getString("videoId"),
                    title = obj.getString("title"),
                    providerName = obj.getString("providerName"),
                    thumbnailUrl = obj.optString("thumbnailUrl", null),
                    type = obj.getString("type"),
                    addedAt = obj.getLong("addedAt"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get favorites filtered by type.
     */
    fun getByType(context: Context, type: String): List<FavoriteItem> {
        return getAll(context).filter { it.type == type }
    }

    /**
     * Clear all favorites.
     */
    fun clear(context: Context) {
        getPrefs(context).edit { remove(KEY_FAVORITES) }
    }

    private fun saveItems(context: Context, items: List<FavoriteItem>) {
        val arr = org.json.JSONArray()
        items.forEach { item ->
            arr.put(org.json.JSONObject().apply {
                put("videoId", item.videoId)
                put("title", item.title)
                put("providerName", item.providerName)
                put("thumbnailUrl", item.thumbnailUrl)
                put("type", item.type)
                put("addedAt", item.addedAt)
            })
        }
        getPrefs(context).edit { putString(KEY_FAVORITES, arr.toString()) }
    }
}
