package com.betterstreamflix.download

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.betterstreamflix.utils.Constants

/**
 * Download notification helper — shows progress notifications for active downloads.
 */
class DownloadNotificationHelper(private val context: Context) {

    /**
     * Show a download progress notification.
     */
    fun showProgressNotification(
        title: String,
        progress: Int,
        downloadId: Int,
        contentText: String = "$progress%",
    ) {
        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_DOWNLOAD)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(downloadId, notification)
        }
    }

    /**
     * Show a download complete notification.
     */
    fun showCompleteNotification(title: String, downloadId: Int) {
        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_DOWNLOAD)
            .setContentTitle("Download complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(downloadId, notification)
    }

    /**
     * Show a download failed notification.
     */
    fun showFailedNotification(title: String, error: String, downloadId: Int) {
        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_DOWNLOAD)
            .setContentTitle("Download failed")
            .setContentText("$title: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(downloadId, notification)
    }

    /**
     * Cancel a notification.
     */
    fun cancelNotification(downloadId: Int) {
        NotificationManagerCompat.from(context).cancel(downloadId)
    }
}
