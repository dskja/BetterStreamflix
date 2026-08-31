package com.betterstreamflix.download

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.betterstreamflix.R

@UnstableApi
class StreamflixDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_notification_channel,
    R.string.download_notification_description,
) {

    override fun getDownloadManager(): DownloadManager =
        Media3OfflineDownloads.requireManager(this)

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        return DownloadNotificationBuilder.buildForegroundNotification(
            this,
            downloads,
            notMetRequirements,
        )
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "downloads"

        fun start(context: android.content.Context) {
            DownloadService.start(context, StreamflixDownloadService::class.java)
        }

        fun startForeground(context: android.content.Context) {
            DownloadService.startForeground(context, StreamflixDownloadService::class.java)
        }
    }
}
