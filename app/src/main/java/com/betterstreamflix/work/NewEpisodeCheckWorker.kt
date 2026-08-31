package com.betterstreamflix.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.betterstreamflix.R
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.notifications.GeneralNotificationBuilder
import com.betterstreamflix.notifications.NewContentNotifier
import com.betterstreamflix.notifications.NotificationDispatcher
import com.betterstreamflix.notifications.NotificationPreferences
import com.betterstreamflix.providers.ProviderHealthMonitor
import com.betterstreamflix.utils.UserPreferences
import java.util.concurrent.TimeUnit

/**
 * Periodically checks the active provider home feed for new content IDs
 * and notifies the user when enough unseen items appear.
 */
class NewEpisodeCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!NotificationPreferences.isNewContentNotificationsEnabled(applicationContext)) {
            return Result.success()
        }
        val provider = UserPreferences.currentProvider ?: return Result.success()
        if (!ProviderHealthMonitor.isHealthy(provider.name)) return Result.success()

        return try {
            val categories = provider.getHome()
            val contentIds = categories
                .flatMap { it.list }
                .mapNotNull { item ->
                    when (item) {
                        is Movie -> "movie:${item.id}"
                        is TvShow -> "tv:${item.id}"
                        is Episode -> "ep:${item.id}"
                        else -> null
                    }
                }
                .distinct()

            val newIds = NewContentNotifier.getNewContentIds(applicationContext, contentIds)
            if (NewContentNotifier.shouldNotify(applicationContext, newIds.size)) {
                val title = applicationContext.getString(R.string.notification_new_content_title)
                val message = applicationContext.getString(
                    R.string.notification_new_content_message,
                    newIds.size,
                )
                val notification = GeneralNotificationBuilder.buildNotification(
                    applicationContext,
                    title,
                    message,
                )
                NotificationDispatcher.send(applicationContext, NOTIFICATION_ID, notification)
                NewContentNotifier.markContentAsSeen(applicationContext, newIds)
            }
            ProviderHealthMonitor.recordSuccess(provider.name)
            Result.success()
        } catch (e: Exception) {
            ProviderHealthMonitor.recordFailure(provider.name, e.message ?: e.javaClass.simpleName)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "new_episode_check"
        private const val NOTIFICATION_ID = 4101

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewEpisodeCheckWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
