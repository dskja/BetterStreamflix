package com.streamflixreborn.streamflix.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.WatchItem
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.ui.UserDataNotifier
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object UserDataCache {

    private val gson = Gson()
    private val memoryCache = ConcurrentHashMap<String, UserData>()

    data class UserData(
        val favoritesMovies: List<CachedMovie> = emptyList(),
        val favoritesTvShows: List<CachedTvShow> = emptyList(),
        val continueWatchingMovies: List<CachedMovie> = emptyList(),
        val continueWatchingEpisodes: List<CachedEpisode> = emptyList(),
    )

    // -------------------------
    // CACHE FILE
    // -------------------------

    private fun cacheKey(provider: Provider): String {
        val baseUrlKey = provider.baseUrl.trim().trimEnd('/')
        return listOf(provider.name, baseUrlKey)
            .filter { it.isNotEmpty() }
            .joinToString("__")
    }

    private fun cacheFile(context: Context, cacheKey: String): File {
        val safeName = cacheKey.replace(Regex("[^a-zA-Z0-9._-]+"), "_")
        val file = File(context.cacheDir, "user-data-cache/$safeName.json")
        Log.d("CACHE_PATH", file.absolutePath)
        return file
    }

    // -------------------------
    // READ / WRITE
    // -------------------------

    fun read(context: Context, provider: Provider): UserData? {
        val key = cacheKey(provider)

        memoryCache[key]?.let { return it }

        val file = cacheFile(context, key)
        if (!file.exists()) return null

        return runCatching {
            gson.fromJson(file.readText(), UserData::class.java).also {
                memoryCache[key] = it
            }
        }.getOrNull()
    }

    fun write(context: Context, provider: Provider, newData: UserData) {
        val key = cacheKey(provider)
        val oldData = memoryCache[key]

        // ✅ prevent spam
        if (oldData == newData) return

        memoryCache[key] = newData

        runCatching {
            cacheFile(context, key).apply {
                parentFile?.mkdirs()
                writeText(gson.toJson(newData))
            }
        }

        UserDataNotifier.notifyChanged()
    }

    fun clear(context: Context, provider: Provider) {
        val key = cacheKey(provider)
        memoryCache.remove(key)
        cacheFile(context, key).delete()
    }

    // -------------------------
    // WRITE HELPERS (FIXED)
    // -------------------------

    fun writeMovies(context: Context, provider: Provider, movies: List<Movie>) {
        val current = read(context, provider) ?: UserData()

        val newData = current.copy(
            favoritesMovies = movies.filter { it.isFavorite }.map { it.toCached() },
            continueWatchingMovies = movies.filter { it.watchHistory != null }.map { it.toCached() }
        )

        write(context, provider, newData)
    }

    fun writeTvShows(context: Context, provider: Provider, tvShows: List<TvShow>) {
        val current = read(context, provider) ?: UserData()

        val newData = current.copy(
            favoritesTvShows = tvShows.filter { it.isFavorite }.map { it.toCached() }
        )

        write(context, provider, newData)
    }

    fun writeEpisodes(context: Context, provider: Provider, episodes: List<Episode>) {
        val current = read(context, provider) ?: UserData()

        val newData = current.copy(
            continueWatchingEpisodes = episodes.filter { it.watchHistory != null }.map { it.toCached() }
        )

        write(context, provider, newData)
    }

    // -------------------------
    // MOVIES
    // -------------------------

    fun removeMovieFromContinueWatching(context: Context, provider: Provider, id: String) {
        val current = read(context, provider) ?: return

        write(context, provider, current.copy(
            continueWatchingMovies = current.continueWatchingMovies.filter { it.id != id }
        ))
        UserDataNotifier.notifyChanged()
    }

    fun addMovieToContinueWatching(context: Context, provider: Provider, movie: Movie) {
        val current = read(context, provider) ?: UserData()

        write(context, provider, current.copy(
            continueWatchingMovies = (current.continueWatchingMovies + movie.toCached())
                .distinctBy { it.id }
        ))
        UserDataNotifier.notifyChanged()
    }

    fun removeMovieFromFavorites(context: Context, provider: Provider, id: String) {
        val current = read(context, provider) ?: return

        write(context, provider, current.copy(
            favoritesMovies = current.favoritesMovies.filter { it.id != id }
        ))
        UserDataNotifier.notifyChanged()
    }

    fun addMovieToFavorites(context: Context, provider: Provider, movie: Movie) {
        val current = read(context, provider) ?: UserData()

        write(context, provider, current.copy(
            favoritesMovies = (current.favoritesMovies + movie.toCached())
                .distinctBy { it.id }
        ))
        UserDataNotifier.notifyChanged()
    }

    // -------------------------
    // EPISODES
    // -------------------------

    fun removeEpisodeFromContinueWatching(context: Context, provider: Provider, id: String) {
        val current = read(context, provider) ?: return

        write(context, provider, current.copy(
            continueWatchingEpisodes = current.continueWatchingEpisodes.filter { it.id != id }
        ))
        UserDataNotifier.notifyChanged()
    }

    fun addEpisodeToContinueWatching(context: Context, provider: Provider, episode: Episode) {
        val current = read(context, provider) ?: UserData()

        write(context, provider, current.copy(
            continueWatchingEpisodes = (current.continueWatchingEpisodes + episode.toCached())
                .distinctBy { it.id }
        ))
        UserDataNotifier.notifyChanged()
    }

    // -------------------------
    // TV SHOWS
    // -------------------------

    fun removeTvShowFromFavorites(context: Context, provider: Provider, id: String) {
        val current = read(context, provider) ?: return

        write(context, provider, current.copy(
            favoritesTvShows = current.favoritesTvShows.filter { it.id != id }
        ))
        UserDataNotifier.notifyChanged()
    }

    fun addTvShowToFavorites(context: Context, provider: Provider, tvShow: TvShow) {
        val current = read(context, provider) ?: UserData()

        write(context, provider, current.copy(
            favoritesTvShows = (current.favoritesTvShows + tvShow.toCached())
                .distinctBy { it.id }
        ))
        UserDataNotifier.notifyChanged()
    }




    data class CachedMovie(
        val id: String,
        val title: String,
        val overview: String? = null,
        val released: String? = null,
        val runtime: Int? = null,
        val trailer: String? = null,
        val quality: String? = null,
        val rating: Double? = null,
        val poster: String? = null,
        val banner: String? = null,
        val isFavorite: Boolean = false,
        val isWatched: Boolean = false,
        val lastEngagementTimeUtcMillis: Long? = null,
    )

    data class CachedTvShow(
        val id: String,
        val title: String,
        val overview: String? = null,
        val released: String? = null,
        val runtime: Int? = null,
        val trailer: String? = null,
        val quality: String? = null,
        val rating: Double? = null,
        val poster: String? = null,
        val banner: String? = null,
        val isFavorite: Boolean = false,
    )

    data class CachedEpisode(
        val id: String,
        val number: Int,
        val title: String? = null,
        val released: String? = null,
        val poster: String? = null,
        val overview: String? = null,
        val isWatched: Boolean = false,
        val lastEngagementTimeUtcMillis: Long? = null,
        val lastPlaybackPositionMillis: Long? = null,
        val tvShowId: String? = null,
        val tvShowTitle: String? = null,
        val tvShowPoster: String? = null,
        val tvShowBanner: String? = null,
        val seasonId: String? = null,
        val seasonNumber: Int? = null,
        val seasonTitle: String? = null,
        val seasonPoster: String? = null,
    )


    fun CachedMovie.toMovie() = Movie(
        id = id,
        title = title,
        overview = overview,
        released = released,
        runtime = runtime,
        trailer = trailer,
        quality = quality,
        rating = rating,
        poster = poster,
        banner = banner,
    ).apply {
        isFavorite = this@toMovie.isFavorite
        isWatched = this@toMovie.isWatched
        if (this@toMovie.lastEngagementTimeUtcMillis != null) {
            watchHistory = WatchItem.WatchHistory(
                lastEngagementTimeUtcMillis = this@toMovie.lastEngagementTimeUtcMillis,
                lastPlaybackPositionMillis = 0,
                durationMillis = 0,
            )
        }
    }

    fun CachedTvShow.toTvShow() = TvShow(
        id = id,
        title = title,
        overview = overview,
        released = released,
        runtime = runtime,
        trailer = trailer,
        quality = quality,
        rating = rating,
        poster = poster,
        banner = banner,
    ).apply {
        isFavorite = this@toTvShow.isFavorite
    }

    fun CachedEpisode.toEpisode() = Episode(
        id = id,
        number = number,
        title = title,
        released = released,
        poster = poster,
        overview = overview,
    ).apply {
        isWatched = this@toEpisode.isWatched
        if (this@toEpisode.lastEngagementTimeUtcMillis != null) {
            watchHistory = WatchItem.WatchHistory(
                lastEngagementTimeUtcMillis = this@toEpisode.lastEngagementTimeUtcMillis,
                lastPlaybackPositionMillis = this@toEpisode.lastPlaybackPositionMillis ?: 0,
                durationMillis = 0,
            )
        }
        tvShow = this@toEpisode.tvShowId?.let {
            TvShow(
                id = it,
                title = this@toEpisode.tvShowTitle.orEmpty(),
                poster = this@toEpisode.tvShowPoster,
                banner = this@toEpisode.tvShowBanner,
            )
        }
        season = this@toEpisode.seasonId?.let {
            Season(
                id = it,
                number = this@toEpisode.seasonNumber ?: 0,
                title = this@toEpisode.seasonTitle.orEmpty(),
                poster = this@toEpisode.seasonPoster,
            )
        }
    }
    fun Movie.toCached() = UserDataCache.CachedMovie(
        id = id,
        title = title,
        overview = overview,
        released = released?.format("yyyy-MM-dd"),
        runtime = runtime,
        trailer = trailer,
        quality = quality,
        rating = rating,
        poster = poster,
        banner = banner,
        isFavorite = isFavorite,
        isWatched = isWatched,
        lastEngagementTimeUtcMillis = watchHistory?.lastEngagementTimeUtcMillis
    )
    fun TvShow.toCached() = UserDataCache.CachedTvShow(
        id = id,
        title = title,
        overview = overview,
        released = released?.format("yyyy-MM-dd"),
        runtime = runtime,
        trailer = trailer,
        quality = quality,
        rating = rating,
        poster = poster,
        banner = banner,
        isFavorite = isFavorite
    )
    fun Episode.toCached() = UserDataCache.CachedEpisode(
        id = id,
        number = number,
        title = title,
        released = released?.format("yyyy-MM-dd"),
        poster = poster,
        overview = overview,
        isWatched = isWatched,
        lastEngagementTimeUtcMillis = watchHistory?.lastEngagementTimeUtcMillis,
        lastPlaybackPositionMillis = watchHistory?.lastPlaybackPositionMillis,

        tvShowId = tvShow?.id,
        tvShowTitle = tvShow?.title,
        tvShowPoster = tvShow?.poster,
        tvShowBanner = tvShow?.banner,

        seasonId = season?.id,
        seasonNumber = season?.number,
        seasonTitle = season?.title,
        seasonPoster = season?.poster,
    )
}