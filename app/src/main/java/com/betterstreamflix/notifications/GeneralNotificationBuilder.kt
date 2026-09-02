package com.betterstreamflix.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * General notification builder — creates general-purpose notifications
 * for various app events.
 */
object GeneralNotificationBuilder {

    private var notificationIdCounter = 3000

    /**
     * Get the next notification ID.
     */
    fun getNextNotificationId(): Int = notificationIdCounter++

    /**
     * Build a general notification.
     */
    fun buildNotification(
        context: Context,
        title: String,
        message: String,
        channelId: String = NotificationChannelManager.CHANNEL_GENERAL,
        ongoing: Boolean = false,
        contentIntent: PendingIntent? = null,
    ): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .apply { contentIntent?.let { setContentIntent(it) } }
            .build()
    }

    /**
     * Build a big text notification.
     */
    fun buildBigTextNotification(
        context: Context,
        title: String,
        message: String,
        channelId: String = NotificationChannelManager.CHANNEL_GENERAL,
    ): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
    }

    /**
     * Build an error notification.
     */
    fun buildErrorNotification(
        context: Context,
        title: String,
        message: String,
        channelId: String = NotificationChannelManager.CHANNEL_GENERAL,
    ): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }
}
