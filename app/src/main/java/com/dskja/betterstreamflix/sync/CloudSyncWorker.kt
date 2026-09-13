package com.dskja.betterstreamflix.sync

import android.content.Context
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
    } catch (_: Throwable) {
        // Persistent failures (e.g. expired session) must not retry forever.
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
