package com.betterstreamflix.notifications

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * Download notification builder — creates progress and completion
 * notifications for downloads.
 */
object DownloadNotificationBuilder {

    private var notificationId = 1000

    /**
     * Get the next notification ID.
     */
    fun getNextNotificationId(): Int = notificationId++

    /**
     * Build a download progress notification.
     */
    fun buildProgressNotification(
        context: Context,
        title: String,
        progress: Int,
        max: Int = 100,
        channelId: String = NotificationChannelManager.CHANNEL_DOWNLOAD,
    ): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText("$progress% complete")
            .setProgress(max, progress, false)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * Build a download complete notification.
     */
    fun buildCompleteNotification(
        context: Context,
        title: String,
        channelId: String = NotificationChannelManager.CHANNEL_DOWNLOAD,
    ): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
    }

    /**
     * Build a download failed notification.
     */
    fun buildFailedNotification(
        context: Context,
        title: String,
        error: String,
        channelId: String = NotificationChannelManager.CHANNEL_DOWNLOAD,
    ): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("Download Failed")
            .setContentText("$title: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
    }

    /**
     * Build a download queued notification.
     */
    fun buildQueuedNotification(
        context: Context,
        title: String,
        channelId: String = NotificationChannelManager.CHANNEL_DOWNLOAD,
    ): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("Download Queued")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
    }
}
