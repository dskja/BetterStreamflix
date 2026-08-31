package com.betterstreamflix.fragments.player

import android.content.Context
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.Season
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.models.Video
import com.betterstreamflix.utils.DeepLinkHandler
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.WatchNextUtils
import kotlin.time.Duration.Companion.seconds

/**
 * Shared logic for recording watch progress and determining playback state.
 * Used by both PlayerMobileFragment and PlayerTvFragment.
 */
object WatchProgressHelper {

    /**
     * Check if the player has started playing (past 0.5% or 20 seconds).
     */
    fun ExoPlayer.hasStarted(): Boolean {
        return (currentPosition > (duration * 0.005) || currentPosition > 20.seconds.inWholeMilliseconds)
    }

    /**
     * Check if the player has finished (past 90% of duration).
     */
    fun ExoPlayer.hasFinished(): Boolean {
        return (currentPosition > (duration * 0.90))
    }

    /**
     * Check if the player has really finished (within autoplay buffer of end).
     */
    fun ExoPlayer.hasReallyFinished(): Boolean {
        return duration > 0 &&
            currentPosition >= (duration - UserPreferences.autoplayBuffer * 1000)
    }

    /**
     * Record the start of watching a video in the database.
     * Creates movie/TV show/episode records if they don't exist.
     */
    fun recordRecentlyWatchedStart(
        database: AppDatabase,
        videoType: Video.Type,
    ) {
        val playedAtMillis = System.currentTimeMillis()
        when (videoType) {
            is Video.Type.Movie -> {
                if (database.movieDao().markRecentlyWatched(videoType.id, playedAtMillis) == 0) {
                    database.movieDao().insert(
                        Movie(
                            id = videoType.id,
                            title = videoType.title,
                            released = videoType.releaseDate,
                            poster = videoType.poster,
                            imdbId = videoType.imdbId,
                        ).apply {
                            lastPlayedAtMillis = playedAtMillis
                        }
                    )
                }
            }

            is Video.Type.Episode -> {
                val storedTvShow = database.tvShowDao().getById(videoType.tvShow.id)
                    ?: TvShow(
                        id = videoType.tvShow.id,
                        title = videoType.tvShow.title,
                        released = videoType.tvShow.releaseDate,
                        poster = videoType.tvShow.poster,
                        banner = videoType.tvShow.banner,
                        imdbId = videoType.tvShow.imdbId,
                    ).apply {
                        lastPlayedAtMillis = playedAtMillis
                        lastPlayedEpisodeId = videoType.id
                        database.tvShowDao().insert(this)
                    }

                if (database.episodeDao().getById(videoType.id) == null) {
                    database.episodeDao().insert(
                        Episode(
                            id = videoType.id,
                            number = videoType.number,
                            title = videoType.title,
                            poster = videoType.poster,
                            overview = videoType.overview,
                            tvShow = storedTvShow,
                            season = Season(
                                number = videoType.season.number,
                                title = videoType.season.title.orEmpty(),
                            ),
                        )
                    )
                }
                database.tvShowDao().markRecentlyWatched(
                    id = videoType.tvShow.id,
                    episodeId = videoType.id,
                    playedAtMillis = playedAtMillis,
                )
            }
        }
    }

    /**
     * Publish or update Android TV Watch Next for continue-watching.
     * Safe no-op on devices without TV provider permissions / support.
     */
    fun upsertWatchNext(
        context: Context,
        videoType: Video.Type,
        positionMillis: Long,
        durationMillis: Long,
        finished: Boolean,
    ) {
        runCatching {
            val providerName = UserPreferences.currentProvider?.name ?: return
            val contentId = when (videoType) {
                is Video.Type.Movie -> videoType.id
                is Video.Type.Episode -> videoType.id
            }
            val existing = WatchNextUtils.getProgram(context, contentId)
            if (finished) {
                existing?.let { WatchNextUtils.deleteProgramById(context, it.id) }
                return
            }
            if (durationMillis <= 0L) return

            val title = when (videoType) {
                is Video.Type.Movie -> videoType.title
                is Video.Type.Episode -> videoType.tvShow.title
            }
            val poster = when (videoType) {
                is Video.Type.Movie -> videoType.poster
                is Video.Type.Episode -> videoType.poster ?: videoType.tvShow.poster
            }
            val intentUri = when (videoType) {
                is Video.Type.Movie -> DeepLinkHandler.movieUri(videoType.id)
                is Video.Type.Episode -> DeepLinkHandler.tvShowUri(videoType.tvShow.id)
            }

            val builder = WatchNextProgram.Builder().apply {
                setType(
                    when (videoType) {
                        is Video.Type.Movie -> androidx.tvprovider.media.tv.TvContractCompat.PreviewProgramColumns.TYPE_MOVIE
                        is Video.Type.Episode -> androidx.tvprovider.media.tv.TvContractCompat.PreviewProgramColumns.TYPE_TV_EPISODE
                    }
                )
                setWatchNextType(androidx.tvprovider.media.tv.TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                setTitle(title)
                setContentId(contentId)
                setInternalProviderId(providerName)
                setIntentUri(intentUri)
                setDurationMillis(durationMillis.toInt().coerceAtLeast(0))
                setLastPlaybackPositionMillis(positionMillis.toInt().coerceAtLeast(0))
                if (!poster.isNullOrBlank()) {
                    setPosterArtUri(android.net.Uri.parse(poster))
                }
            }
            val program = builder.build()
            if (existing != null) {
                WatchNextUtils.updateProgram(context, existing.id, program)
            } else {
                WatchNextUtils.insert(context, program)
            }
        }.onFailure { e ->
            Log.d("WatchProgressHelper", "Watch Next upsert skipped: ${e.message}")
        }
    }
}
