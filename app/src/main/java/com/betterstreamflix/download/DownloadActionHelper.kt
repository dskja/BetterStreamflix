package com.betterstreamflix.download

import android.content.Context
import android.widget.Toast
import com.betterstreamflix.R
import com.betterstreamflix.models.Video
import com.betterstreamflix.utils.UserPreferences

object DownloadActionHelper {

    fun displayTitle(videoType: Video.Type): String = when (videoType) {
        is Video.Type.Movie -> videoType.title
        is Video.Type.Episode -> {
            val show = videoType.tvShow.title.ifBlank { "Episode" }
            val epTitle = videoType.title?.takeIf { it.isNotBlank() }
            val seasonEp = "S${videoType.season.number.toString().padStart(2, '0')}" +
                "E${videoType.number.toString().padStart(2, '0')}"
            if (epTitle != null) "$show · $seasonEp · $epTitle" else "$show · $seasonEp"
        }
    }

    fun findExisting(
        context: Context,
        videoId: String,
        providerName: String = UserPreferences.currentProvider?.name ?: "unknown",
    ): DownloadManager.DownloadTask? =
        DownloadFeature.findExisting(context, videoId, providerName)

    fun enqueueCurrentVideo(
        context: Context,
        videoType: Video.Type,
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
        DownloadFeature.ensureNotificationPermission(context)
        val videoId = when (videoType) {
            is Video.Type.Movie -> videoType.id
            is Video.Type.Episode -> videoType.id
        }
        val providerName = UserPreferences.currentProvider?.name ?: "unknown"
        val existing = findExisting(context, videoId, providerName)
        if (existing != null) {
            Toast.makeText(context, R.string.download_already_queued, Toast.LENGTH_SHORT).show()
            return false
        }
        val title = displayTitle(videoType)
        val ok = DownloadFeature.enqueue(
            context = context,
            videoId = videoId,
            title = title,
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
