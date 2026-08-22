package com.betterstreamflix.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CloudSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (!SupabaseProvider.isConfigured || CloudSyncManager.currentUserId() == null) {
            Result.success()
        } else {
            CloudSyncManager.syncNow(applicationContext)
            Result.success()
        }
    } catch (e: Exception) {
        Log.e("CloudSyncWorker", "Background sync failed, will retry", e)
        Result.retry()
    }
}
