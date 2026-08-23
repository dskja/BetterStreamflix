package com.betterstreamflix.notifications

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * Update notification builder — creates notifications for available
 * app updates.
 */
object UpdateNotificationBuilder {

    private const val NOTIFICATION_ID = 2000

    /**
     * Build an update available notification.
     */
    fun buildUpdateAvailableNotification(
        context: Context,
        versionName: String,
        releaseNotes: String?,
        channelId: String = NotificationChannelManager.CHANNEL_UPDATE,
    ): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("Update Available")
            .setContentText("Version $versionName is available")
            .apply {
                releaseNotes?.let { setStyle(NotificationCompat.BigTextStyle().bigText(it)) }
            }
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    /**
     * Get the notification ID for updates.
     */
    fun getNotificationId(): Int = NOTIFICATION_ID
}
