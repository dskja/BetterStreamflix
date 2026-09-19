package com.dskja.betterstreamflix.cast

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.utils.EpisodeManager
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves the next episode stream and enqueues it on [CastPlaybackHub] for seamless Cast autoplay.
 */
object CastQueueCoordinator {
    private const val TAG = "CastQueue"

    data class NextCastItem(
        val mediaItem: MediaItem,
        val episode: Video.Type.Episode,
        val video: Video,
        val server: Video.Server,
    )

    /**
     * Prefetch next episode for Cast. Safe to call repeatedly — clears stale queue first.
     */
    suspend fun prepareNextEpisodeQueue(
        provider: Provider?,
        currentVideoType: Video.Type,
        subtitleConfigurations: List<MediaItem.SubtitleConfiguration> = emptyList(),
        headers: Map<String, String> = emptyMap(),
    ): NextCastItem? = withContext(Dispatchers.IO) {
        if (currentVideoType !is Video.Type.Episode) return@withContext null
        if (!UserPreferences.autoplay && !UserPreferences.castQueueNextEpisode) return@withContext null
        val next = EpisodeManager.peekNextEpisode() ?: return@withContext null
        val activeProvider = provider ?: UserPreferences.currentProvider ?: return@withContext null
        runCatching {
            val nextType = Video.Type.Episode(
                id = next.id,
                number = next.number,
                title = next.title,
                poster = next.poster,
                overview = next.overview,
                tvShow = Video.Type.Episode.TvShow(
                    id = next.tvShow.id,
                    title = next.tvShow.title,
                    poster = next.tvShow.poster,
                    banner = next.tvShow.banner,
                    releaseDate = next.tvShow.releaseDate,
                    imdbId = next.tvShow.imdbId,
                ),
                season = Video.Type.Episode.Season(
                    number = next.season.number,
                    title = next.season.title,
                ),
            )
            val servers = activeProvider.getServers(next.id, nextType)
            val server = servers.firstOrNull() ?: return@runCatching null
            val video = activeProvider.getVideo(server)
            val title = next.tvShow.title
            val subtitle = "S${next.season.number} E${next.number}" +
                (next.title?.takeIf { it.isNotBlank() }?.let { "  •  $it" } ?: "")
            val metadata = CastMediaFactory.buildMetadata(
                title = title,
                subtitle = subtitle,
                videoType = nextType,
                serverId = server.id,
            )
            val mergedHeaders = headers.ifEmpty { video.headers.orEmpty() }
            val item = CastMediaFactory.queueItemForCast(
                source = video.source,
                mimeType = video.type,
                headers = mergedHeaders,
                metadata = metadata,
                subtitleConfigurations = subtitleConfigurations,
            )
            NextCastItem(mediaItem = item, episode = nextType, video = video, server = server)
        }.onFailure {
            Log.w(TAG, "Cast next enqueue failed: ${it.message}")
        }.getOrNull()
    }

    fun enqueueExclusive(item: MediaItem) {
        CastPlaybackHub.clearQueue()
        CastPlaybackHub.enqueue(item)
        Log.i(TAG, "Cast queue size=${CastPlaybackHub.queuedCount()}")
    }
}
