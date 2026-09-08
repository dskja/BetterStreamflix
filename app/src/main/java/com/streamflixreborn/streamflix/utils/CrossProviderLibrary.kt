package com.streamflixreborn.streamflix.utils

import android.content.Context
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.providers.Provider
import kotlinx.coroutines.flow.first

/**
 * Loads favorites / continue watching / recently watched either from the current
 * provider DB only, or aggregated across every provider DB on disk — controlled by
 * [UserPreferences.libraryScope].
 *
 * Items are stamped with [Movie.providerName] / [TvShow.providerName] so the UI can
 * switch provider before opening them.
 */
object CrossProviderLibrary {

    data class HomeHistory(
        val continueWatching: List<AppAdapter.Item>,
        val recentlyWatched: List<AppAdapter.Item>,
        val favoriteMovies: List<Movie>,
        val favoriteTvShows: List<TvShow>,
    )

    suspend fun loadHomeHistory(context: Context): HomeHistory {
        val continueWatching = mutableListOf<AppAdapter.Item>()
        val recentlyWatched = mutableListOf<AppAdapter.Item>()
        val favoriteMovies = mutableListOf<Movie>()
        val favoriteTvShows = mutableListOf<TvShow>()

        forEachScopedProviderDb(context) { provider, db ->
            favoriteMovies += db.movieDao().getFavorites().first()
                .map { it.withProvider(provider.name) }
            favoriteTvShows += db.tvShowDao().getFavorites().first()
                .map { it.withProvider(provider.name) }

            continueWatching += db.movieDao().getWatchingMovies().first()
                .map { it.withProvider(provider.name) }

            val watchingEpisodes = db.episodeDao().getWatchingEpisodes().first()
            val nextEpisodes = db.episodeDao().getNextEpisodesToWatch().first()
            val tvShowsMap = db.tvShowDao().getAll().first().associateBy { it.id }
            val allEpisodes = (watchingEpisodes + nextEpisodes).distinctBy { it.id }
            val seasonIds = allEpisodes.mapNotNull { it.season?.id }.distinct()
            val seasonsMap = if (seasonIds.isEmpty()) {
                emptyMap()
            } else {
                db.seasonDao().getByIds(seasonIds).associateBy { it.id }
            }

            continueWatching += allEpisodes.map { episode ->
                episode.copy(
                    tvShow = (episode.tvShow?.id?.let { tvShowsMap[it] } ?: episode.tvShow)
                        ?.withProvider(provider.name),
                    season = episode.season?.id?.let { seasonsMap[it] } ?: episode.season,
                ).apply { merge(episode) }
            }

            recentlyWatched += db.movieDao().getRecentlyWatched().first()
                .map { it.withProvider(provider.name) }

            val recentTvShows = db.tvShowDao().getRecentlyWatched().first()
            val episodeIds = recentTvShows.mapNotNull { it.lastPlayedEpisodeId }.distinct()
            val episodesById = if (episodeIds.isEmpty()) {
                emptyMap()
            } else {
                db.episodeDao().getByIds(episodeIds).associateBy { it.id }
            }
            recentlyWatched += recentTvShows.map { tvShow ->
                tvShow.withProvider(provider.name).apply {
                    lastPlayedEpisode = lastPlayedEpisodeId?.let(episodesById::get)
                }
            }
        }

        return HomeHistory(
            continueWatching = continueWatching
                .sortedByDescending { engagementMillis(it) }
                .distinctBy { continueWatchingKey(it) },
            recentlyWatched = recentlyWatched
                .sortedByDescending { recentlyWatchedMillis(it) }
                .distinctBy { libraryItemKey(it) },
            favoriteMovies = favoriteMovies.sortedByDescending { it.favoritedAtMillis ?: 0L },
            favoriteTvShows = favoriteTvShows.sortedByDescending { it.favoritedAtMillis ?: 0L },
        )
    }

    suspend fun loadFavoriteMovies(context: Context): List<Movie> =
        loadHomeHistory(context).favoriteMovies

    suspend fun loadFavoriteTvShows(context: Context): List<TvShow> =
        loadHomeHistory(context).favoriteTvShows

    private fun engagementMillis(item: AppAdapter.Item): Long = when (item) {
        is Movie -> item.watchHistory?.lastEngagementTimeUtcMillis
            ?: item.watchedDate?.timeInMillis
            ?: 0L
        is Episode -> item.watchHistory?.lastEngagementTimeUtcMillis
            ?: item.watchedDate?.timeInMillis
            ?: 0L
        else -> 0L
    }

    private fun recentlyWatchedMillis(item: AppAdapter.Item): Long = when (item) {
        is Movie -> item.lastPlayedAtMillis ?: 0L
        is TvShow -> item.lastPlayedAtMillis ?: 0L
        else -> 0L
    }

    private fun continueWatchingKey(item: AppAdapter.Item): String? = when (item) {
        is Episode -> "${item.tvShow?.providerName}:${item.tvShow?.id}"
        is Movie -> "${item.providerName}:${item.id}"
        else -> null
    }

    private fun libraryItemKey(item: AppAdapter.Item): String? = when (item) {
        is Movie -> "${item.providerName}:${item.id}"
        is TvShow -> "${item.providerName}:${item.id}"
        else -> null
    }

    private inline fun forEachScopedProviderDb(
        context: Context,
        block: (Provider, AppDatabase) -> Unit,
    ) {
        val appContext = context.applicationContext
        val current = UserPreferences.currentProvider
        val currentName = current?.name
        val currentDb = if (currentName != null) {
            runCatching { AppDatabase.getInstance(appContext) }.getOrNull()
        } else {
            null
        }

        val providers = when (UserPreferences.libraryScope) {
            UserPreferences.LibraryScope.PER_PROVIDER -> listOfNotNull(current)
            UserPreferences.LibraryScope.CROSS_PROVIDER -> Provider.allKnown()
                .distinctBy { it.name }
                .filter { provider ->
                    appContext.getDatabasePath(AppDatabase.databaseNameFor(provider.name)).exists()
                }
        }

        providers.forEach { provider ->
            if (provider.name == currentName && currentDb != null) {
                block(provider, currentDb)
            } else {
                val db = AppDatabase.getInstanceForProvider(provider.name, appContext)
                try {
                    block(provider, db)
                } finally {
                    db.close()
                }
            }
        }
    }

    private fun Movie.withProvider(name: String): Movie = copy().apply {
        merge(this@withProvider)
        providerName = name
    }

    private fun TvShow.withProvider(name: String): TvShow = copy().apply {
        merge(this@withProvider)
        providerName = name
        isWatching = this@withProvider.isWatching
    }
}
