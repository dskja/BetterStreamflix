package com.betterstreamflix.ui

import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.download.DownloadEnqueueHelper
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.providers.Provider
import com.betterstreamflix.utils.ArtworkRepair
import com.betterstreamflix.utils.UserDataCache
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.toActivity
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Shared mutation engine for show options (movie / TV show / episode).
 * Used by [ShowOptionsDialog] / Compose hosts so behavior stays in sync.
 */
object ShowOptionsActions {

    fun ensureProvider(item: AppAdapter.Item) {
        val providerName = when (item) {
            is Movie -> item.providerName
            is TvShow -> item.providerName
            is Episode -> item.tvShow?.providerName
            else -> null
        }
        if (!providerName.isNullOrBlank() && providerName != UserPreferences.currentProvider?.name) {
            Provider.providers.keys.find { it.name == providerName }?.let {
                UserPreferences.currentProvider = it
            }
        }
    }

    private fun withProvider(item: AppAdapter.Item, action: () -> Unit) {
        ensureProvider(item)
        action()
    }

    private fun activityScope(context: Context): CoroutineScope? =
        context.toActivity()?.lifecycleScope

    private fun database(context: Context): AppDatabase = AppDatabase.getInstance(context)

    // region Movie

    fun toggleFavorite(context: Context, movie: Movie, scope: CoroutineScope? = activityScope(context)) {
        val freshMovie = database(context).movieDao().getById(movie.id) ?: movie
        withProvider(freshMovie) {
            val provider = UserPreferences.currentProvider ?: return@withProvider
            (scope ?: return@withProvider).launch(Dispatchers.IO) {
                val dao = database(context).movieDao()
                val current = dao.getById(movie.id)?.isFavorite ?: false
                val newValue = !current
                val resolvedMovie = ArtworkRepair.resolveMovieForFavorite(context, movie, newValue)
                dao.upsertFavorite(resolvedMovie, newValue)
                if (newValue) {
                    UserDataCache.addMovieToFavorites(
                        context,
                        provider,
                        resolvedMovie.copy().apply { isFavorite = true },
                    )
                } else {
                    UserDataCache.removeMovieFromFavorites(context, provider, freshMovie.id)
                }
            }
        }
    }

    fun toggleWatched(context: Context, movie: Movie) {
        val freshMovie = database(context).movieDao().getById(movie.id) ?: movie
        withProvider(freshMovie) {
            val provider = UserPreferences.currentProvider ?: return@withProvider
            val updatedMovie = freshMovie.copy().apply {
                merge(freshMovie)
                isWatched = !isWatched
                if (isWatched) {
                    watchedDate = Calendar.getInstance()
                    watchHistory = null
                } else {
                    watchedDate = null
                }
            }
            database(context).movieDao().save(updatedMovie)
            UserDataCache.syncMovieToCache(context, provider, updatedMovie)
            if (updatedMovie.isWatched) {
                UserDataCache.removeMovieFromContinueWatching(context, provider, freshMovie.id)
            }
        }
    }

    fun clearProgress(context: Context, movie: Movie) {
        val freshMovie = database(context).movieDao().getById(movie.id) ?: movie
        withProvider(freshMovie) {
            val provider = UserPreferences.currentProvider ?: return@withProvider
            val updatedMovie = freshMovie.copy().apply {
                merge(freshMovie)
                watchHistory = null
                isWatched = false
                watchedDate = null
            }
            database(context).movieDao().save(updatedMovie)
            UserDataCache.syncMovieToCache(context, provider, updatedMovie)
            UserDataCache.removeMovieFromContinueWatching(context, provider, freshMovie.id)
        }
    }

    fun removeRecentlyWatched(context: Context, movie: Movie) {
        val freshMovie = database(context).movieDao().getById(movie.id) ?: movie
        database(context).movieDao().clearRecentlyWatched(freshMovie.id)
    }

    fun enqueueDownload(context: Context, movie: Movie, scope: CoroutineScope? = activityScope(context)) {
        withProvider(movie) {
            (scope ?: return@withProvider).launch {
                DownloadEnqueueHelper.enqueueMovie(context, movie)
            }
        }
    }

    // endregion

    // region TvShow

    fun toggleFavorite(context: Context, tvShow: TvShow, scope: CoroutineScope? = activityScope(context)) {
        val freshTvShow = database(context).tvShowDao().getById(tvShow.id) ?: tvShow
        withProvider(freshTvShow) {
            val provider = UserPreferences.currentProvider ?: return@withProvider
            (scope ?: return@withProvider).launch(Dispatchers.IO) {
                val dao = database(context).tvShowDao()
                val current = dao.getById(tvShow.id)?.isFavorite ?: false
                val newValue = !current
                val resolvedTvShow = ArtworkRepair.resolveTvShowForFavorite(context, tvShow, newValue)
                dao.upsertFavorite(resolvedTvShow, newValue)
                if (newValue) {
                    UserDataCache.syncTvShowToCache(
                        context,
                        provider,
                        resolvedTvShow.copy().apply { isFavorite = true },
                    )
                } else {
                    UserDataCache.removeTvShowFromFavorites(context, provider, freshTvShow.id)
                }
            }
        }
    }

    fun clearWatchingProgress(context: Context, tvShow: TvShow) {
        val freshTvShow = database(context).tvShowDao().getById(tvShow.id) ?: tvShow
        withProvider(freshTvShow) {
            val provider = UserPreferences.currentProvider ?: return@withProvider
            database(context).tvShowDao().setWatching(freshTvShow.id, false)
            val episodeDao = database(context).episodeDao()
            val episodes = episodeDao.getEpisodesByTvShowId(freshTvShow.id)
            for (ep in episodes) {
                if (ep.watchHistory != null) {
                    val updatedEp = ep.copy().apply {
                        merge(ep)
                        watchHistory = null
                        isWatched = false
                        watchedDate = null
                    }
                    episodeDao.save(updatedEp)
                    UserDataCache.removeEpisodeFromContinueWatching(context, provider, ep.id)
                }
            }
        }
    }

    fun removeRecentlyWatched(context: Context, tvShow: TvShow) {
        val freshTvShow = database(context).tvShowDao().getById(tvShow.id) ?: tvShow
        database(context).tvShowDao().clearRecentlyWatched(freshTvShow.id)
    }

    // endregion

    // region Episode

    fun toggleWatched(context: Context, episode: Episode) {
        withProvider(episode) {
            val currentProvider = UserPreferences.currentProvider ?: return@withProvider
            val updatedEpisode = episode.copy().apply {
                merge(episode)
                isWatched = !isWatched
                if (isWatched) {
                    watchedDate = Calendar.getInstance()
                    watchHistory = null
                } else {
                    watchedDate = null
                }
            }
            database(context).episodeDao().save(updatedEpisode)
            UserDataCache.syncEpisodeToCache(context, currentProvider, updatedEpisode)

            episode.tvShow?.let { tvShow ->
                val episodeDao = database(context).episodeDao()
                val isStillWatching = episodeDao.hasAnyWatchHistoryForTvShow(tvShow.id)
                if (updatedEpisode.isWatched && !isStillWatching) {
                    database(context).tvShowDao().setWatching(tvShow.id, false)
                    UserDataCache.removeEpisodeFromContinueWatching(context, currentProvider, episode.id)
                }
            }
            if (updatedEpisode.isWatched) {
                UserDataCache.removeEpisodeFromContinueWatching(context, currentProvider, episode.id)
            }
        }
    }

    fun markAllPreviousWatched(context: Context, episode: Episode) {
        withProvider(episode) {
            val episodeDao = database(context).episodeDao()
            val episodeNumber = episode.number
            val tvShowId = episode.tvShow?.id ?: return@withProvider
            val allEpisodes = episodeDao
                .getEpisodesByTvShowIdAndSeason(tvShowId, episode.season?.id)
                .filter { it.number <= episodeNumber }
            val targetState = !episode.isWatched
            val now = Calendar.getInstance()
            val currentProvider = UserPreferences.currentProvider ?: return@withProvider

            for (ep in allEpisodes) {
                if (ep.isWatched != targetState) {
                    val updatedEp = ep.copy().apply {
                        merge(ep)
                        isWatched = targetState
                        watchedDate = if (targetState) now else null
                        watchHistory = if (targetState) null else watchHistory
                    }
                    episodeDao.save(updatedEp)
                    UserDataCache.syncEpisodeToCache(context, currentProvider, updatedEp)
                }
            }

            if (targetState) {
                episode.tvShow?.let { tvShow ->
                    if (!episodeDao.hasAnyWatchHistoryForTvShow(tvShow.id)) {
                        database(context).tvShowDao().setWatching(tvShow.id, false)
                        UserDataCache.removeEpisodeFromContinueWatching(context, currentProvider, episode.id)
                    }
                }
                UserDataCache.removeEpisodeFromContinueWatching(context, currentProvider, episode.id)
            }
            if (!targetState) {
                episode.tvShow?.let { tvShow ->
                    database(context).tvShowDao().setWatching(tvShow.id, true)
                }
            }
        }
    }

    fun clearProgress(context: Context, episode: Episode) {
        withProvider(episode) {
            val provider = UserPreferences.currentProvider ?: return@withProvider
            val updatedEpisode = episode.copy().apply {
                merge(episode)
                watchHistory = null
                isWatched = false
                watchedDate = null
            }
            database(context).episodeDao().save(updatedEpisode)
            episode.tvShow?.let { tvShow ->
                database(context).tvShowDao().setWatching(tvShow.id, false)
            }
            UserDataCache.removeEpisodeFromContinueWatching(context, provider, episode.id)
        }
    }

    fun enqueueDownload(context: Context, episode: Episode, scope: CoroutineScope? = activityScope(context)) {
        withProvider(episode) {
            (scope ?: return@withProvider).launch {
                DownloadEnqueueHelper.enqueueEpisode(context, episode)
            }
        }
    }

    // endregion
}
