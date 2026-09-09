package com.dskja.betterstreamflix.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DownloadBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        runCatching {
            StreamflixDownloadManager.get(context.applicationContext)
            StreamflixDownloadService.start(context.applicationContext)
        }
    }
}
