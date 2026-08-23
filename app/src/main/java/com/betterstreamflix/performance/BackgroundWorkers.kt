package com.betterstreamflix.performance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Worker classes for background tasks.
 */

class PerformanceCacheCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            DiskCacheManager.trimCache(applicationContext)
            MemoryCacheManager.clearAll()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

class MetadataRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Refresh stale metadata in background
            val repo = com.betterstreamflix.database.AppDataRepository(applicationContext)
            val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            repo.clearOldMetadata(cutoff)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

class HistoryCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = com.betterstreamflix.database.AppDataRepository(applicationContext)
            val cutoff = System.currentTimeMillis() - (90 * 24 * 60 * 60 * 1000)
            repo.clearWatchHistoryOlderThan(cutoff)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
