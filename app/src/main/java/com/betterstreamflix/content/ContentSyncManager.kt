package com.betterstreamflix.content

import android.content.Context
import androidx.core.content.edit

/**
 * Content sync manager — syncs favorites and watch history across
 * providers.
 */
object ContentSyncManager {

    /**
     * Sync favorites from local storage to all providers.
     */
    suspend fun syncFavorites(context: Context) {
        val repo = com.betterstreamflix.database.AppSettingsRepository(context)
        val favorites = repo.getFavorites()
        // Would iterate through favorites and sync to each provider
    }

    /**
     * Sync watch history from local storage.
     */
    suspend fun syncWatchHistory(context: Context) {
        val repo = com.betterstreamflix.database.AppSettingsRepository(context)
        val history = repo.getWatchHistory()
        // Would process history items for cross-provider matching
    }

    /**
     * Merge content from multiple providers, deduplicating by title.
     */
    fun <T> mergeContent(
        providerContent: Map<String, List<T>>,
        titleExtractor: (T) -> String,
    ): List<T> {
        val seen = mutableSetOf<String>()
        val merged = mutableListOf<T>()

        for (items in providerContent.values) {
            for (item in items) {
                val title = titleExtractor(item).lowercase().trim()
                if (title !in seen) {
                    seen.add(title)
                    merged.add(item)
                }
            }
        }
        return merged
    }

    /**
     * Check if sync is needed based on last sync time.
     */
    fun isSyncNeeded(context: Context, syncIntervalMs: Long = 60 * 60 * 1000L): Boolean {
        val prefs = context.getSharedPreferences("content_sync", Context.MODE_PRIVATE)
        val lastSync = prefs.getLong("last_sync", 0)
        return System.currentTimeMillis() - lastSync > syncIntervalMs
    }

    /**
     * Record sync completion.
     */
    fun recordSync(context: Context) {
        context.getSharedPreferences("content_sync", Context.MODE_PRIVATE).edit {
            putLong("last_sync", System.currentTimeMillis())
        }
    }
}
