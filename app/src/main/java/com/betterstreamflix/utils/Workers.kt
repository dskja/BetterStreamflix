package com.betterstreamflix.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Worker for repairing missing artwork in background.
 */
class ArtworkRepairWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            ArtworkRepairScheduler.schedule(applicationContext, UserPreferences.currentProvider)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * Worker for periodic cache cleanup.
 */
class CacheCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val threshold = if (AppConfig.isTv) Constants.CACHE_THRESHOLD_TV_MB else Constants.CACHE_THRESHOLD_MOBILE_MB
            CacheUtils.autoClearIfNeeded(applicationContext, thresholdMb = threshold)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * Worker for checking app updates in background.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val update = InAppUpdater.getReleaseUpdate()
            if (update != null) {
                // Could post a notification about the update
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
