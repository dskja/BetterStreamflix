package com.betterstreamflix.performance

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Background task scheduler — schedules periodic maintenance tasks
 * like cache cleanup, metadata refresh, and download queue processing.
 */
object BackgroundTaskScheduler {

    private const val WORK_CACHE_CLEANUP = "cache_cleanup"
    private const val WORK_METADATA_REFRESH = "metadata_refresh"
    private const val WORK_DOWNLOAD_QUEUE = "download_queue"
    private const val WORK_HISTORY_CLEANUP = "history_cleanup"

    /**
     * Schedule all periodic background tasks.
     */
    fun scheduleAll(context: Context) {
        scheduleCacheCleanup(context)
        scheduleMetadataRefresh(context)
        scheduleHistoryCleanup(context)
    }

    /**
     * Schedule cache cleanup to run daily.
     */
    fun scheduleCacheCleanup(context: Context) {
        val constraints = Constraints.Builder()
            .requiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<CacheCleanupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_CACHE_CLEANUP,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Schedule metadata refresh to run every 12 hours.
     */
    fun scheduleMetadataRefresh(context: Context) {
        val constraints = Constraints.Builder()
            .requiredNetworkType(NetworkType.UNMETERED)
            .requiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<MetadataRefreshWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_METADATA_REFRESH,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Schedule history cleanup to run weekly.
     */
    fun scheduleHistoryCleanup(context: Context) {
        val constraints = Constraints.Builder()
            .requiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<HistoryCleanupWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_HISTORY_CLEANUP,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Cancel all scheduled tasks.
     */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_CACHE_CLEANUP)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_METADATA_REFRESH)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_HISTORY_CLEANUP)
    }
}
