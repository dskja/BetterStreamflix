package com.dskja.betterstreamflix.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory LRU-ish cache for TMDb enrichment / details to cut scraper fan-out.
 * Cleared when the API key or enable flag changes via [clear].
 */
object TmdbCache {
    private const val MAX_ENTRIES = 256
    /** Sentinel for a cached miss (ConcurrentHashMap forbids null values). */
    private const val MISS = -1
    private val movieDetails = ConcurrentHashMap<String, CachedMovie>()
    private val tvDetails = ConcurrentHashMap<String, CachedTv>()
    private val searchMovie = ConcurrentHashMap<String, Int>()
    private val searchTv = ConcurrentHashMap<String, Int>()
    private val findImdbMovie = ConcurrentHashMap<String, Int>()
    private val findImdbTv = ConcurrentHashMap<String, Int>()

    data class CachedMovie(
        val id: Int,
        val title: String,
        val overview: String?,
        val released: String?,
        val runtime: Int?,
        val trailer: String?,
        val rating: Double?,
        val poster: String?,
        val banner: String?,
        val imdbId: String?,
        val genres: List<Pair<String, String>>,
        val cast: List<Triple<String, String, String?>>,
    )

    data class CachedTv(
        val id: Int,
        val title: String,
        val overview: String?,
        val released: String?,
        val trailer: String?,
        val rating: Double?,
        val poster: String?,
        val banner: String?,
        val imdbId: String?,
        val seasons: List<SeasonCache>,
        val genres: List<Pair<String, String>>,
        val cast: List<Triple<String, String, String?>>,
    )

    data class SeasonCache(
        val number: Int,
        val title: String?,
        val poster: String?,
    )

    fun clear() {
        movieDetails.clear()
        tvDetails.clear()
        searchMovie.clear()
        searchTv.clear()
        findImdbMovie.clear()
        findImdbTv.clear()
    }

    fun getMovie(id: Int): CachedMovie? = movieDetails[id.toString()]

    fun putMovie(cached: CachedMovie) {
        trim(movieDetails)
        movieDetails[cached.id.toString()] = cached
    }

    fun getTv(id: Int): CachedTv? = tvDetails[id.toString()]

    fun putTv(cached: CachedTv) {
        trim(tvDetails)
        tvDetails[cached.id.toString()] = cached
    }

    fun getSearchMovieId(key: String): Int? = searchMovie[key]?.takeUnless { it == MISS }

    fun hasSearchMovie(key: String): Boolean = searchMovie.containsKey(key)

    fun putSearchMovieId(key: String, id: Int?) {
        trim(searchMovie)
        searchMovie[key] = id ?: MISS
    }

    fun getSearchTvId(key: String): Int? = searchTv[key]?.takeUnless { it == MISS }

    fun hasSearchTv(key: String): Boolean = searchTv.containsKey(key)

    fun putSearchTvId(key: String, id: Int?) {
        trim(searchTv)
        searchTv[key] = id ?: MISS
    }

    fun getFindImdbMovie(imdb: String): Int? =
        findImdbMovie[imdb]?.takeUnless { it == MISS }

    fun hasFindImdbMovie(imdb: String): Boolean = findImdbMovie.containsKey(imdb)

    fun putFindImdbMovie(imdb: String, id: Int?) {
        trim(findImdbMovie)
        findImdbMovie[imdb] = id ?: MISS
    }

    fun getFindImdbTv(imdb: String): Int? =
        findImdbTv[imdb]?.takeUnless { it == MISS }

    fun hasFindImdbTv(imdb: String): Boolean = findImdbTv.containsKey(imdb)

    fun putFindImdbTv(imdb: String, id: Int?) {
        trim(findImdbTv)
        findImdbTv[imdb] = id ?: MISS
    }

    private fun <K, V> trim(map: ConcurrentHashMap<K, V>) {
        if (map.size < MAX_ENTRIES) return
        val keys = map.keys.take(map.size / 4)
        keys.forEach { map.remove(it) }
    }
}
