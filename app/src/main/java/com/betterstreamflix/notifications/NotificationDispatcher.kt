package com.betterstreamflix.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * Notification dispatcher — handles sending and canceling notifications
 * with permission checking.
 */
object NotificationDispatcher {

    /**
     * Send a notification.
     */
    fun send(context: Context, id: Int, notification: android.app.Notification) {
        if (!hasPermission(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    /**
     * Cancel a notification.
     */
    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    /**
     * Cancel all notifications.
     */
    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    /**
     * Check if notification permission is granted.
     */
    fun hasPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Get active notification count.
     */
    fun getActiveNotificationCount(context: Context): Int {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return 0
        val manager = context.getSystemService(NotificationManager::class.java) ?: return 0
        return manager.activeNotifications.size
    }
}
