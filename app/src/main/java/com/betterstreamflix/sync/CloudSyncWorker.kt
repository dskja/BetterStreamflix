package com.betterstreamflix.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.betterstreamflix.utils.UserProfiles
import io.github.jan.supabase.auth.auth

class CloudSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val profileId = inputData.getString(KEY_PROFILE_ID) ?: return Result.success()
        val expectedUserId = inputData.getString(KEY_USER_ID) ?: return Result.success()

        // Safest initial policy: only sync while this profile is active.
        if (UserProfiles.active().id != profileId) {
            Log.i(TAG, "Skipping sync for inactive profile=$profileId")
            return Result.success()
        }

        return try {
            if (!SupabaseProvider.isConfigured) return Result.success()
            val client = SupabaseProvider.clientFor(applicationContext, profileId)
            client.auth.awaitInitialization()
            val sessionUserId = client.auth.currentSessionOrNull()?.user?.id
            if (sessionUserId == null || sessionUserId != expectedUserId) {
                Log.i(TAG, "Session missing or changed for profile=$profileId; leaving queue")
                return Result.success()
            }
            if (UserProfiles.active().id != profileId) {
                Log.i(TAG, "Profile switched before apply; leaving queue for profile=$profileId")
                return Result.success()
            }
            CloudSyncManager.syncNow(applicationContext, profileId)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed, will retry", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_PROFILE_ID = "profileId"
        const val KEY_USER_ID = "userId"
        private const val TAG = "CloudSyncWorker"
    }
}
