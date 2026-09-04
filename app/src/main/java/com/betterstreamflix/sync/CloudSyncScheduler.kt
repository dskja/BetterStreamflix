package com.betterstreamflix.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object CloudSyncScheduler {
    private const val PERIODIC_PREFIX = "cloud-user-state-periodic"

    fun enqueue(context: Context, profileId: String, userId: String) {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setInputData(
                workDataOf(
                    CloudSyncWorker.KEY_PROFILE_ID to profileId,
                    CloudSyncWorker.KEY_USER_ID to userId,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(profileTag(profileId))
            .addTag(userTag(profileId, userId))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            userTag(profileId, userId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun schedulePeriodic(context: Context, profileId: String, userId: String) {
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    CloudSyncWorker.KEY_PROFILE_ID to profileId,
                    CloudSyncWorker.KEY_USER_ID to userId,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(PERIODIC_PREFIX)
            .addTag(profileTag(profileId))
            .addTag(userTag(profileId, userId))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            "$PERIODIC_PREFIX-$profileId-$userId",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelPeriodic(context: Context, profileId: String? = null) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (profileId == null) {
            wm.cancelAllWorkByTag(PERIODIC_PREFIX)
        } else {
            wm.cancelAllWorkByTag(profileTag(profileId))
        }
    }

    fun cancelForProfile(context: Context, profileId: String) {
        WorkManager.getInstance(context.applicationContext)
            .cancelAllWorkByTag(profileTag(profileId))
    }

    fun profileTag(profileId: String): String = "cloud-profile-$profileId"

    fun userTag(profileId: String, userId: String): String =
        "cloud-user-state-$profileId-$userId"
}
