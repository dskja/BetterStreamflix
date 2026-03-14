package com.streamflixreborn.streamflix.providers

import android.util.Log
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.models.*
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.UserPreferences
import com.streamflixreborn.streamflix.utils.TMDb3
import com.streamflixreborn.streamflix.utils.TMDb3.original
import com.streamflixreborn.streamflix.utils.TMDb3.w500
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
import okhttp3.Request
import org.jsoup.nodes.Document
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

    override suspend fun getHome(): List<Category> {
        return try {
            coroutineScope {
                val document = service.getPage("$baseUrl/")

                val categories = mutableListOf<Category>()

                // Featured / Top 10
                val featured = document.select("div.embla__viewport article").mapNotNull { article ->
                    val a = article.selectFirst("h2 a") ?: return@mapNotNull null
                    val title = a.selectFirst("span.sr-only")?.text() ?: a.text()
                    val href = a.attr("href")
                    val poster = article.selectFirst("img.poster")?.let {
                        it.attr("data-src").ifEmpty { it.attr("src") }
                    }
                    if (href.contains("/pelicula/")) {
                        Movie(
                            id = href.substringAfter(baseUrl).trim('/'),
                            title = title,
                            poster = poster
                        )
                    } else if (href.contains("/serie/")) {
                        TvShow(
                            id = href.substringAfter(baseUrl).trim('/'),
                            title = title,
                            poster = poster
                        )
                    } else null
                }
                if (featured.isNotEmpty()) {
                    categories.add(Category(name = Category.FEATURED, list = featured))
                }

                fun parseSection(selector: String, name: String): Category? {
                    val items = document.select("$selector article").mapNotNull { article ->
                        val a = article.selectFirst("h2 a") ?: return@mapNotNull null
                        val title = a.text()
                        val href = a.attr("href")
                        val poster = article.selectFirst("img.poster")?.let {
                            it.attr("data-src").ifEmpty { it.attr("src") }
                        }
                        if (href.contains("/pelicula/")) {
                            Movie(
                                id = href.substringAfter(baseUrl).trim('/'),
                                title = title,
                                poster = poster
                            )
                        } else if (href.contains("/serie/")) {
                            TvShow(
                                id = href.substringAfter(baseUrl).trim('/'),
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

                categories
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
        if (page > 1) return emptyList()
        return try {
            val document = service.getPage("$baseUrl/?s=$query")
            document.select("article.tooltip-content").mapNotNull { article ->
                val a = article.selectFirst("h2 a") ?: return@mapNotNull null
                val title = a.text()
                val href = a.attr("href")
                val poster = article.selectFirst("img.poster")?.let {
                    it.attr("data-src").ifEmpty { it.attr("src") }
                }
                if (href.contains("/pelicula/")) {
                    Movie(
                        id = href.substringAfter(baseUrl).trim('/'),
                        title = title,
                        poster = poster
                    )
                } else if (href.contains("/serie/")) {
                    TvShow(
                        id = href.substringAfter(baseUrl).trim('/'),
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
        return try {
            val url = if (page == 1) "$baseUrl/pelicula/" else "$baseUrl/pelicula/page/$page/"
            val document = service.getPage(url)
            document.select("article.tooltip-content").mapNotNull { article ->
                val a = article.selectFirst("h2 a") ?: return@mapNotNull null
                val href = a.attr("href")
                if (!href.contains("/pelicula/")) return@mapNotNull null
                Movie(
                    id = href.substringAfter(baseUrl).trim('/'),
                    title = a.text(),
                    poster = article.selectFirst("img.poster")?.let {
                        it.attr("data-src").ifEmpty { it.attr("src") }
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return try {
            val url = if (page == 1) "$baseUrl/serie/" else "$baseUrl/serie/page/$page/"
            val document = service.getPage(url)
            document.select("article.tooltip-content").mapNotNull { article ->
                val a = article.selectFirst("h2 a") ?: return@mapNotNull null
                val href = a.attr("href")
                if (!href.contains("/serie/")) return@mapNotNull null
                TvShow(
                    id = href.substringAfter(baseUrl).trim('/'),
                    title = a.text(),
                    poster = article.selectFirst("img.poster")?.let {
                        it.attr("data-src").ifEmpty { it.attr("src") }
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovie(id: String): Movie {
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
        val poster = document.selectFirst("div.self-start figure img")?.attr("src")
        val banner = document.selectFirst("figure.mask-to-l img")?.attr("src")

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
        val poster = document.selectFirst("div.self-start figure img")?.attr("src")
        val banner = document.selectFirst("figure.mask-to-l img")?.attr("src")

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
            val document = service.getPage("$baseUrl/$id/")
            val nonce = document.html().substringAfter("window.wpApiSettings = {", "").substringAfter("\"nonce\":\"", "").substringBefore("\"")
            val playerWrapper = document.selectFirst("#player-wrapper")
            val tmdbId = playerWrapper?.attr("data-id") ?: ""
            val contentType = playerWrapper?.attr("data-type") ?: "" // "movie" or "episode"
            val season = playerWrapper?.attr("data-season")
            val episode = playerWrapper?.attr("data-episode")

            Log.d(TAG, "getServers: id=$id, tmdbId=$tmdbId, nonce=$nonce, type=$contentType")

            if (tmdbId.isEmpty() || nonce.isEmpty()) {
                Log.e(TAG, "getServers: tmdbId or nonce is empty!")
                return emptyList()
            }

            val apiUrl = "$baseUrl/wp-json/ryusaki-sync/v1/get-servers?type=$contentType&tmdb_id=$tmdbId" +
                    (if (season != null && season.isNotEmpty()) "&season=$season" else "") +
                    (if (episode != null && episode.isNotEmpty()) "&episode=$episode" else "")

            Log.d(TAG, "getServers: calling API $apiUrl")

            val request = Request.Builder()
                .url(apiUrl)
                .header("X-WP-Nonce", nonce)
                .build()

            val responseBody = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { it.body?.string() }
            } ?: return emptyList()

            Log.d(TAG, "getServers: API response: $responseBody")
            val serversJson = json.decodeFromString<RyusakiServersResponse>(responseBody)

            val servers = mutableListOf<Video.Server>()
            serversJson.servers?.forEach { ryusakiServer ->
                val name = "${ryusakiServer.serverName} [${ryusakiServer.language.uppercase()}]"
                if (ryusakiServer.type == "embed") {
                    val streamUrl = fetchRyusakiStreamUrl(tmdbId, contentType, ryusakiServer.id, nonce, season, episode)
                    if (streamUrl != null) {
                        servers.add(Video.Server(id = streamUrl, name = name))
                    }
                } else if (ryusakiServer.url != null) {
                    servers.add(Video.Server(id = ryusakiServer.url, name = name))
                }
            }
            servers
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

    override val logo: String get() = "$baseUrl/wp-content/uploads/2025/05/icono-cuevana-3-dark.webp"

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            val url = if (page == 1) "$baseUrl/genero/$id/" else "$baseUrl/genero/$id/page/$page/"
            val document = service.getPage(url)
            val shows = document.select("article.tooltip-content").mapNotNull { article ->
                val a = article.selectFirst("h2 a") ?: return@mapNotNull null
                val href = a.attr("href")
                val poster = article.selectFirst("img.poster")?.let {
                    it.attr("data-src").ifEmpty { it.attr("src") }
                }
                if (href.contains("/pelicula/")) {
                    Movie(
                        id = href.substringAfter(baseUrl).trim('/'),
                        title = a.text(),
                        poster = poster
                    )
                } else if (href.contains("/serie/")) {
                    TvShow(
                        id = href.substringAfter(baseUrl).trim('/'),
                        title = a.text(),
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