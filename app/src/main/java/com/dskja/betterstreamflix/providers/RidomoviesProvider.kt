package com.dskja.betterstreamflix.providers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import com.dskja.betterstreamflix.utils.UserPreferences

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.dskja.betterstreamflix.BetterStreamflixApp
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.extractors.Extractor
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Genre
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.People
import com.dskja.betterstreamflix.models.Season
import com.dskja.betterstreamflix.models.Show
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.utils.DnsResolver
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.WebViewResolver
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

object RidomoviesProvider : Provider, ProviderConfigUrl {

    const val URL = "https://ridomovies.su/"
    override val defaultBaseUrl = "https://ridomovies.su/"
    override val baseUrl: String
        get() = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL).ifBlank { defaultBaseUrl }
    override val changeUrlMutex = Mutex()

    override suspend fun onChangeUrl(forceRefresh: Boolean): String = changeUrlMutex.withLock {
        service = Service.build()
        baseUrl
    }
    override val name = "Ridomovies"
    override val logo = "$URL/uploads/logos/hero_logo-1-1769040020-ab537326.png"
    override val language = "en"

    private const val TAG = "RidomoviesBypass"
    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private var service = Service.build()
    private var currentSlug: String? = null
    private var webViewResolver: WebViewResolver? = null
    private val providerMutex = Mutex()

    fun init(context: Context) {
        webViewResolver = WebViewResolver(context)
    }

    private fun getResolver(): WebViewResolver {
        return webViewResolver ?: WebViewResolver(BetterStreamflixApp.instance).also {
            webViewResolver = it
        }
    }

    private fun requiresClearance(html: String): Boolean {
        return html.contains("Just a moment", ignoreCase = true) ||
            html.contains("cf-browser-verification", ignoreCase = true) ||
            html.contains("cf-mitigated", ignoreCase = true) ||
            html.contains("challenge-platform", ignoreCase = true) ||
            html.contains("Checking your browser", ignoreCase = true)
    }

    private suspend fun getHtmlDocument(url: String): Document {
        val absolute = when {
            url.startsWith("http") -> url
            else -> "${URL.trimEnd('/')}/${url.trimStart('/')}"
        }
        try {
            val document = service.getPage(url)
            if (requiresClearance(document.outerHtml())) {
                throw Exception("Ridomovies Cloudflare challenge detected")
            }
            return document
        } catch (e: Exception) {
            val httpCode = (e as? retrofit2.HttpException)?.code()
            val challengeBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string().orEmpty()
            val needsWebView = requiresClearance(e.message.orEmpty()) ||
                requiresClearance(challengeBody) ||
                httpCode == 403 ||
                httpCode == 503 ||
                e.message?.contains("Cloudflare", ignoreCase = true) == true

            if (!needsWebView) throw e

            Log.d(TAG, "Using WebView bypass for $absolute")
            val result = providerMutex.withLock {
                getResolver().getResult(
                    url = absolute,
                    headers = mapOf(
                        "User-Agent" to BROWSER_UA,
                        "Accept-Language" to "en-US,en;q=0.9",
                    ),
                    completion = { _, htmlText, _ ->
                        !requiresClearance(htmlText) &&
                            (htmlText.contains("player-cover") ||
                                htmlText.contains("data-embed") ||
                                htmlText.contains("movie-card") ||
                                htmlText.contains("highlight-card") ||
                                htmlText.contains("server-dropdown"))
                    }
                )
            }
            CookieManager.getInstance().flush()
            return Jsoup.parse(result.html, absolute).apply { setBaseUri(URL) }
        }
    }

    private fun fixUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return "${URL.trimEnd('/')}/${path.trimStart('/')}"
    }

    override suspend fun getHome(): List<Category> {
        val document = getHtmlDocument("home-rd1")
        val tvResponse = service.getLatestSeries(1)

        val categories = mutableListOf<Category>()

        categories.add(
            Category(
                name = Category.FEATURED,
                list = document.select("div.highlight-card").mapNotNull {
                    val href = it.selectFirst("a")?.attr("href") ?: ""
                    val id = href.trimEnd('/').substringAfterLast("/")
                    if (id.isEmpty()) return@mapNotNull null
                    val title = it.selectFirst("h2, h3")?.text() ?: ""
                    val overview = it.selectFirst("p.highlight-desc")?.text()
                    val banner = fixUrl(it.selectFirst("img")?.attr("src"))

                    if (href.contains("/movie/")) {
                        Movie(
                            id = id,
                            title = title,
                            overview = overview,
                            banner = banner,
                        )
                    } else if (href.contains("/tv/")) {
                        TvShow(
                            id = id,
                            title = title,
                            overview = overview,
                            banner = banner,
                        )
                    } else {
                        null
                    }
                }
            )
        )

        categories.add(
            Category(
                name = "Latest Movies",
                list = document.select("div.movie-card").mapNotNull {
                    val href = it.selectFirst("a")?.attr("href") ?: ""
                    if (!href.contains("/movie/")) return@mapNotNull null
                    Movie(
                        id = href.trimEnd('/').substringAfterLast("/"),
                        title = it.selectFirst(".movie-title")?.text() ?: "",
                        released = it.selectFirst(".movie-year")?.text(),
                        quality = it.selectFirst(".badge-quality")?.text()?.takeIf { q -> q.isNotBlank() },
                        poster = fixUrl(it.selectFirst("img")?.attr("src")),
                    )
                }
            )
        )

        categories.add(
            Category(
                name = "Latest TV Series",
                list = tvResponse.series.map {
                    TvShow(
                        id = it.slug,
                        title = it.title,
                        released = it.releaseDate?.substringBefore("-"),
                        quality = it.quality,
                        poster = fixUrl(it.posterPath),
                    )
                }
            )
        )

        return categories

    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isEmpty()) {
            val document = getHtmlDocument("home-rd1")
            val genres = document.select(".dropdown-grid.genres-grid a, .mobile-accordion-content a.mobile-accordion-link").mapNotNull { a ->
                val href = a.attr("href")
                if (!href.contains("/genre/")) return@mapNotNull null
                val slug = href.substringAfter("/genre/").substringBefore("/")
                val name = a.text()
                if (slug.isEmpty()) return@mapNotNull null
                Genre(id = slug, name = name)
            }.distinctBy { it.id }

            return genres
        }

        val response = service.search(query, page)

        val results = response.data.mapNotNull {
            val slug = it.slug ?: it.slugEn ?: return@mapNotNull null
            when (it.type) {
                "movie" -> Movie(
                    id = slug,
                    title = it.title,
                    released = it.releaseDate?.substringBefore("-"),
                    quality = it.quality,
                    poster = fixUrl(it.posterPath),
                )
                "tv" -> TvShow(
                    id = slug,
                    title = it.title,
                    released = it.releaseDate?.substringBefore("-"),
                    quality = it.quality,
                    poster = fixUrl(it.posterPath),
                )
                else -> null
            }
        }

        return results
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val response = service.getLatestMovies(page)

        val movies = response.movies.map {
            Movie(
                id = it.slug,
                title = it.title,
                released = it.releaseDate?.substringBefore("-"),
                quality = it.quality,
                poster = fixUrl(it.posterPath),
            )
        }

        return movies
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val response = service.getLatestSeries(page)

        val tvShows = response.series.map {
            TvShow(
                id = it.slug,
                title = it.title,
                released = it.releaseDate?.substringBefore("-"),
                quality = it.quality,
                poster = fixUrl(it.posterPath),
            )
        }

        return tvShows
    }

    override suspend fun getMovie(id: String): Movie {
        val document = getHtmlDocument("movie/$id")
        val finalId = currentSlug ?: id

        val h1Text = document.selectFirst("h1")?.text() ?: ""
        val title = h1Text.substringBeforeLast("(").trim()
        val year = if ("(" in h1Text) h1Text.substringAfterLast("(").trimEnd(')') else null

        val movie = Movie(
            id = finalId,
            title = title,
            overview = document.selectFirst(".movie-overview")
                ?.text(),
            released = year,
            runtime = document.select("span.meta-info")
                .find { it.selectFirst("strong")?.text()?.contains("Duration") == true }
                ?.ownText()?.let {
                    val hours = it.substringBefore("h").filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                    val minutes = it.substringAfter("h").substringBefore("m").filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                    if (hours * 60 + minutes != 0) hours * 60 + minutes else null
                },
            rating = document.selectFirst(".imdb-score")
                ?.text()?.toDoubleOrNull(),
            poster = fixUrl(document.selectFirst("img.movie-poster-img")?.attr("src")),

            genres = document.select(".genre-links a").map {
                Genre(
                    id = it.attr("href").split("/").getOrNull(2) ?: "",
                    name = it.text(),
                )
            },
            cast = document.select(".cast-card").map {
                People(
                    id = "",
                    name = it.selectFirst(".cast-name")
                        ?.text()
                        ?: "",
                    image = fixUrl(it.selectFirst("img.cast-photo")?.attr("src")),
                )
            },
        )

        return movie
    }

    override suspend fun getTvShow(id: String): TvShow {
        val document = getHtmlDocument("tv/$id")
        val finalId = currentSlug ?: id

        val h1Text = document.selectFirst("h1")?.text() ?: ""
        val title = h1Text.substringBeforeLast("(").trim()
        val year = if ("(" in h1Text) h1Text.substringAfterLast("(").trimEnd(')') else null

        val tvShow = TvShow(
            id = finalId,
            title = title,
            overview = document.selectFirst(".movie-overview")
                ?.text(),
            released = year,
            runtime = document.select("span.meta-info")
                .find { it.selectFirst("strong")?.text()?.contains("Duration") == true }
                ?.ownText()?.let {
                    val hours = it.substringBefore("h").filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                    val minutes = it.substringAfter("h").substringBefore("m").filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                    if (hours * 60 + minutes != 0) hours * 60 + minutes else null
                },
            rating = document.selectFirst(".imdb-score")
                ?.text()?.toDoubleOrNull(),
            poster = fixUrl(document.selectFirst("img[class*='poster']")?.attr("src")),

            seasons = document.select(".season-tabs button").mapNotNull { tab ->
                val seasonNum = tab.attr("data-season-number").toIntOrNull()
                    ?: return@mapNotNull null
                Season(
                    id = "$finalId/$seasonNum",
                    number = seasonNum,
                    title = "Season $seasonNum",
                )
            }.ifEmpty {
                listOf(Season(id = "$finalId/1", number = 1, title = "Season 1"))
            },
            genres = document.select(".genre-links a").map {
                Genre(
                    id = it.attr("href").split("/").getOrNull(2) ?: "",
                    name = it.text(),
                )
            },
            cast = document.select(".cast-card").map {
                People(
                    id = "",
                    name = it.selectFirst(".cast-name")
                        ?.text()
                        ?: "",
                    image = fixUrl(it.selectFirst("img.cast-photo")?.attr("src")),
                )
            },
        )

        return tvShow
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val parts = seasonId.split("/")
        val tvShowSlug = parts.getOrElse(0) { seasonId }
        val seasonNum = parts.getOrNull(1)?.toIntOrNull() ?: 1

        val document = getHtmlDocument("tv/$tvShowSlug/season-$seasonNum/episode-1")

        val episodes = document.select(".episodes-grid .episode-link").mapNotNull { ep ->
            val href = ep.attr("href")
            val epTitleRow = ep.selectFirst(".ep-title-row")?.text() ?: ""
            val epTitle = ep.selectFirst(".ep-name-row")?.text()
            val epDate = ep.selectFirst(".ep-date")?.text()

            val epNum = epTitleRow.substringAfterLast("Episode ").trim().toIntOrNull()
                ?: href.substringAfterLast("episode-").toIntOrNull()
                ?: return@mapNotNull null

            Episode(
                id = href.trimStart('/'),
                number = epNum,
                title = epTitle ?: "Episode $epNum",
                released = epDate,
            )
        }.distinctBy { it.number }

        return episodes
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            coroutineScope {
                val moviesDeferred = async {
                    try {
                        if (page > 1) service.getGenreMoviesPage(id, page) else service.getGenreMovies(id)
                    } catch (e: Exception) { null }
                }
                val tvDeferred = async {
                    try {
                        if (page > 1) service.getGenreSeriesPage(id, page) else service.getGenreSeries(id)
                    } catch (e: Exception) { null }
                }

                val movieItems = moviesDeferred.await()?.movies.orEmpty()
                val tvItems = tvDeferred.await()?.movies.orEmpty()

                val shows = mutableListOf<Show>()

                movieItems.forEach { item ->
                    shows.add(
                        Movie(
                            id = item.slug,
                            title = item.title,
                            released = item.releaseDate?.substringBefore("-"),
                            quality = item.quality,
                            poster = fixUrl(item.posterPath),
                        )
                    )
                }

                tvItems.forEach { item ->
                    shows.add(
                        TvShow(
                            id = item.slug,
                            title = item.title,
                            released = item.releaseDate?.substringBefore("-"),
                            quality = item.quality,
                            poster = fixUrl(item.posterPath),
                        )
                    )
                }

                Genre(id = id, name = id.replaceFirstChar { it.uppercase() }, shows = shows)
            }
        } catch (e: Exception) {
            Genre(id = id, name = id.replaceFirstChar { it.uppercase() }, shows = emptyList())
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        throw Exception("Not yet implemented")
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val pageUrl = when (videoType) {
            is Video.Type.Episode -> id.trimStart('/')
            is Video.Type.Movie -> "movie/$id"
        }

        val document = getHtmlDocument(pageUrl)
        val servers = mutableListOf<Video.Server>()

        fun extractIframeSrc(rawHtml: String): String? {
            if (rawHtml.isBlank()) return null
            // data-embed may contain a full iframe HTML snippet or a bare URL
            val trimmed = rawHtml.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("//")) {
                return if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
            }
            val iframe = Jsoup.parse(rawHtml).selectFirst("iframe")
            val src = iframe?.attr("src")?.ifBlank { iframe.attr("data-src") }
            return when {
                src.isNullOrBlank() -> null
                src.startsWith("//") -> "https:$src"
                else -> src
            }
        }

        // #player-cover is always present (movies and episodes)
        document.selectFirst("#player-cover[data-embed], [data-embed]")?.let { el ->
            val src = extractIframeSrc(el.attr("data-embed"))
            if (!src.isNullOrBlank()) {
                servers.add(Video.Server(id = src, name = "Server 1", src = src))
            }
        }

        // Dropdown buttons appear only on multi-server movies — add extras deduplicating against player-cover
        document.select(".server-dropdown-item[data-server-embed], [data-server-embed], .servers a[data-embed]").forEachIndexed { idx, btn ->
            val src = extractIframeSrc(btn.attr("data-server-embed").ifBlank { btn.attr("data-embed") })
            val label = btn.text().ifBlank { "Server ${idx + 1}" }
            if (!src.isNullOrBlank() && servers.none { it.src == src }) {
                servers.add(Video.Server(id = src, name = label, src = src))
            }
        }

        if (servers.isEmpty()) {
            document.select("iframe[src], iframe[data-src]").forEachIndexed { idx, iframe ->
                val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }
                val absolute = when {
                    src.startsWith("//") -> "https:$src"
                    else -> src
                }
                if (absolute.isNotBlank()) {
                    servers.add(Video.Server(id = absolute, name = "Server ${idx + 1}", src = absolute))
                }
            }
        }

        // Some pages stash embeds in inline scripts / data attributes.
        if (servers.isEmpty()) {
            val html = document.html()
            Regex(
                """(?:data-(?:server-)?embed|src)\s*[:=]\s*["'](https?://[^"']+)["']""",
                RegexOption.IGNORE_CASE,
            ).findAll(html).forEachIndexed { idx, match ->
                val src = match.groupValues.getOrNull(1).orEmpty()
                if (src.isNotBlank() && servers.none { it.src == src }) {
                    servers.add(Video.Server(id = src, name = "Embed ${idx + 1}", src = src))
                }
            }
        }

        if (servers.isEmpty() && requiresClearance(document.html())) {
            throw Exception("Ridomovies blocked by Cloudflare; open the site on-device to clear challenge")
        }

        return servers.distinctBy { it.src.ifBlank { it.id } }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.src.ifBlank { server.id }, server)
    }


    private interface Service {

        companion object {
            fun build(): Service {
                val client = OkHttpClient.Builder()
                    .dns(DnsResolver.doh)
                    .cookieJar(NetworkClient.cookieJar)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .addInterceptor { chain ->
                        val request = chain.request()
                        val response = chain.proceed(request)

                        val requestUrl = request.url.toString()
                        val responseUrl = response.request.url.toString()

                        if (requestUrl != responseUrl) {
                            currentSlug = responseUrl.substringBefore("?").substringBefore("#")
                                .trimEnd('/').substringAfterLast("/")
                        }

                        response
                    }
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                            .header("Accept-Language", "en-US,en;q=0.9")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                            .header("Referer", URL)
                            .header("Origin", URL.trimEnd('/'))
                            .header("Sec-CH-UA", "\"Chromium\";v=\"131\", \"Not-A.Brand\";v=\"24\", \"Google Chrome\";v=\"131\"")
                            .header("Sec-CH-UA-Mobile", "?0")
                            .header("Sec-CH-UA-Platform", "\"Windows\"")
                            .header("Sec-Fetch-Dest", "document")
                            .header("Sec-Fetch-Mode", "navigate")
                            .header("Sec-Fetch-Site", "same-origin")
                            .header("Upgrade-Insecure-Requests", "1")
                            .build()
                        chain.proceed(request)
                    }
                    .connectTimeout(25, TimeUnit.SECONDS)
                    .readTimeout(35, TimeUnit.SECONDS)
                    .callTimeout(50, TimeUnit.SECONDS)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(URL)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(Service::class.java)
            }
        }


        @GET("home-rd1")
        suspend fun getHome(): Document

        @GET
        suspend fun getPage(@Url url: String): Document

        @GET("movie/{slug}")
        suspend fun getMovie(
            @Path("slug") slug: String,
        ): Document

        @GET("tv/{slug}")
        suspend fun getTv(
            @Path("slug") slug: String,
        ): Document

        @GET("tv/{slug}/season-{season}/episode-{episode}")
        suspend fun getEpisodePage(
            @Path("slug") slug: String,
            @Path("season") season: Int,
            @Path("episode") episode: Int,
        ): Document

        @GET("api/movies/latest")
        suspend fun getLatestMovies(
            @Query("page") page: Int = 1,
        ): MoviesResponse

        @GET("api/tv/latest")
        suspend fun getLatestSeries(
            @Query("page") page: Int = 1,
        ): SeriesResponse

        @GET("api/search")
        suspend fun search(
            @Query("q") q: String,
            @Query("page") page: Int = 1,
            @Query("lang") lang: String = "en",
            @Query("limit") limit: Int = 20,
        ): SearchResponse

        @Headers("X-Requested-With: XMLHttpRequest")
        @GET("genre/{genre}/movie")
        suspend fun getGenreMovies(
            @Path("genre") genre: String,
        ): MoviesResponse

        @Headers("X-Requested-With: XMLHttpRequest")
        @GET("genre/{genre}/movie/page-{page}")
        suspend fun getGenreMoviesPage(
            @Path("genre") genre: String,
            @Path("page") page: Int,
        ): MoviesResponse

        @Headers("X-Requested-With: XMLHttpRequest")
        @GET("genre/{genre}/tv")
        suspend fun getGenreSeries(
            @Path("genre") genre: String,
        ): MoviesResponse

        @Headers("X-Requested-With: XMLHttpRequest")
        @GET("genre/{genre}/tv/page-{page}")
        suspend fun getGenreSeriesPage(
            @Path("genre") genre: String,
            @Path("page") page: Int,
        ): MoviesResponse


        data class MoviesResponse(
            val success: Boolean,
            val movies: List<ShowItem>,
            val page: Int,
            val hasMore: Boolean,
        )

        data class SeriesResponse(
            val success: Boolean,
            val series: List<ShowItem>,
            val page: Int,
            val hasMore: Boolean,
        )

        data class ShowItem(
            val slug: String,
            val title: String,
            @SerializedName("poster_path") val posterPath: String?,
            @SerializedName("release_date") val releaseDate: String?,
            val quality: String?,
        )

        data class SearchResponse(
            val status: Boolean,
            val data: List<SearchItem>,
        )

        data class SearchItem(
            val id: Int?,
            val slug: String?,
            @SerializedName("slug_en") val slugEn: String?,
            val type: String?,
            val title: String,
            @SerializedName("poster_path") val posterPath: String?,
            @SerializedName("release_date") val releaseDate: String?,
            val quality: String?,
        )
    }
}