package com.betterstreamflix.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Sets up notification channels for the app.
 * Must be called early in Application.onCreate().
 */
object NotificationChannelSetup {

    /**
     * Create all notification channels.
     * Safe to call multiple times — channels are idempotent.
     */
    fun setup(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return

        // Downloads channel
        manager.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_DOWNLOAD,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Download progress notifications"
            }
        )

        // Updates channel
        manager.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_UPDATE,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications about new app versions"
            }
        )

        // General channel
        manager.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "General notifications"
            }
        )
    }
}
