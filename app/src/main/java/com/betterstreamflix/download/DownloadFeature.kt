package com.betterstreamflix.download

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Public entry point for the offline download feature.
 */
object DownloadFeature {

    private const val TAG = "DownloadFeature"
    const val NOTIFICATION_PERMISSION_REQUEST = 4217

    fun observe(context: Context): Flow<List<DownloadManager.DownloadTask>> =
        DownloadRepository(context).observeTasks()

    fun enqueue(
        context: Context,
        videoId: String,
        title: String,
        url: String,
        providerName: String,
        filePath: String = "",
    ): Boolean {
        val appContext = context.applicationContext
        if (StreamTypeDetector.isDrmProtected(url)) {
            Log.w(TAG, "DRM stream not downloadable: $title")
            return false
        }
        val decision = DownloadScheduler.shouldStartDownloads(appContext)
        if (decision is DownloadScheduler.ScheduleDecision.Wait) {
            Log.i(TAG, "Download blocked: $decision")
            return false
        }
        if (decision is DownloadScheduler.ScheduleDecision.Defer) {
            Log.i(TAG, "Download deferred but queued: $decision")
        }
        return runCatching {
            ensureNotificationPermission(context)
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
            WorkManager.getInstance(appContext).enqueue(
                OneTimeWorkRequestBuilder<DownloadWorker>().build(),
            )
            true
        }.onFailure { e ->
            Log.e(TAG, "enqueue failed", e)
        }.getOrDefault(false)
    }

    fun list(context: Context): List<DownloadManager.DownloadTask> =
        DownloadRepository(context).getAllBlocking()

    fun retry(context: Context, id: String) {
        DownloadManager.resumeDownload(context, id)
        WorkManager.getInstance(context.applicationContext).enqueue(
            OneTimeWorkRequestBuilder<DownloadWorker>().build(),
        )
    }

    fun ensureNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        val activity = (context as? Activity) ?: return
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_REQUEST,
        )
    }
}
