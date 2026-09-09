package com.dskja.betterstreamflix.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.activities.main.MainMobileActivity
import com.dskja.betterstreamflix.activities.main.MainTvActivity
import com.dskja.betterstreamflix.utils.UserPreferences

object DownloadNotifier {
    const val CHANNEL_ID = "downloads"
    const val NOTIFICATION_ID_ACTIVE = 42001
    const val NOTIFICATION_ID_COMPLETE = 42002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.download_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.download_notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildForegroundNotification(
        context: Context,
        title: String,
        progressPct: Int,
        indeterminate: Boolean,
        contentText: String,
    ): Notification {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_downloads)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openDownloadsIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (indeterminate || progressPct < 0) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, progressPct.coerceIn(0, 100), false)
        }
        return builder.build()
    }

    fun notifyActive(
        context: Context,
        title: String,
        progressPct: Int,
        indeterminate: Boolean,
        contentText: String,
    ) {
        ensureChannel(context)
        runCatching {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_ACTIVE,
                buildForegroundNotification(context, title, progressPct, indeterminate, contentText),
            )
        }
    }

    fun notifyCompleted(context: Context, title: String) {
        if (!UserPreferences.downloadNotifyComplete) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_downloads)
            .setContentTitle(context.getString(R.string.download_notification_complete_title))
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(openDownloadsIntent(context))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_COMPLETE, notification)
        }
    }

    fun cancelActive(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ACTIVE)
    }

    private fun openDownloadsIntent(context: Context): PendingIntent {
        val isTv = context.packageManager.hasSystemFeature("android.software.leanback")
        val intent = Intent(
            context,
            if (isTv) MainTvActivity::class.java else MainMobileActivity::class.java,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_downloads", true)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
