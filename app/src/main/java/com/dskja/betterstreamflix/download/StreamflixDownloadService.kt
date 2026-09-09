package com.dskja.betterstreamflix.download

import android.app.Notification
import android.content.Context
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import com.dskja.betterstreamflix.R

class StreamflixDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DownloadNotifier.CHANNEL_ID,
    R.string.download_notification_channel,
    R.string.download_notification_channel_desc,
) {
    override fun getDownloadManager(): DownloadManager =
        StreamflixDownloadManager.get(this)

    override fun getScheduler(): Scheduler? =
        if (Util.SDK_INT >= 21) PlatformScheduler(this, JOB_ID) else null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        val helper = StreamflixDownloadManager.notificationHelper(this)
        return helper.buildProgressNotification(
            this,
            R.drawable.ic_menu_downloads,
            null,
            null,
            downloads,
            notMetRequirements,
        )
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 42001
        private const val JOB_ID = 4201

        fun start(context: Context) {
            try {
                startForeground(
                    context,
                    StreamflixDownloadService::class.java,
                    FOREGROUND_NOTIFICATION_ID,
                )
            } catch (_: Exception) {
                start(context, StreamflixDownloadService::class.java)
            }
        }
    }
}
