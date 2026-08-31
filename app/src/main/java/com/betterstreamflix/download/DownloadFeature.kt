package com.betterstreamflix.download

import android.content.Context
import android.util.Log
import java.util.UUID

/**
 * Public entry point for the offline download feature (P3).
 * Wires the existing queue/manager stack for UI and player overflow menus.
 */
object DownloadFeature {

    private const val TAG = "DownloadFeature"

    fun enqueue(
        context: Context,
        videoId: String,
        title: String,
        url: String,
        providerName: String,
        filePath: String = "",
    ): Boolean {
        val appContext = context.applicationContext
        val decision = DownloadScheduler.shouldStartDownloads(appContext)
        if (decision !is DownloadScheduler.ScheduleDecision.Proceed) {
            Log.i(TAG, "Download deferred: $decision")
        }
        return runCatching {
            val taskId = UUID.randomUUID().toString()
            val task = DownloadManager.DownloadTask(
                id = taskId,
                videoId = videoId,
                title = title,
                providerName = providerName,
                url = url,
                filePath = filePath.ifBlank {
                    DownloadFileManager.getDownloadFile(appContext, taskId).absolutePath
                },
            )
            DownloadManager.addDownload(appContext, task)
            true
        }.onFailure { e ->
            Log.e(TAG, "enqueue failed", e)
        }.getOrDefault(false)
    }

    fun list(context: Context): List<DownloadManager.DownloadTask> =
        DownloadManager.getAllDownloads(context.applicationContext)
}
