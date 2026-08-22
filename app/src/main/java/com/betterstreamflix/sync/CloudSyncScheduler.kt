package com.betterstreamflix.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CloudSyncScheduler {
    private const val PERIODIC_TAG = "cloud-user-state-periodic"

    fun enqueue(context: Context) {
        val userId = CloudSyncManager.currentUserId() ?: return
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "cloud-user-state-$userId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun schedulePeriodic(context: Context) {
        val userId = CloudSyncManager.currentUserId() ?: return
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            "$PERIODIC_TAG-$userId",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(PERIODIC_TAG)
    }
}
