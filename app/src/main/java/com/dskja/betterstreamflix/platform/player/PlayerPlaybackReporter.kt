package com.dskja.betterstreamflix.platform.player

import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.platform.jellyfin.JellyfinPlaybackReporter
import com.dskja.betterstreamflix.platform.plex.PlexPlaybackReporter
import com.dskja.betterstreamflix.platform.simkl.SimklSyncHooks
import com.dskja.betterstreamflix.platform.trakt.TraktEpisodeRef
import com.dskja.betterstreamflix.platform.trakt.TraktIds
import com.dskja.betterstreamflix.platform.trakt.TraktSyncHooks
import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.utils.UserPreferences

/**
 * Shared playback progress fan-out for Trakt + Simkl + self-host servers (mobile + TV).
 */
object PlayerPlaybackReporter {
    fun report(
        videoType: Video.Type,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        provider: Provider? = UserPreferences.currentProvider,
        itemId: String? = null,
    ) {
        if (durationMs <= 0L) return
        reportTrakt(videoType, positionMs, durationMs, isPlaying)
        reportSimkl(videoType, positionMs, durationMs, isPlaying)
        val id = itemId ?: when (videoType) {
            is Video.Type.Movie -> videoType.id
            is Video.Type.Episode -> videoType.id
        }
        when (provider?.name) {
            "Jellyfin" -> JellyfinPlaybackReporter.report(id, positionMs, durationMs, isPlaying)
            "Plex" -> PlexPlaybackReporter.report(id, positionMs, durationMs, isPlaying)
        }
    }

    fun resetSession() {
        TraktSyncHooks.resetSession()
        SimklSyncHooks.resetSession()
    }

    private fun reportTrakt(
        videoType: Video.Type,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
    ) {
        when (videoType) {
            is Video.Type.Movie -> {
                TraktSyncHooks.onPlaybackProgress(
                    imdbId = videoType.imdbId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    mediaKey = "movie:${videoType.imdbId ?: videoType.id}",
                )
            }
            is Video.Type.Episode -> {
                val showImdb = videoType.tvShow.imdbId
                val ref = if (!showImdb.isNullOrBlank()) {
                    TraktEpisodeRef(
                        showIds = TraktIds(imdb = showImdb),
                        season = videoType.season.number,
                        number = videoType.number,
                    )
                } else null
                TraktSyncHooks.onPlaybackProgress(
                    imdbId = showImdb,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    mediaKey = "ep:${showImdb ?: videoType.id}:S${videoType.season.number}E${videoType.number}",
                    episodeRef = ref,
                )
            }
        }
    }

    private fun reportSimkl(
        videoType: Video.Type,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
    ) {
        when (videoType) {
            is Video.Type.Movie -> {
                SimklSyncHooks.onPlaybackProgress(
                    imdbId = videoType.imdbId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    mediaKey = "simkl:movie:${videoType.imdbId ?: videoType.id}",
                )
            }
            is Video.Type.Episode -> {
                SimklSyncHooks.onPlaybackProgress(
                    imdbId = videoType.tvShow.imdbId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    mediaKey = "simkl:ep:${videoType.tvShow.imdbId ?: videoType.id}:S${videoType.season.number}E${videoType.number}",
                    season = videoType.season.number,
                    episode = videoType.number,
                )
            }
        }
    }
}
