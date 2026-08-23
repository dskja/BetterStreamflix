package com.betterstreamflix.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Notification channel manager — creates and manages notification channels
 * for different types of notifications.
 */
object NotificationChannelManager {

    const val CHANNEL_DOWNLOAD = "downloads"
    const val CHANNEL_UPDATE = "updates"
    const val CHANNEL_PLAYBACK = "playback"
    const val CHANNEL_GENERAL = "general"

    /**
     * Create all notification channels.
     */
    fun createChannels(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DOWNLOAD,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Download progress and completion notifications"
                },
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_UPDATE,
                    "Updates",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "App update notifications"
                },
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PLAYBACK,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Media playback controls"
                },
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_GENERAL,
                    "General",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "General notifications"
                },
            )
        }
    }

    /**
     * Check if a channel is enabled.
     */
    fun isChannelEnabled(context: Context, channelId: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        val channel = manager.getNotificationChannel(channelId) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Delete a notification channel.
     */
    fun deleteChannel(context: Context, channelId: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.deleteNotificationChannel(channelId)
        }
    }
}
