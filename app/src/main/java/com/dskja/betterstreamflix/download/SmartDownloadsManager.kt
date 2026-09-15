package com.dskja.betterstreamflix.download

import android.content.Context
import android.util.Log
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.utils.UserPreferences
import org.json.JSONObject

/**
 * Netflix-style Smart Downloads (opt-in):
 *  - `downloadAutoDeleteWatched`: remove a finished episode's download.
 *  - `downloadSmartEnabled`:     silently enqueue the next episode.
 *
 * Invoked from the players' watch-finished paths. All provider/network work is
 * best-effort — failures just skip the smart action, never crash playback.
 */
object SmartDownloadsManager {
    private const val TAG = "SmartDownloads"

    /** In-memory debounce so repeated pause/stop events don't re-trigger work. */
    private val handled = mutableSetOf<String>()

    suspend fun onEpisodeFinished(context: Context, videoType: Video.Type.Episode) {
        val autoDelete = UserPreferences.downloadAutoDeleteWatched
        val smart = UserPreferences.downloadSmartEnabled
        if (!autoDelete && !smart) return

        val key = "${videoType.tvShow.id}:${videoType.season.number}:${videoType.number}:${videoType.id}"
        if (!handled.add(key)) return
        if (handled.size > 64) handled.clear() // bound the debounce set

        val repo = DownloadRepository.get(context)
        val item = repo.getAllOnce().firstOrNull { entity ->
            entity.kind == DownloadKind.EPISODE.name &&
                videoTypeIdEquals(entity.videoTypeJson, videoType.id)
        }

        // Flag watched state for the downloads tab badge, then maybe delete.
        if (item != null && !item.watchedOffline) {
            repo.upsert(item.copy(watchedOffline = true, updatedAt = System.currentTimeMillis()))
        }
        if (autoDelete && item?.state == DownloadItemState.COMPLETED.name) {
            repo.remove(item.id)
        }
        if (smart) enqueueNextEpisode(context, videoType)
    }

    private suspend fun enqueueNextEpisode(context: Context, current: Video.Type.Episode) {
        val provider = UserPreferences.currentProvider ?: return
        if (provider is com.dskja.betterstreamflix.providers.IptvProvider) return
        if (UserPreferences.downloadWifiOnly && DownloadConnectivityMonitor.isMetered(context)) return
        if (!DownloadStorage.hasEnoughSpace(context)) return
        if (DownloadConnectivityMonitor.current(context).type == DownloadNetworkType.NONE) return

        val next = runCatching { findNextEpisode(context, current) }.getOrNull() ?: return

        // Skip if the next episode is already downloaded or in flight.
        val existing = DownloadRepository.get(context).getAllOnce().firstOrNull { entity ->
            entity.kind == DownloadKind.EPISODE.name &&
                videoTypeIdEquals(entity.videoTypeJson, next.id)
        }
        if (existing != null && existing.state != DownloadItemState.FAILED.name) return

        val episodeType = Video.Type.Episode(
            id = next.id,
            number = next.number,
            title = next.title,
            poster = next.poster,
            overview = next.overview,
            tvShow = Video.Type.Episode.TvShow(
                id = current.tvShow.id,
                title = current.tvShow.title,
                poster = current.tvShow.poster,
                banner = current.tvShow.banner,
                releaseDate = current.tvShow.releaseDate,
                imdbId = current.tvShow.imdbId,
            ),
            season = Video.Type.Episode.Season(
                number = next.seasonNumber,
                title = next.seasonTitle,
            ),
        )

        val servers = runCatching { provider.getServers(next.id, episodeType) }.getOrNull()
        val server = servers?.firstOrNull() ?: return
        val video = runCatching { provider.getVideo(server) }.getOrNull() ?: return
        if (video.source.isBlank()) return

        val contentKey = DownloadContentKey.episode(
            providerName = provider.name,
            tvShowId = current.tvShow.id,
            seasonNumber = next.seasonNumber,
            episodeNumber = next.number,
            episodeId = next.id,
        )
        DownloadRepository.get(context).enqueueResolved(
            contentKey = contentKey,
            providerName = provider.name,
            kind = DownloadKind.EPISODE,
            title = current.tvShow.title,
            subtitle = "S${next.seasonNumber.toString().padStart(2, '0')}" +
                "E${next.number.toString().padStart(2, '0')}" +
                (next.title?.let { " · $it" } ?: ""),
            posterUrl = next.poster ?: current.tvShow.poster.orEmpty(),
            videoTypeJson = DownloadController.serializeVideoType(episodeType),
            serverName = server.name,
            serverId = server.id,
            qualityLabel = "Auto",
            mimeType = video.type,
            streamUrl = video.source,
            headers = video.headers.orEmpty(),
            subtitleUrls = video.subtitles.map { it.label to it.file },
            smartEnqueued = true,
        )
        Log.i(TAG, "smart-downloaded next episode S${next.seasonNumber}E${next.number}")
    }

    private data class NextEpisode(
        val id: String,
        val number: Int,
        val seasonNumber: Int,
        val title: String?,
        val poster: String?,
        val overview: String?,
        val seasonTitle: String?,
    )

    /**
     * Next episode in the same season; if the current one was the last, the
     * first episode of the next season. Returns null at the end of a show.
     */
    private suspend fun findNextEpisode(
        context: Context,
        current: Video.Type.Episode,
    ): NextEpisode? {
        val provider = UserPreferences.currentProvider ?: return null
        val show = provider.getTvShow(current.tvShow.id)
        val seasons = show.seasons.orEmpty().sortedBy { it.number }
        if (seasons.isEmpty()) return null

        val currentSeason = seasons.firstOrNull { it.number == current.season.number }
            ?: return null
        val episodes = runCatching { provider.getEpisodesBySeason(currentSeason.id) }
            .getOrNull()
            .orEmpty()
            .sortedBy { it.number }

        episodes.firstOrNull { it.number == current.number + 1 }?.let { ep ->
            return NextEpisode(
                id = ep.id,
                number = ep.number,
                seasonNumber = current.season.number,
                title = ep.title,
                poster = ep.poster,
                overview = ep.overview,
                seasonTitle = currentSeason.title,
            )
        }

        // Season boundary: first episode of the next season.
        val nextSeason = seasons.firstOrNull { it.number > current.season.number } ?: return null
        val first = runCatching { provider.getEpisodesBySeason(nextSeason.id) }
            .getOrNull()
            .orEmpty()
            .minByOrNull { it.number }
            ?: return null
        return NextEpisode(
            id = first.id,
            number = first.number,
            seasonNumber = nextSeason.number,
            title = first.title,
            poster = first.poster,
            overview = first.overview,
            seasonTitle = nextSeason.title,
        )
    }

    private fun videoTypeIdEquals(videoTypeJson: String, id: String): Boolean {
        if (videoTypeJson.isBlank() || id.isBlank()) return false
        return runCatching {
            JSONObject(videoTypeJson).optString("id") == id
        }.getOrDefault(false)
    }
}
