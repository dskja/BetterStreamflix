package com.betterstreamflix.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules background work using WorkManager.
 * Centralizes all periodic background task scheduling.
 */
object WorkScheduler {

    const val WORK_ARTWORK_REPAIR = "artwork_repair"
    const val WORK_CACHE_CLEANUP = "cache_cleanup"
    const val WORK_UPDATE_CHECK = "update_check"
    const val WORK_SYNC = "cloud_sync"

    /**
     * Schedule all periodic background tasks.
     * Call from Application.onCreate().
     */
    fun scheduleAll(context: Context) {
        scheduleArtworkRepair(context)
        scheduleCacheCleanup(context)
        scheduleUpdateCheck(context)
    }

    /**
     * Schedule artwork repair to run daily.
     */
    private fun scheduleArtworkRepair(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<ArtworkRepairWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_ARTWORK_REPAIR,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Schedule cache cleanup to run daily.
     */
    private fun scheduleCacheCleanup(context: Context) {
        val request = PeriodicWorkRequestBuilder<CacheCleanupWorker>(1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_CACHE_CLEANUP,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Schedule update check to run every 12 hours.
     */
    private fun scheduleUpdateCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_UPDATE_CHECK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Cancel all scheduled work.
     */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
    }
}
