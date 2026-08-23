package com.betterstreamflix.security

import android.content.Context
import androidx.core.content.edit

/**
 * Data retention manager — manages how long different types of data
 * are kept before automatic cleanup.
 */
object DataRetentionManager {

    private const val PREFS_NAME = "data_retention"

    enum class DataType(val defaultRetentionDays: Int) {
        WATCH_HISTORY(90),
        SEARCH_HISTORY(30),
        DOWNLOADS(365),
        CACHED_METADATA(7),
        ERROR_LOGS(14),
        PLAYBACK_POSITIONS(365),
    }

    /**
     * Get retention period for a data type.
     */
    fun getRetentionDays(context: Context, type: DataType): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(type.name, type.defaultRetentionDays)
    }

    /**
     * Set retention period for a data type.
     */
    fun setRetentionDays(context: Context, type: DataType, days: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putInt(type.name, days)
        }
    }

    /**
     * Get the cutoff timestamp for a data type.
     */
    fun getRetentionCutoff(context: Context, type: DataType): Long {
        val days = getRetentionDays(context, type)
        return System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L)
    }

    /**
     * Run cleanup for all data types based on retention policies.
     */
    suspend fun runCleanup(context: Context) {
        val repo = com.betterstreamflix.database.AppSettingsRepository(context)

        // Watch history
        val historyCutoff = getRetentionCutoff(context, DataType.WATCH_HISTORY)
        repo.clearWatchHistoryOlderThan(historyCutoff)

        // Cached metadata
        val metadataCutoff = getRetentionCutoff(context, DataType.CACHED_METADATA)
        repo.clearOldMetadata(metadataCutoff)

        // Error logs
        val errorCutoff = getRetentionCutoff(context, DataType.ERROR_LOGS)
        val entries = com.betterstreamflix.resilience.ErrorLog.getEntries(context)
        val recentEntries = entries.filter { it.timestamp >= errorCutoff }
        // ErrorLog doesn't have selective clear, so clear all if needed
        if (recentEntries.size != entries.size) {
            com.betterstreamflix.resilience.ErrorLog.clear(context)
        }

        // Disk cache
        com.betterstreamflix.performance.DiskCacheManager.trimCache(context)
    }

    /**
     * Reset all retention settings to defaults.
     */
    fun resetToDefaults(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            clear()
        }
    }
}
