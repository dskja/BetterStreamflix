package com.betterstreamflix.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.betterstreamflix.R
import com.betterstreamflix.notifications.NotificationPreferences

@UnstableApi
object DownloadNotificationBuilder {

    private const val CHANNEL_ID = "downloads"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.download_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    fun buildForegroundNotification(
        context: Context,
        downloads: List<Download>,
        @Suppress("UNUSED_PARAMETER") notMetRequirements: Int,
    ): Notification {
        ensureChannel(context)
        val active = downloads.count { it.state == Download.STATE_DOWNLOADING }
        val completed = downloads.count { it.state == Download.STATE_COMPLETED }
        val title = context.getString(R.string.download_notification_title)
        val text = when {
            active > 0 -> context.getString(R.string.download_notification_progress, active)
            completed > 0 -> context.getString(R.string.download_notification_complete, completed)
            else -> context.getString(R.string.download_notification_description)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(active > 0)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun buildProgressNotification(context: Context, title: String, percent: Int): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setProgress(100, percent.coerceIn(0, 100), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun buildCompleteNotification(context: Context, title: String): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.download_notification_complete, 1))
            .setContentText(title)
            .setAutoCancel(true)
            .build()
    }

    fun buildFailedNotification(context: Context, title: String, error: String): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.download_notification_failed))
            .setContentText("$title: $error")
            .setAutoCancel(true)
            .build()
    }

    fun notifyIfEnabled(context: Context, notificationId: Int, notification: Notification) {
        if (!NotificationPreferences.isDownloadNotificationsEnabled(context)) return
        context.getSystemService(NotificationManager::class.java)?.notify(notificationId, notification)
    }

    fun cancelIfEnabled(context: Context, notificationId: Int) {
        if (!NotificationPreferences.isDownloadNotificationsEnabled(context)) return
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId)
    }
}
