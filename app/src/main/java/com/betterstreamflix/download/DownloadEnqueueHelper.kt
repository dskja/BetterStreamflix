package com.betterstreamflix.download

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.betterstreamflix.R
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.Video
import com.betterstreamflix.providers.Provider
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared stream resolution + enqueue for detail pages (movies & episodes).
 */
object DownloadEnqueueHelper {

    private const val TAG = "DownloadEnqueueHelper"

    fun videoTypeFor(movie: Movie): Video.Type.Movie = Video.Type.Movie(
        id = movie.id,
        title = movie.title,
        releaseDate = movie.released?.format("yyyy-MM-dd") ?: "",
        poster = movie.poster ?: movie.banner ?: "",
        imdbId = movie.imdbId,
    )

    fun videoTypeFor(episode: Episode): Video.Type.Episode = Video.Type.Episode(
        id = episode.id,
        number = episode.number,
        title = episode.title,
        poster = episode.poster,
        overview = episode.overview,
        tvShow = Video.Type.Episode.TvShow(
            id = episode.tvShow?.id.orEmpty(),
            title = episode.tvShow?.title.orEmpty(),
            poster = episode.tvShow?.poster,
            banner = episode.tvShow?.banner,
            releaseDate = episode.tvShow?.released?.format("yyyy-MM-dd"),
            imdbId = episode.tvShow?.imdbId,
        ),
        season = Video.Type.Episode.Season(
            number = episode.season?.number ?: 0,
            title = episode.season?.title,
        ),
    )

    suspend fun resolveStreamUrl(videoType: Video.Type, contentId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val provider: Provider = UserPreferences.currentProvider ?: return@withContext null
                val servers = provider.getServers(contentId, videoType)
                val server = servers.firstOrNull() ?: return@withContext null
                provider.getVideo(server).source.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "resolveStreamUrl failed for $contentId", e)
                null
            }
        }

    suspend fun enqueueMovie(context: Context, movie: Movie): Boolean {
        val providerName = UserPreferences.currentProvider?.name ?: "unknown"
        if (DownloadActionHelper.findExisting(context, movie.id, providerName) != null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.download_already_queued, Toast.LENGTH_SHORT).show()
            }
            return false
        }
        val videoType = videoTypeFor(movie)
        val url = resolveStreamUrl(videoType, movie.id)
        if (url.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.download_error_no_url, Toast.LENGTH_SHORT).show()
            }
            return false
        }
        return withContext(Dispatchers.Main) {
            DownloadActionHelper.enqueueCurrentVideo(context, videoType, url)
        }
    }

    suspend fun enqueueEpisode(context: Context, episode: Episode): Boolean {
        val providerName = UserPreferences.currentProvider?.name ?: "unknown"
        if (DownloadActionHelper.findExisting(context, episode.id, providerName) != null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.download_already_queued, Toast.LENGTH_SHORT).show()
            }
            return false
        }
        val videoType = videoTypeFor(episode)
        val url = resolveStreamUrl(videoType, episode.id)
        if (url.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.download_error_no_url, Toast.LENGTH_SHORT).show()
            }
            return false
        }
        return withContext(Dispatchers.Main) {
            DownloadActionHelper.enqueueCurrentVideo(context, videoType, url)
        }
    }

    /**
     * Enqueue many episodes sequentially (best-effort). Returns started count.
     * Skips episodes already queued/completed for the current provider.
     * Schedules a single queue worker after the batch.
     */
    suspend fun enqueueEpisodes(
        context: Context,
        episodes: List<Episode>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        var started = 0
        val providerName = UserPreferences.currentProvider?.name ?: "unknown"
        episodes.forEachIndexed { index, episode ->
            if (DownloadActionHelper.findExisting(context, episode.id, providerName) == null) {
                if (enqueueEpisodeSilent(context, episode, scheduleWorker = false)) started++
            }
            onProgress(index + 1, episodes.size)
        }
        if (started > 0) {
            withContext(Dispatchers.Main) {
                DownloadFeature.scheduleQueueWorker(context)
            }
        }
        return started
    }

    /** Like [enqueueEpisode] but without "already queued" toasts (for batch). */
    private suspend fun enqueueEpisodeSilent(
        context: Context,
        episode: Episode,
        scheduleWorker: Boolean = true,
    ): Boolean {
        val videoType = videoTypeFor(episode)
        val url = resolveStreamUrl(videoType, episode.id) ?: return false
        val providerName = UserPreferences.currentProvider?.name ?: "unknown"
        return withContext(Dispatchers.Main) {
            if (StreamTypeDetector.isDrmProtected(url)) return@withContext false
            val decision = DownloadScheduler.shouldStartDownloads(context)
            if (decision is DownloadScheduler.ScheduleDecision.Wait) return@withContext false
            if (DownloadActionHelper.findExisting(context, episode.id, providerName) != null) {
                return@withContext false
            }
            DownloadFeature.ensureNotificationPermission(context)
            DownloadFeature.enqueue(
                context = context,
                videoId = episode.id,
                title = DownloadActionHelper.displayTitle(videoType),
                url = url,
                providerName = providerName,
                artworkUrl = DownloadActionHelper.artworkUrl(videoType),
                scheduleWorker = scheduleWorker,
            )
        }
    }

    fun statusForVideoId(
        context: Context,
        videoId: String,
        providerName: String = UserPreferences.currentProvider?.name ?: "unknown",
    ): DownloadManager.DownloadStatus? =
        DownloadActionHelper.findExisting(context, videoId, providerName)?.status
}
