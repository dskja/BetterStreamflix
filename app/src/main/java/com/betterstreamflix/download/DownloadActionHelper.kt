package com.betterstreamflix.download

import android.content.Context
import android.widget.Toast
import com.betterstreamflix.R
import com.betterstreamflix.models.Video
import com.betterstreamflix.utils.UserPreferences

object DownloadActionHelper {

    fun enqueueCurrentVideo(
        context: Context,
        video: Video,
        streamUrl: String,
    ): Boolean {
        if (streamUrl.isBlank()) {
            Toast.makeText(context, R.string.download_error_no_url, Toast.LENGTH_SHORT).show()
            return false
        }
        if (StreamTypeDetector.isDrmProtected(streamUrl)) {
            Toast.makeText(context, R.string.download_error_drm, Toast.LENGTH_LONG).show()
            return false
        }
        val decision = DownloadScheduler.shouldStartDownloads(context)
        if (decision is DownloadScheduler.ScheduleDecision.Wait) {
            Toast.makeText(context, decision.reason, Toast.LENGTH_LONG).show()
            return false
        }
        val providerName = UserPreferences.currentProvider?.name ?: "unknown"
        val ok = DownloadFeature.enqueue(
            context = context,
            videoId = video.id,
            title = video.title ?: video.id,
            url = streamUrl,
            providerName = providerName,
        )
        if (ok) {
            Toast.makeText(context, R.string.download_started, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, R.string.download_error_generic, Toast.LENGTH_SHORT).show()
        }
        return ok
    }
}
