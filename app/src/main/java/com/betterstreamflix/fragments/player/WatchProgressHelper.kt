package com.betterstreamflix.fragments.player

import androidx.media3.exoplayer.ExoPlayer
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.Season
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.models.Video
import com.betterstreamflix.utils.UserPreferences
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
}
