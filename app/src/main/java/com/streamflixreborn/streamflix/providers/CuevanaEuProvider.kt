package com.streamflixreborn.streamflix.providers

import android.util.Log
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.models.*
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.TMDb3
import com.streamflixreborn.streamflix.utils.TMDb3.original
import com.streamflixreborn.streamflix.utils.TMDb3.w500
import com.streamflixreborn.streamflix.utils.TmdbUtils
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.io.File
import java.util.concurrent.TimeUnit

object CuevanaEuProvider : Provider {

    override val name = "Cuevana 3"
    override val baseUrl: String get() = "https://${UserPreferences.cuevanaDomain}"
    override val language = "es"
    private const val TAG = "CuevanaEuProvider"

    private var _service: CuevanaEuService? = null
    private val service: CuevanaEuService
        get() {
            if (_service == null) {
                val retrofit = Retrofit.Builder()
                    .baseUrl("$baseUrl/")
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)
                    .build()
                _service = retrofit.create(CuevanaEuService::class.java)
            }
            return _service!!
        }

    private var _client: okhttp3.OkHttpClient? = null
    private val client: okhttp3.OkHttpClient
        get() {
            if (_client == null) {
                _client = getOkHttpClient()
            }
            return _client!!
        }

    private val json = Json { ignoreUnknownKeys = true }

    private fun getOkHttpClient(): okhttp3.OkHttpClient {
        val appCache = Cache(File("cacheDir", "okhttpcache"), 10 * 1024 * 1024)
        val clientBuilder = okhttp3.OkHttpClient.Builder()
            .cache(appCache)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Referer", baseUrl)
                    .build()
                chain.proceed(request)
            }
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.isRedirect) {
                    val location = response.header("Location")
                    if (!location.isNullOrEmpty()) {
                        val newHost = if (location.startsWith("http")) {
                            java.net.URL(location).host
                        } else {
                            null
                        }
                        if (!newHost.isNullOrEmpty() && newHost != UserPreferences.cuevanaDomain) {
                            Log.d(TAG, "Domain changed from ${UserPreferences.cuevanaDomain} to $newHost")
                            UserPreferences.cuevanaDomain = newHost
                            _service = null
                            _client = null
                        }
                    }
                }
                response
            }
        return clientBuilder.dns(DnsResolver.doh).build()
    }

    private interface CuevanaEuService {
        @GET
        suspend fun getPage(@Url url: String): Document
    }

    @Serializable
    private data class RyusakiServersResponse(
        val success: Boolean,
        val servers: List<RyusakiServer>? = null
    )

    @Serializable
    private data class RyusakiServer(
        val id: String,
        val serverName: String,
        val language: String,
        val type: String, // "embed" or "download"
        val url: String? = null
    )

    @Serializable
    private data class RyusakiStreamResponse(
        val success: Boolean,
        val streamUrl: String? = null
    )

    @Serializable
    private data class CuevanaEpisodesResponse(
        val season: Int,
        val episodes: List<CuevanaEpisode>
    )

    @Serializable
    private data class CuevanaEpisode(
        val snum: Int,
        val enum: Int,
        val name: String,
        val still_path: String? = null
    )

    @Serializable
    private data class FastApiEnvelope<T>(
        val error: Boolean = false,
        val message: String? = null,
        val data: T? = null,
    )

    @Serializable
    private data class FastApiImages(
        val poster: String? = null,
        val backdrop: String? = null,
        val logo: String? = null,
    )

    @Serializable
    private data class FastApiSinglePost(
        val _id: Int,
        val title: String,
        val overview: String? = null,
        val slug: String,
        val images: FastApiImages? = null,
        val trailer: String? = null,
        val rating: String? = null,
        val community_rating: String? = null,
        val community_vote_count: Int? = null,
        val genres: List<Int> = emptyList(),
        val quality: List<Int> = emptyList(),
        val years: List<Int> = emptyList(),
        val type: String,
        val release_date: String? = null,
        val last_update: String? = null,
        val vote_count: String? = null,
        val runtime: String? = null,
        val original_title: String? = null,
        val gallery: String? = null,
        val poster: String? = null,
        val backdrop: String? = null,
        val tagline: String? = null,
    )

    @Serializable
    private data class FastApiEpisodeListData(
        val posts: List<FastApiEpisodeSummary> = emptyList(),
        val seasons: List<String> = emptyList(),
        val pagination: FastApiPagination? = null,
    )

    @Serializable
    private data class FastApiPageData(
        val posts: List<FastApiSinglePost> = emptyList(),
        val pagination: FastApiPagination? = null,
    )

    @Serializable
    private data class FastApiEpisodeSingleData(
        val episode: FastApiEpisodeSummary? = null,
        val serie: FastApiSinglePost? = null,
    )

    @Serializable
    private data class FastApiPlayerData(
        val embeds: List<FastApiPlayerEntry> = emptyList(),
        val downloads: List<FastApiPlayerEntry> = emptyList(),
    )

    @Serializable
    private data class FastApiPlayerEntry(
        val url: String,
        val server: String? = null,
        val lang: String? = null,
        val quality: String? = null,
        val size: String? = null,
        val subtitle: Int? = null,
        val format: String? = null,
        val resolution: String? = null,
    )

    @Serializable
    private data class FastApiPagination(
        val current_page: Int = 0,
        val last_page: Int = 0,
        val per_page: Int = 0,
        val total: Int = 0,
    )

    @Serializable
    private data class FastApiEpisodeSummary(
        val _id: Int,
        val title: String,
        val date: String? = null,
        val slug: String,
        val type: String,
        val episode_type: String? = null,
        val overview: String? = null,
        val runtime: String? = null,
        val show_id: String? = null,
        val still_path: String? = null,
        val vote_average: String? = null,
        val vote_count: String? = null,
        val season_number: Int,
        val episode_number: Int,
    )

    override suspend fun getHome(): List<Category> {
        return try {
            coroutineScope {
                getCuevanaFastApiHome()
                    .takeIf { it.isNotEmpty() }
                    ?.let { return@coroutineScope it }

                val document = service.getPage("$baseUrl/")
                Log.d(TAG, "getHome: fetched document from $baseUrl. Length: ${document.toString().length}")
                if (document.toString().length > 1000) {
                    Log.d(TAG, "getHome HTML Snippet: ${document.toString().take(1000)}")
                }

                val categories = mutableListOf<Category>()

                fun parseSection(selector: String, name: String): Category? {
                    val items = document.select("$selector article").mapNotNull { article ->
                        val a = article.selectFirst("h2 a") ?: return@mapNotNull null
                        val title = a.text()
                        val href = a.attr("href")
                        val poster = (article.selectFirst("img")?.let {
                            it.attr("data-src").ifEmpty { it.attr("src") }.ifEmpty { it.attr("data-lazy-src") }
                        } ?: article.selectFirst(".backdrop")?.attr("style")?.let { style ->
                            Regex("url\\(['\"]?(.*?)['\"]?\\)").find(style)?.groupValues?.get(1)
                        })?.let { fixImageUrl(it) }

                        if (isMovieHref(href)) {
                            Movie(
                                id = extractId(href),
                                title = title,
                                poster = poster
                            )
                        } else if (isTvShowHref(href)) {
                            TvShow(
                                id = extractId(href),
                                title = title,
                                poster = poster
                            )
                        } else null
                    }
                    return if (items.isNotEmpty()) Category(name = name, list = items) else null
                }

                parseSection("#last-movies", "Últimas Películas")?.let { categories.add(it) }
                parseSection("#premiere-movies", "Estrenos Películas")?.let { categories.add(it) }
                parseSection("#trend-movies", "Tendencias Películas")?.let { categories.add(it) }
                parseSection("#last-series", "Últimas Series")?.let { categories.add(it) }
                parseSection("#premiere-series", "Estrenos Series")?.let { categories.add(it) }
                parseSection("#trend-series", "Tendencias Series")?.let { categories.add(it) }

                if (categories.isNotEmpty()) {
                    categories
                } else {
                    val movies = async { getMovies(1) }
                    val tvShows = async { getTvShows(1) }

                    listOfNotNull(
                        movies.await()
                            .takeIf { it.isNotEmpty() }
                            ?.let { Category(name = "Últimas Películas", list = it.take(20)) },
                        tvShows.await()
                            .takeIf { it.isNotEmpty() }
                            ?.let { Category(name = "Últimas Series", list = it.take(20)) },
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            return listOf(
                Genre("accion", "Acción"),
                Genre("aventura", "Aventura"),
                Genre("animacion", "Animación"),
                Genre("ciencia-ficcion", "Ciencia Ficción"),
                Genre("comedia", "Comedia"),
                Genre("crimen", "Crimen"),
                Genre("documental", "Documental"),
                Genre("drama", "Drama"),
                Genre("familia", "Familia"),
                Genre("fantasia", "Fantasía"),
                Genre("misterio", "Misterio"),
                Genre("romance", "Romance"),
                Genre("suspense", "Suspenso"),
                Genre("terror", "Terror"),
            )
        }
        getFastApi<FastApiPageData>(
            path = "/search",
            params = mapOf(
                "q" to query,
                "page" to page.toString(),
                "postType" to "any",
                "postsPerPage" to "24",
            )
        )?.posts
            ?.mapNotNull(::fastApiPostToItem)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        return try {
            val document = service.getPage("$baseUrl/?s=$query")
            parseArticles(document).mapNotNull { (article, anchor, href) ->
                val title = extractTitle(article, anchor)
                val poster = extractPoster(article)

                if (isMovieHref(href)) {
                    Movie(
                        id = extractId(href),
                        title = title,
                        poster = poster
                    )
                } else if (isTvShowHref(href)) {
                    TvShow(
                        id = extractId(href),
                        title = title,
                        poster = poster
                    )
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        getFastApi<FastApiPageData>(
            path = "/listing/movies",
            params = mapOf(
                "page" to page.toString(),
                "orderBy" to "latest",
                "order" to "desc",
                "postType" to "movies",
                "postsPerPage" to "24",
            )
        )?.posts
            ?.mapNotNull(::fastApiPostToMovie)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        return try {
            loadFirstNonEmptyCatalog(
                listOf(
                    if (page == 1) "$baseUrl/peliculas/" else "$baseUrl/peliculas/page/$page/",
                    if (page == 1) "$baseUrl/pelicula/" else "$baseUrl/pelicula/page/$page/",
                )
            ) { document ->
                parseArticles(document).mapNotNull { (article, anchor, href) ->
                    if (!isMovieHref(href)) return@mapNotNull null
                    Movie(
                        id = extractId(href),
                        title = extractTitle(article, anchor),
                        poster = extractPoster(article)
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val tvShows = getFastApi<FastApiPageData>(
            path = "/listing/tvshows",
            params = mapOf(
                "page" to page.toString(),
                "orderBy" to "latest",
                "order" to "desc",
                "postType" to "tvshows",
                "postsPerPage" to "24",
            )
        )?.posts
            ?.mapNotNull(::fastApiPostToTvShow)
            .orEmpty()

        val anime = getFastApi<FastApiPageData>(
            path = "/listing/animes",
            params = mapOf(
                "page" to page.toString(),
                "orderBy" to "latest",
                "order" to "desc",
                "postType" to "animes",
                "postsPerPage" to "24",
            )
        )?.posts
            ?.mapNotNull(::fastApiPostToTvShow)
            .orEmpty()

        (tvShows + anime)
            .distinctBy { it.id }
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        return try {
            loadFirstNonEmptyCatalog(
                listOf(
                    if (page == 1) "$baseUrl/series/" else "$baseUrl/series/page/$page/",
                    if (page == 1) "$baseUrl/serie/" else "$baseUrl/serie/page/$page/",
                )
            ) { document ->
                parseArticles(document).mapNotNull { (article, anchor, href) ->
                    if (!isTvShowHref(href)) return@mapNotNull null
                    TvShow(
                        id = extractId(href),
                        title = extractTitle(article, anchor),
                        poster = extractPoster(article)
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovie(id: String): Movie {
        val slug = extractSlug(id)
        val fastApiMovie = getFastApi<FastApiSinglePost>(
            path = "/single/movies",
            params = mapOf(
                "slug" to slug,
                "postType" to "movies",
            )
        )

        if (fastApiMovie != null) {
            val fastApiPoster = fastApiMovie.poster?.let(::fixImageUrl)
            val fastApiBanner = fastApiMovie.backdrop?.let(::fixImageUrl)
            val tmdbMovie = TmdbUtils.getMovie(
                title = fastApiMovie.original_title ?: fastApiMovie.title,
                year = extractYear(fastApiMovie.release_date) ?: fastApiMovie.years.firstOrNull(),
                language = language
            )

            if (tmdbMovie != null) {
                return tmdbMovie.copy(
                    id = id,
                    title = tmdbMovie.title.ifBlank { fastApiMovie.title },
                    overview = tmdbMovie.overview?.takeIf { it.isNotBlank() } ?: fastApiMovie.overview,
                    runtime = tmdbMovie.runtime ?: parseRuntimeMinutes(fastApiMovie.runtime),
                    trailer = tmdbMovie.trailer ?: normalizeTrailerUrl(fastApiMovie.trailer),
                    rating = tmdbMovie.rating ?: parseRating(fastApiMovie),
                    poster = tmdbMovie.poster ?: fastApiPoster,
                    banner = tmdbMovie.banner ?: fastApiBanner,
                )
            }

            return Movie(
                id = id,
                title = fastApiMovie.title,
                overview = fastApiMovie.overview,
                released = fastApiMovie.release_date,
                runtime = parseRuntimeMinutes(fastApiMovie.runtime),
                trailer = normalizeTrailerUrl(fastApiMovie.trailer),
                rating = parseRating(fastApiMovie),
                poster = fastApiPoster,
                banner = fastApiBanner,
                genres = fastApiMovie.genres.mapNotNull(::genreFromId)
            )
        }

        val document = service.getPage("$baseUrl/$id/")
        val title = document.selectFirst("h1")?.text() ?: ""

        val tmdbId = document.selectFirst("#player-wrapper")?.attr("data-id")
            ?: document.selectFirst("div[data-id]")?.attr("data-id")

        val movie = if (!tmdbId.isNullOrEmpty()) {
            try {
                TMDb3.Movies.details(
                    movieId = tmdbId.toInt(),
                    appendToResponse = listOf(
                        TMDb3.Params.AppendToResponse.Movie.CREDITS,
                        TMDb3.Params.AppendToResponse.Movie.RECOMMENDATIONS,
                        TMDb3.Params.AppendToResponse.Movie.VIDEOS,
                        TMDb3.Params.AppendToResponse.Movie.EXTERNAL_IDS,
                    ),
                    language = language
                ).let { tmdbMovie ->
                    Movie(
                        id = id,
                        title = tmdbMovie.title,
                        overview = tmdbMovie.overview,
                        released = tmdbMovie.releaseDate,
                        runtime = tmdbMovie.runtime,
                        trailer = tmdbMovie.videos?.results
                            ?.sortedBy { it.publishedAt ?: "" }
                            ?.firstOrNull { it.site == TMDb3.Video.VideoSite.YOUTUBE }
                            ?.let { "https://www.youtube.com/watch?v=${it.key}" },
                        rating = tmdbMovie.voteAverage.toDouble(),
                        poster = tmdbMovie.posterPath?.original,
                        banner = tmdbMovie.backdropPath?.original,
                        imdbId = tmdbMovie.externalIds?.imdbId,
                        genres = tmdbMovie.genres.map { genre ->
                            Genre(genre.id.toString(), genre.name)
                        },
                        cast = tmdbMovie.credits?.cast?.map { cast ->
                            People(
                                id = cast.id.toString(),
                                name = cast.name,
                                image = cast.profilePath?.w500,
                            )
                        } ?: emptyList(),
                        recommendations = tmdbMovie.recommendations?.results?.mapNotNull { multi ->
                            when (multi) {
                                is TMDb3.Movie -> Movie(
                                    id = multi.id.toString(),
                                    title = multi.title,
                                    poster = multi.posterPath?.w500,
                                )
                                is TMDb3.Tv -> TvShow(
                                    id = multi.id.toString(),
                                    title = multi.name,
                                    poster = multi.posterPath?.w500,
                                )
                                else -> null
                            }
                        } ?: emptyList(),
                    )
                }
            } catch (e: Exception) {
                null
            }
        } else null

        if (movie != null) return movie

        val overview = document.selectFirst("div.entry p")?.text()
            ?: document.selectFirst("div[data-read-more-text]")?.text() ?: ""
        val released = document.select("div.flex.items-center.flex-wrap.gap-x-1 span")
            .firstOrNull { it.text().matches(Regex("\\d{4}")) }?.text()
        val poster = document.selectFirst("div.self-start figure img, div.Image img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }?.let { fixImageUrl(it) }
        val banner = document.selectFirst("figure.mask-to-l img, .backdrop img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }?.let { fixImageUrl(it) }

        val genres = document.select("a[href*='/genero/']").map {
            Genre(id = it.attr("href").substringAfter("/genero/").trim('/'), name = it.text())
        }.distinctBy { it.id }

        val cast = document.select("a[href*='/elenco/']").map {
            People(id = it.attr("href").substringAfter("/elenco/").trim('/'), name = it.text())
        }.distinctBy { it.name }

        return Movie(
            id = id,
            title = title,
            overview = overview,
            released = released,
            poster = poster,
            banner = banner,
            genres = genres,
            cast = cast
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        Log.d(TAG, "getTvShow: id=$id")
        val slug = extractSlug(id)
        val showPostType = getCuevanaShowPostType(id)
        val fastApiTvShow = getFastApi<FastApiSinglePost>(
            path = "/single/$showPostType",
            params = mapOf(
                "slug" to slug,
                "postType" to showPostType,
            )
        )

        if (fastApiTvShow != null) {
            val cuevanaSeasons = getCuevanaSeasons(id, fastApiTvShow)
            val fastApiPoster = fastApiTvShow.poster?.let(::fixImageUrl)
            val fastApiBanner = fastApiTvShow.backdrop?.let(::fixImageUrl)
            val tmdbTvShow = TmdbUtils.getTvShow(
                title = fastApiTvShow.original_title ?: fastApiTvShow.title,
                year = extractYear(fastApiTvShow.release_date) ?: fastApiTvShow.years.firstOrNull(),
                language = language
            )

            if (tmdbTvShow != null) {
                val mergedSeasons = cuevanaSeasons.map { cuevanaSeason ->
                    val tmdbSeason = tmdbTvShow.seasons.find { it.number == cuevanaSeason.number }
                    tmdbSeason
                        ?.copy(
                            id = cuevanaSeason.id,
                            title = tmdbSeason.title ?: cuevanaSeason.title,
                            poster = tmdbSeason.poster ?: cuevanaSeason.poster,
                        )
                        ?: cuevanaSeason
                }

                return tmdbTvShow.copy(
                    id = id,
                    title = tmdbTvShow.title.ifBlank { fastApiTvShow.title },
                    overview = tmdbTvShow.overview?.takeIf { it.isNotBlank() } ?: fastApiTvShow.overview,
                    runtime = tmdbTvShow.runtime ?: parseRuntimeMinutes(fastApiTvShow.runtime),
                    trailer = tmdbTvShow.trailer ?: normalizeTrailerUrl(fastApiTvShow.trailer),
                    rating = tmdbTvShow.rating ?: parseRating(fastApiTvShow),
                    poster = tmdbTvShow.poster ?: fastApiPoster,
                    banner = tmdbTvShow.banner ?: fastApiBanner,
                    seasons = mergedSeasons,
                )
            }

            return TvShow(
                id = id,
                title = fastApiTvShow.title,
                overview = fastApiTvShow.overview,
                released = fastApiTvShow.release_date,
                runtime = parseRuntimeMinutes(fastApiTvShow.runtime),
                trailer = normalizeTrailerUrl(fastApiTvShow.trailer),
                rating = parseRating(fastApiTvShow),
                poster = fastApiPoster,
                banner = fastApiBanner,
                seasons = cuevanaSeasons,
                genres = fastApiTvShow.genres.mapNotNull(::genreFromId),
            )
        }

        val document = service.getPage("$baseUrl/$id/")
        val title = document.selectFirst("h1")?.text() ?: ""

        val tmdbId = document.selectFirst("#player-wrapper")?.attr("data-id")
            ?: document.selectFirst("div[data-id]")?.attr("data-id")

        // Try to find the "todos los episodios" link if seasons are not directly visible
        val todosLosEpisodiosLink = document.select("a, span, button").firstOrNull { it.text().contains("todos los episodios", ignoreCase = true) }
        val seasonsDoc = if (todosLosEpisodiosLink != null && todosLosEpisodiosLink.tagName() == "a") {
            val url = todosLosEpisodiosLink.attr("href")
            Log.d(TAG, "getTvShow: following 'todos los episodi' link: $url")
            try {
                service.getPage(if (url.startsWith("http")) url else "$baseUrl/${url.trim('/')}")
            } catch (e: Exception) {
                document
            }
        } else {
            document
        }

        val cuevanaSeasons = seasonsDoc.select("div.se-q, button[data-season], .se-nav li span, .se-c, #seasons .se-q").mapNotNull {
            val number = it.selectFirst(".se-t")?.text()?.toIntOrNull()
                ?: it.attr("data-season").toIntOrNull()
                ?: it.text().filter { it.isDigit() }.toIntOrNull()
                ?: return@mapNotNull null
            Season(
                id = "$id/temporada-$number",
                number = number,
                title = "Temporada $number"
            )
        }.distinctBy { it.number }.sortedBy { it.number }

        val tvShow = if (!tmdbId.isNullOrEmpty()) {
            try {
                TMDb3.TvSeries.details(
                    seriesId = tmdbId.toInt(),
                    appendToResponse = listOf(
                        TMDb3.Params.AppendToResponse.Tv.CREDITS,
                        TMDb3.Params.AppendToResponse.Tv.RECOMMENDATIONS,
                        TMDb3.Params.AppendToResponse.Tv.VIDEOS,
                        TMDb3.Params.AppendToResponse.Tv.EXTERNAL_IDS,
                    ),
                    language = language
                ).let { tmdbTv ->
                    TvShow(
                        id = id,
                        title = tmdbTv.name,
                        overview = tmdbTv.overview,
                        released = tmdbTv.firstAirDate,
                        trailer = tmdbTv.videos?.results
                            ?.sortedBy { it.publishedAt ?: "" }
                            ?.firstOrNull { it.site == TMDb3.Video.VideoSite.YOUTUBE }
                            ?.let { "https://www.youtube.com/watch?v=${it.key}" },
                        rating = tmdbTv.voteAverage.toDouble(),
                        poster = tmdbTv.posterPath?.original,
                        banner = tmdbTv.backdropPath?.original,
                        imdbId = tmdbTv.externalIds?.imdbId,
                        seasons = cuevanaSeasons.map { season ->
                            val tmdbSeason = tmdbTv.seasons.find { it.seasonNumber == season.number }
                            season.copy(
                                title = tmdbSeason?.name ?: season.title,
                                poster = tmdbSeason?.posterPath?.w500 ?: season.poster
                            )
                        },
                        genres = tmdbTv.genres.map { genre ->
                            Genre(genre.id.toString(), genre.name)
                        },
                        cast = tmdbTv.credits?.cast?.map { cast ->
                            People(
                                id = cast.id.toString(),
                                name = cast.name,
                                image = cast.profilePath?.w500,
                            )
                        } ?: emptyList(),
                        recommendations = tmdbTv.recommendations?.results?.mapNotNull { multi ->
                            when (multi) {
                                is TMDb3.Movie -> Movie(
                                    id = multi.id.toString(),
                                    title = multi.title,
                                    poster = multi.posterPath?.w500,
                                )
                                is TMDb3.Tv -> TvShow(
                                    id = multi.id.toString(),
                                    title = multi.name,
                                    poster = multi.posterPath?.w500,
                                )
                                else -> null
                            }
                        } ?: emptyList(),
                    )
                }
            } catch (e: Exception) {
                null
            }
        } else null

        if (tvShow != null) return tvShow

        val overview = document.selectFirst("div.entry p")?.text()
            ?: document.selectFirst("div[data-read-more-text]")?.text() ?: ""
        val released = document.select("div.flex.items-center.flex-wrap.gap-x-1 span")
            .firstOrNull { it.text().matches(Regex("\\d{4}")) }?.text()
        val poster = document.selectFirst("div.self-start figure img, div.Image img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }?.let { fixImageUrl(it) }
        val banner = document.selectFirst("figure.mask-to-l img, .backdrop img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }?.let { fixImageUrl(it) }

        val genres = document.select("a[href*='/genero/']").map {
            Genre(id = it.attr("href").substringAfter("/genero/").trim('/'), name = it.text())
        }.distinctBy { it.id }

        val cast = document.select("a[href*='/elenco/']").map {
            People(id = it.attr("href").substringAfter("/elenco/").trim('/'), name = it.text())
        }.distinctBy { it.name }

        Log.d(TAG, "getTvShow: found ${cuevanaSeasons.size} seasons")

        return TvShow(
            id = id,
            title = title,
            overview = overview,
            released = released,
            poster = poster,
            banner = banner,
            genres = genres,
            cast = cast,
            seasons = cuevanaSeasons
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        Log.d(TAG, "getEpisodesBySeason: seasonId=$seasonId")
        val seasonNumber = seasonId.substringAfterLast("-").toIntOrNull() ?: 1
        val seriesSlug = if (seasonId.contains("/temporada")) seasonId.substringBeforeLast("/temporada") else seasonId
        val tvShowSlug = extractSlug(seriesSlug)
        val showPostType = getCuevanaShowPostType(seriesSlug)

        try {
            val fastApiTvShow = getFastApi<FastApiSinglePost>(
                path = "/single/$showPostType",
                params = mapOf(
                    "slug" to tvShowSlug,
                    "postType" to showPostType,
                )
            )

            if (fastApiTvShow != null) {
                val tmdbTvShow = TmdbUtils.getTvShow(
                    title = fastApiTvShow.original_title ?: fastApiTvShow.title,
                    year = extractYear(fastApiTvShow.release_date) ?: fastApiTvShow.years.firstOrNull(),
                    language = language
                )
                val tmdbEpisodes = tmdbTvShow
                    ?.id
                    ?.toIntOrNull()
                    ?.let { TmdbUtils.getEpisodesBySeason(it.toString(), seasonNumber, language) }
                    .orEmpty()
                val cuevanaEpisodes = getFastApi<FastApiEpisodeListData>(
                    path = "/single/episodes/list",
                    params = mapOf(
                        "_id" to fastApiTvShow._id.toString(),
                        "season" to seasonNumber.toString(),
                        "page" to "1",
                        "postsPerPage" to "100",
                    )
                )

                if (cuevanaEpisodes != null && cuevanaEpisodes.posts.isNotEmpty()) {
                    return cuevanaEpisodes.posts
                        .sortedBy { it.episode_number }
                        .map { cuevanaEpisode ->
                            val tmdbEpisode = tmdbEpisodes.firstOrNull { it.number == cuevanaEpisode.episode_number }
                            val episodeId = "episodio/${cuevanaEpisode.slug}"
                            tmdbEpisode?.copy(
                                id = episodeId,
                                title = tmdbEpisode.title ?: cuevanaEpisode.title,
                                overview = tmdbEpisode.overview ?: cuevanaEpisode.overview,
                                poster = tmdbEpisode.poster ?: cuevanaEpisode.still_path?.let(::fixImageUrl),
                            ) ?: Episode(
                                id = episodeId,
                                number = cuevanaEpisode.episode_number,
                                title = cuevanaEpisode.title,
                                overview = cuevanaEpisode.overview,
                                released = cuevanaEpisode.date,
                                poster = cuevanaEpisode.still_path?.let(::fixImageUrl),
                            )
                        }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getEpisodesBySeason fastApi error: ${e.message}", e)
        }

        return try {
            val url = "$baseUrl/${seriesSlug.trim('/')}/"
            Log.d(TAG, "getEpisodesBySeason: fetching series page to get post_id and nonce: $url")
            val document = service.getPage(url)

            val postId = document.selectFirst("#season-wrapper")?.attr("data-post-id")
                ?: document.selectFirst("#player-wrapper")?.attr("data-post_id")
            val tmdbId = document.selectFirst("#player-wrapper")?.attr("data-id")
                ?: document.selectFirst("div[data-id]")?.attr("data-id")
            val nonce = document.html().substringAfter("window.wpApiSettings = {", "").substringAfter("\"nonce\":\"", "").substringBefore("\"")

            if (postId == null || nonce.isEmpty()) {
                Log.e(TAG, "getEpisodesBySeason: postId ($postId) or nonce ($nonce) is missing!")
                // Fallback to old method if API fails
                return fallbackGetEpisodesBySeason(seasonId)
            }

            val tmdbSeason = if (!tmdbId.isNullOrEmpty()) {
                try {
                    TMDb3.TvSeasons.details(
                        seriesId = tmdbId.toInt(),
                        seasonNumber = seasonNumber,
                        language = language
                    )
                } catch (e: Exception) {
                    null
                }
            } else null

            val apiUrl = "$baseUrl/wp-json/cuevana/v1/get-season-episodes?id=$postId&season=$seasonNumber"
            Log.d(TAG, "getEpisodesBySeason: calling API $apiUrl")

            val request = Request.Builder()
                .url(apiUrl)
                .header("X-WP-Nonce", nonce)
                .build()

            val responseBody = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { it.body?.string() }
            } ?: return emptyList()

            val responseJson = json.decodeFromString<CuevanaEpisodesResponse>(responseBody)
            return responseJson.episodes.map { cuevanaEpisode ->
                val tmdbEpisode = tmdbSeason?.episodes?.find { it.episodeNumber == cuevanaEpisode.enum }
                Episode(
                    id = "$seriesSlug/temporada-${cuevanaEpisode.snum}/episodio-${cuevanaEpisode.enum}",
                    number = cuevanaEpisode.enum,
                    title = tmdbEpisode?.name ?: cuevanaEpisode.name,
                    overview = tmdbEpisode?.overview,
                    released = tmdbEpisode?.airDate,
                    poster = if (!cuevanaEpisode.still_path.isNullOrEmpty()) {
                        "https://image.tmdb.org/t/p/w300${cuevanaEpisode.still_path}"
                    } else {
                        tmdbEpisode?.stillPath?.w500
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getEpisodesBySeason error: ${e.message}", e)
            fallbackGetEpisodesBySeason(seasonId)
        }
    }

    private suspend fun fallbackGetEpisodesBySeason(seasonId: String): List<Episode> {
        val seasonNumber = seasonId.substringAfterLast("-").toIntOrNull()
        val seriesId = if (seasonId.contains("/temporada")) seasonId.substringBeforeLast("/temporada") else seasonId

        // List of URLs to try
        val urlsToTry = mutableListOf<String>()
        urlsToTry.add("$baseUrl/${seasonId.trim('/')}/")
        urlsToTry.add("$baseUrl/${seriesId.trim('/')}/")
        if (seasonNumber != null) {
            urlsToTry.add("$baseUrl/${seriesId.trim('/')}/temporada/$seasonNumber/")
        }

        for (url in urlsToTry) {
            try {
                Log.d(TAG, "getEpisodesBySeason fallback: trying URL: $url")
                val document = service.getPage(url)

                // Check for "todos los episodi" on the page too
                val todosLosEpisodios = document.select("a").firstOrNull {
                    it.text().contains("todos los episodi", ignoreCase = true) ||
                            it.attr("href").contains("/episodios/")
                }

                val docsToProcess = mutableListOf(document)
                if (todosLosEpisodios != null) {
                    val allUrl = todosLosEpisodios.attr("href")
                    val fullAllUrl = if (allUrl.startsWith("http")) allUrl else "$baseUrl/${allUrl.trim('/')}"
                    if (fullAllUrl != url) {
                        try {
                            docsToProcess.add(service.getPage(fullAllUrl))
                        } catch (e: Exception) {
                        }
                    }
                }

                for (doc in docsToProcess) {
                    val episodes = parseEpisodesFromDoc(doc, seasonNumber)
                    if (episodes.isNotEmpty()) {
                        return episodes
                    }
                }
            } catch (e: Exception) {
            }
        }
        return emptyList()
    }

    private fun parseEpisodesFromDoc(document: Document, seasonNumber: Int?): List<Episode> {
        val seasonSection = if (seasonNumber != null) {
            document.select("div.se-c").firstOrNull {
                it.selectFirst(".se-q .se-t, .se-q")?.text()?.filter { it.isDigit() }?.toIntOrNull() == seasonNumber
            }
        } else null

        val episodeElements = seasonSection?.select("ul.episodios li, .episodios li, article")
            ?: document.select("#season-episodes article, ul.episodios li, .episodios li, article.episodio")

        return episodeElements.mapNotNull { element ->
            val a = element.selectFirst("h2 a, .episodiotitle a, a") ?: return@mapNotNull null
            val href = a.attr("href")
            if (href.isEmpty() || (!href.contains("/episodio/") && !href.contains("-temporada-"))) return@mapNotNull null

            // Filter by season if we are not in a specific season section
            if (seasonSection == null && seasonNumber != null) {
                val text = element.text()
                val isCorrectSeason = href.contains("-temporada-$seasonNumber-") ||
                        href.contains("${seasonNumber}x") ||
                        text.contains("${seasonNumber}x") ||
                        element.selectFirst(".numerando")?.text()?.startsWith("$seasonNumber") == true
                if (!isCorrectSeason) return@mapNotNull null
            }

            val epTitle = a.text()
            val epNumberText = element.selectFirst("span.bg-main, .numerando, .ep-num")?.text() ?: ""
            val epNumber = epNumberText.filter { it.isDigit() }.toIntOrNull() ?: 0

            Episode(
                id = href.substringAfter(baseUrl).trim('/'),
                number = epNumber,
                title = epTitle,
                poster = element.selectFirst("img.poster, .episodioimage img, img")?.let {
                    it.attr("data-src").ifEmpty { it.attr("src") }
                }
            )
        }.distinctBy { it.id }
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return try {
            val postId = when (videoType) {
                is Video.Type.Movie -> getFastApi<FastApiSinglePost>(
                    path = "/single/movies",
                    params = mapOf(
                        "slug" to extractSlug(id),
                        "postType" to "movies",
                    )
                )?._id
                is Video.Type.Episode -> {
                    val directEpisode = getFastApi<FastApiEpisodeSingleData>(
                        path = "/single/episodes",
                        params = mapOf(
                            "slug" to extractSlug(id),
                            "postType" to "episodes",
                        )
                    )?.episode?._id

                    directEpisode ?: run {
                        val tvShowSlug = extractSlug(videoType.tvShow.id)
                        val showPostType = getCuevanaShowPostType(videoType.tvShow.id)
                        val tvShow = getFastApi<FastApiSinglePost>(
                            path = "/single/$showPostType",
                            params = mapOf(
                                "slug" to tvShowSlug,
                                "postType" to showPostType,
                            )
                        )
                        val episode = tvShow?._id?.let { tvShowPostId ->
                            getFastApi<FastApiEpisodeListData>(
                                path = "/single/episodes/list",
                                params = mapOf(
                                    "_id" to tvShowPostId.toString(),
                                    "season" to videoType.season.number.toString(),
                                    "page" to "1",
                                    "postsPerPage" to "100",
                                )
                            )?.posts?.firstOrNull {
                                it.season_number == videoType.season.number && it.episode_number == videoType.number
                            }
                        }
                        episode?._id
                    }
                }
            } ?: return emptyList()

            val playerData = getFastApi<FastApiPlayerData>(
                path = "/player",
                params = mapOf(
                    "postId" to postId.toString(),
                    "demo" to "0",
                )
            ) ?: return emptyList()

            playerData.embeds
                .map { entry ->
                    Video.Server(
                        id = entry.url,
                        name = buildCuevanaServerName(entry)
                    )
                }
                .distinctBy { it.id }
        } catch (e: Exception) {
            Log.e(TAG, "getServers error: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun fetchRyusakiStreamUrl(tmdbId: String, contentType: String, serverId: String, nonce: String, season: String?, episode: String?): String? {
        return try {
            val formBodyBuilder = FormBody.Builder()
                .add("tmdbId", tmdbId)
                .add("contentType", contentType)
                .add("serverId", serverId)

            if (season != null && season.isNotEmpty()) formBodyBuilder.add("season", season)
            if (episode != null && episode.isNotEmpty()) formBodyBuilder.add("episode", episode)

            val request = Request.Builder()
                .url("$baseUrl/wp-json/ryusaki-sync/v1/request-stream")
                .header("X-WP-Nonce", nonce)
                .post(formBodyBuilder.build())
                .build()

            val responseBody = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { it.body?.string() }
            } ?: return null

            Log.d(TAG, "fetchRyusakiStreamUrl response: $responseBody")
            val streamJson = json.decodeFromString<RyusakiStreamResponse>(responseBody)
            streamJson.streamUrl
        } catch (e: Exception) {
            Log.e(TAG, "fetchRyusakiStreamUrl error: ${e.message}", e)
            null
        }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        var finalUrl = server.id
        if (finalUrl.contains("/player.php?")) {
            try {
                val html = withContext(Dispatchers.IO) {
                    client.newCall(Request.Builder().url(finalUrl).build()).execute().use { it.body?.string() }
                } ?: ""
                val iframeUrl = Regex("""<iframe[^>]+src="([^"]+)"""", RegexOption.IGNORE_CASE)
                    .find(html)
                    ?.groupValues
                    ?.getOrNull(1)
                if (!iframeUrl.isNullOrBlank()) {
                    finalUrl = iframeUrl
                }
            } catch (e: Exception) {
                Log.e(TAG, "getVideo iframe unwrap error: ${e.message}")
            }
        }
        if (finalUrl.contains("app.mysync.mov/stream/")) {
            try {
                val html = withContext(Dispatchers.IO) {
                    client.newCall(Request.Builder().url(finalUrl).build()).execute().use { it.body?.string() }
                } ?: ""
                val redirectUrl = html.substringAfter("window.location.replace(\"", "").substringBefore("\"")
                if (redirectUrl.isNotEmpty()) {
                    finalUrl = redirectUrl
                }
            } catch (e: Exception) {
                Log.e(TAG, "getVideo redirect error: ${e.message}")
            }
        }
        return Extractor.extract(finalUrl, server)
    }

    override val logo: String get() = "$baseUrl/wp-content/uploads/2026/03/cropped-unnamed-removebg-preview-32x32.png"

    private fun fixImageUrl(url: String): String? {
        if (url.isEmpty()) return null
        if (url.startsWith("data:image")) return null
        if (url.startsWith("http")) return url
        if (url.startsWith("//")) return "https:$url"
        if (url.startsWith("/thumbs/") || url.startsWith("/backdrops/") || url.startsWith("/logos/") || url.startsWith("/wp-content/")) {
            return "$baseUrl/${url.trimStart('/')}"
        }
        if (Regex("^/[A-Za-z0-9._-]+\\.(jpg|jpeg|png)$", RegexOption.IGNORE_CASE).matches(url)) {
            return url.original
        }
        return "$baseUrl/${url.trimStart('/')}"
    }

    private fun buildCuevanaServerName(entry: FastApiPlayerEntry): String {
        val rawServer = entry.url
            .toHttpUrlOrNull()
            ?.queryParameter("server")
            ?.ifBlank { null }
            ?: entry.url
                .toHttpUrlOrNull()
                ?.host
                ?.removePrefix("www.")
                ?.takeIf { it.isNotBlank() }
            ?: entry.server
            ?.takeIf { it.isNotBlank() }
            ?: "Server"

        val parts = listOfNotNull(
            rawServer,
            entry.lang?.takeIf { it.isNotBlank() },
            entry.quality?.takeIf { it.isNotBlank() },
        )
        return parts.joinToString(" | ")
    }

    private suspend inline fun <reified T> getFastApi(
        path: String,
        params: Map<String, String>,
    ): T? {
        return try {
            val baseApiUrl = "$baseUrl/wp-api/v1".toHttpUrlOrNull() ?: return null
            val url = baseApiUrl.newBuilder()
                .addEncodedPathSegments(path.trimStart('/'))
                .apply {
                    params.forEach { (key, value) -> addQueryParameter(key, value) }
                }
                .build()

            val body = withContext(Dispatchers.IO) {
                client.newCall(
                    Request.Builder()
                        .url(url)
                        .build()
                ).execute().use { it.body?.string() }
            } ?: return null

            json.decodeFromString<FastApiEnvelope<T>>(body).data
        } catch (e: Exception) {
            Log.e(TAG, "getFastApi error for $path: ${e.message}", e)
            null
        }
    }

    private suspend fun getCuevanaSeasons(
        showId: String,
        fastApiTvShow: FastApiSinglePost,
    ): List<Season> {
        val episodeList = getFastApi<FastApiEpisodeListData>(
            path = "/single/episodes/list",
            params = mapOf(
                "_id" to fastApiTvShow._id.toString(),
                "season" to "1",
                "page" to "1",
                "postsPerPage" to "1",
            )
        )

        val seasonNumbers = episodeList?.seasons
            ?.mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
            ?.ifEmpty {
                episodeList.posts
                    .map { it.season_number }
                    .distinct()
                    .sorted()
            }
            .orEmpty()

        return seasonNumbers.map { seasonNumber ->
            Season(
                id = "$showId/temporada-$seasonNumber",
                number = seasonNumber,
                title = "Temporada $seasonNumber",
            )
        }
    }

    private fun extractSlug(id: String): String {
        return id.trim('/').substringAfterLast("/")
    }

    private fun getCuevanaShowPostType(id: String): String {
        return when (id.trim('/').substringBefore('/')) {
            "anime", "animes" -> "animes"
            else -> "tvshows"
        }
    }

    private fun fastApiPostToItem(post: FastApiSinglePost): AppAdapter.Item? {
        return when (post.type) {
            "movies" -> fastApiPostToMovie(post)
            "tvshows", "animes" -> fastApiPostToTvShow(post)
            else -> null
        }
    }

    private fun fastApiPostToShow(post: FastApiSinglePost): Show? {
        return when (post.type) {
            "movies" -> fastApiPostToMovie(post)
            "tvshows", "animes" -> fastApiPostToTvShow(post)
            else -> null
        }
    }

    private fun fastApiPostToMovie(post: FastApiSinglePost): Movie? {
        if (post.type != "movies") return null
        return Movie(
            id = "peliculas/${post.slug}",
            title = post.title,
            overview = post.overview,
            released = post.release_date,
            runtime = parseRuntimeMinutes(post.runtime),
            rating = parseRating(post),
            poster = post.poster?.let(::fixImageUrl),
            banner = post.backdrop?.let(::fixImageUrl),
        )
    }

    private fun fastApiPostToTvShow(post: FastApiSinglePost): TvShow? {
        if (post.type != "tvshows" && post.type != "animes") return null
        val idPrefix = if (post.type == "animes") "animes" else "series"
        return TvShow(
            id = "$idPrefix/${post.slug}",
            title = post.title,
            overview = post.overview,
            released = post.release_date,
            runtime = parseRuntimeMinutes(post.runtime),
            rating = parseRating(post),
            poster = post.poster?.let(::fixImageUrl),
            banner = post.backdrop?.let(::fixImageUrl),
        )
    }

    private fun fastApiEpisodeToHomeEpisode(episode: FastApiEpisodeSummary): Episode {
        return Episode(
            id = "episodio/${episode.slug}",
            number = episode.episode_number,
            title = episode.title,
            released = episode.date,
            poster = episode.still_path?.let(::fixImageUrl),
            overview = episode.overview,
            season = Season(
                id = "",
                number = episode.season_number,
                title = "Temporada ${episode.season_number}",
            ),
        )
    }

    private suspend fun getCuevanaFastApiHome(): List<Category> = coroutineScope {
        val episodes = async {
            getFastApi<FastApiEpisodeListData>(
                path = "/sliders/episodes",
                params = mapOf(
                    "page" to "1",
                    "postType" to "episodes",
                    "postsPerPage" to "20",
                )
            )?.posts
                ?.map(::fastApiEpisodeToHomeEpisode)
                ?.takeIf { it.isNotEmpty() }
                ?.let { Category(name = "Episodios", list = it) }
        }
        val movies = async {
            getFastApiHomeCategory(
                name = "Películas Online",
                path = "/listing/movies",
                postType = "movies",
                postsPerPage = 18,
            ) { fastApiPostToMovie(it) }
        }
        val tvShows = async {
            getFastApiHomeCategory(
                name = "Series Online",
                path = "/listing/tvshows",
                postType = "tvshows",
                postsPerPage = 6,
            ) { fastApiPostToTvShow(it) }
        }
        val anime = async {
            getFastApiHomeCategory(
                name = "Anime Online",
                path = "/listing/animes",
                postType = "animes",
                postsPerPage = 6,
            ) { fastApiPostToTvShow(it) }
        }
        val mostViewedMovies = async {
            getFastApiHomeCategory(
                name = "Películas más vistas",
                path = "/listing/movies",
                postType = "movies",
                postsPerPage = 6,
                orderBy = "views_release_date",
            ) { fastApiPostToMovie(it) }
        }

        listOfNotNull(
            episodes.await(),
            movies.await(),
            tvShows.await(),
            anime.await(),
            mostViewedMovies.await(),
        )
    }

    private suspend fun <T : AppAdapter.Item> getFastApiHomeCategory(
        name: String,
        path: String,
        postType: String,
        postsPerPage: Int,
        orderBy: String = "latest",
        mapper: (FastApiSinglePost) -> T?,
    ): Category? {
        val items = getFastApi<FastApiPageData>(
            path = path,
            params = mapOf(
                "page" to "1",
                "orderBy" to orderBy,
                "order" to "desc",
                "postType" to postType,
                "postsPerPage" to postsPerPage.toString(),
            )
        )?.posts
            ?.mapNotNull(mapper)
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return Category(name = name, list = items)
    }

    private fun extractYear(value: String?): Int? {
        return Regex("(19|20)\\d{2}").find(value.orEmpty())?.value?.toIntOrNull()
    }

    private fun parseRuntimeMinutes(value: String?): Int? {
        return Regex("\\d+").find(value.orEmpty())?.value?.toIntOrNull()
    }

    private fun parseRating(post: FastApiSinglePost): Double? {
        return post.community_rating?.toDoubleOrNull()
            ?: post.rating?.toDoubleOrNull()
    }

    private fun normalizeTrailerUrl(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return when {
            value.startsWith("http") -> value
            value.startsWith("//") -> "https:$value"
            value.length == 11 && value.none { it == '/' } -> "https://www.youtube.com/watch?v=$value"
            else -> value
        }
    }

    private fun genreFromId(id: Int): Genre? {
        val entry = when (id) {
            26 -> "Acción" to "accion"
            253 -> "Action & Adventure" to "action-adventure"
            53 -> "Animación" to "animacion"
            25 -> "Aventura" to "aventura"
            214 -> "Bélica" to "belica"
            27 -> "Ciencia ficción" to "ciencia-ficcion"
            80 -> "Comedia" to "comedia"
            190 -> "Crimen" to "crimen"
            8690 -> "Documental" to "documental"
            81 -> "Drama" to "drama"
            54 -> "Familia" to "familia"
            163 -> "Fantasia" to "fantasia"
            680 -> "Historia" to "historia"
            251 -> "Kids" to "kids"
            401 -> "Misterio" to "misterio"
            437 -> "Musica" to "musica"
            7952 -> "Pelicula de TV" to "pelicula-de-tv"
            17547 -> "Reality" to "reality"
            82 -> "Romance" to "romance"
            252 -> "Sci-Fi & Fantasy" to "sci-fi-fantasy"
            26793 -> "Soap" to "soap"
            345 -> "Suspense" to "suspense"
            1502 -> "Terror" to "terror"
            1002 -> "War & Politics" to "war-politics"
            278 -> "Western" to "western"
            else -> null
        } ?: return null

        return Genre(id = entry.second, name = entry.first)
    }

    private suspend fun <T> loadFirstNonEmptyCatalog(
        urls: List<String>,
        parser: (Document) -> List<T>,
    ): List<T> {
        urls.forEach { url ->
            val document = runCatching { service.getPage(url) }.getOrNull() ?: return@forEach
            val items = parser(document)
            if (items.isNotEmpty()) {
                return items
            }
        }
        return emptyList()
    }

    private fun parseArticles(document: Document): List<Triple<Element, Element, String>> {
        return document.select("article.tooltip-content, article").mapNotNull { article ->
            val anchor = article.selectFirst("h2 a, a[href]") ?: return@mapNotNull null
            val href = anchor.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Triple(article, anchor, href)
        }
    }

    private fun extractTitle(article: Element, anchor: Element): String {
        return anchor.text()
            .ifBlank { article.selectFirst("h2, h3")?.text().orEmpty() }
            .ifBlank { article.selectFirst("img[alt]")?.attr("alt").orEmpty() }
            .trim()
    }

    private fun extractPoster(article: Element): String? {
        return (article.selectFirst("img")?.let {
            it.attr("data-src")
                .ifEmpty { it.attr("src") }
                .ifEmpty { it.attr("data-lazy-src") }
                .ifEmpty { it.attr("srcset").substringBefore(" ") }
        } ?: article.selectFirst(".backdrop")?.attr("style")?.let { style ->
            Regex("url\\(['\"]?(.*?)['\"]?\\)").find(style)?.groupValues?.get(1)
        })?.let { fixImageUrl(it) }
    }

    private fun isMovieHref(href: String): Boolean {
        return href.contains("/pelicula/") || href.contains("/peliculas/")
    }

    private fun isTvShowHref(href: String): Boolean {
        return href.contains("/serie/") || href.contains("/series/") ||
                href.contains("/anime/") || href.contains("/animes/")
    }

    private fun extractId(url: String): String {
        return url.substringAfter(baseUrl).trim('/')
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        getFastApi<FastApiPageData>(
            path = "/taxonomies",
            params = mapOf(
                "taxonomy" to "genres",
                "term" to id,
                "page" to page.toString(),
                "postType" to "any",
                "postsPerPage" to "24",
                "orderBy" to "ID",
                "order" to "desc",
            )
        )?.posts
            ?.mapNotNull(::fastApiPostToShow)
            ?.let { return Genre(id = id, name = id.replaceFirstChar { it.uppercaseChar() }, shows = it) }

        return try {
            val url = if (page == 1) "$baseUrl/genero/$id/" else "$baseUrl/genero/$id/page/$page/"
            val document = service.getPage(url)
            val shows = parseArticles(document).mapNotNull { (article, anchor, href) ->
                val poster = extractPoster(article)

                if (isMovieHref(href)) {
                    Movie(
                        id = extractId(href),
                        title = extractTitle(article, anchor),
                        poster = poster
                    )
                } else if (isTvShowHref(href)) {
                    TvShow(
                        id = extractId(href),
                        title = extractTitle(article, anchor),
                        poster = poster
                    )
                } else null
            }
            Genre(id = id, name = id.replaceFirstChar { it.uppercaseChar() }, shows = shows)
        } catch (e: Exception) {
            Genre(id = id, name = id.replaceFirstChar { it.uppercaseChar() }, shows = emptyList())
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        throw Exception("Esta funzione non è disponibile nel provider Cuevana 3.")
    }
}
