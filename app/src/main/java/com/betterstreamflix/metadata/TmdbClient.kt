package com.betterstreamflix.metadata

import android.content.Context
import com.betterstreamflix.utils.Constants
import com.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * TMDB (The Movie Database) API client for fetching metadata.
 * Provides movie/TV show metadata, posters, backdrops, and cast info.
 */
object TmdbClient {

    private const val BASE_URL = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p"

    /**
     * Search for a movie by title.
     */
    suspend fun searchMovie(title: String, year: Int? = null, language: String = "en"): TmdbResult? {
        val query = buildSearchQuery("search/movie", mapOf(
            "query" to title,
            "language" to language,
            "page" to "1",
            "include_adult" to "false",
        ).let { if (year != null) it + ("year" to year.toString()) else it })
        return parseSearchResult(query)
    }

    /**
     * Search for a TV show by title.
     */
    suspend fun searchTv(title: String, year: Int? = null, language: String = "en"): TmdbResult? {
        val query = buildSearchQuery("search/tv", mapOf(
            "query" to title,
            "language" to language,
            "page" to "1",
        ).let { if (year != null) it + ("first_air_date_year" to year.toString()) else it })
        return parseSearchResult(query)
    }

    /**
     * Get movie details by TMDB ID.
     */
    suspend fun getMovieDetails(tmdbId: Int, language: String = "en"): TmdbMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/movie/$tmdbId?api_key=${getApiKey()}&language=$language"
                val response = fetchUrl(url)
                response?.let { parseMetadata(it, "movie") }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Get TV show details by TMDB ID.
     */
    suspend fun getTvDetails(tmdbId: Int, language: String = "en"): TmdbMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/tv/$tmdbId?api_key=${getApiKey()}&language=$language"
                val response = fetchUrl(url)
                response?.let { parseMetadata(it, "tv") }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Get the full image URL for a poster path.
     */
    fun getPosterUrl(path: String?, size: String = "w500"): String? {
        if (path == null) return null
        return "$IMAGE_BASE_URL/$size$path"
    }

    /**
     * Get the full image URL for a backdrop path.
     */
    fun getBackdropUrl(path: String?, size: String = "original"): String? {
        if (path == null) return null
        return "$IMAGE_BASE_URL/$size$path"
    }

    private suspend fun buildSearchQuery(endpoint: String, params: Map<String, String>): String? {
        return withContext(Dispatchers.IO) {
            try {
                val queryString = params.entries.joinToString("&") { (k, v) ->
                    "$k=${java.net.URLEncoder.encode(v, "UTF-8")}"
                }
                val url = "$BASE_URL/$endpoint?api_key=${getApiKey()}&$queryString"
                fetchUrl(url)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parseSearchResult(json: String?): TmdbResult? {
        if (json == null) return null
        return try {
            val obj = JSONObject(json)
            val results = obj.optJSONArray("results")
            if (results == null || results.length() == 0) return null
            val first = results.optJSONObject(0) ?: return null
            TmdbResult(
                id = first.getInt("id"),
                title = first.optString("title", first.optString("name", "")),
                overview = first.optString("overview", ""),
                posterPath = first.optString("poster_path", null),
                backdropPath = first.optString("backdrop_path", null),
                voteAverage = first.optDouble("vote_average", 0.0),
                releaseDate = first.optString("release_date", first.optString("first_air_date", "")),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMetadata(json: String, type: String): TmdbMetadata? {
        return try {
            val obj = JSONObject(json)
            TmdbMetadata(
                id = obj.getInt("id"),
                type = type,
                title = obj.optString("title", obj.optString("name", "")),
                overview = obj.optString("overview", ""),
                posterPath = obj.optString("poster_path", null),
                backdropPath = obj.optString("backdrop_path", null),
                voteAverage = obj.optDouble("vote_average", 0.0),
                voteCount = obj.optInt("vote_count", 0),
                genres = obj.optJSONArray("genres")?.let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it).optString("name", "") }
                } ?: emptyList(),
                runtime = obj.optInt("runtime", obj.optInt("episode_run_time", 0)),
                releaseDate = obj.optString("release_date", obj.optString("first_air_date", "")),
                numberOfSeasons = obj.optInt("number_of_seasons", 0),
                numberOfEpisodes = obj.optInt("number_of_episodes", 0),
                status = obj.optString("status", ""),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchUrl(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as? HttpsURLConnection
                ?: return null
            conn.requestMethod = "GET"
            conn.connectTimeout = Constants.NETWORK_TIMEOUT_MS
            conn.readTimeout = Constants.NETWORK_TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            (URL(url).openConnection() as? HttpsURLConnection)?.disconnect()
        }
    }

    private fun getApiKey(): String {
        return com.betterstreamflix.BuildConfig.TMDB_API_KEY ?: ""
    }
}

/**
 * TMDB search result.
 */
data class TmdbResult(
    val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val releaseDate: String,
)

/**
 * Full TMDB metadata.
 */
data class TmdbMetadata(
    val id: Int,
    val type: String,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val voteCount: Int,
    val genres: List<String>,
    val runtime: Int,
    val releaseDate: String,
    val numberOfSeasons: Int,
    val numberOfEpisodes: Int,
    val status: String,
)
