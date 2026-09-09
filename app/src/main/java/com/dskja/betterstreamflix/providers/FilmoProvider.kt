package com.dskja.betterstreamflix.providers

import MyCookieJar
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.extractors.Extractor
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Genre
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.People
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.utils.TmdbUtils
import com.dskja.betterstreamflix.utils.UserPreferences
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url
import java.net.URLDecoder
import java.util.Calendar
import java.util.concurrent.TimeUnit

object FilmoProvider : Provider, ProviderConfigUrl {

    override val name = "Filmo"
    override val defaultBaseUrl = "https://filmo.to/"
    override val baseUrl: String = defaultBaseUrl
        get() {
            val cachedUrl = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL)
            return cachedUrl.ifBlank { field }
        }
    override val logo = "https://filmo.to/apple-touch-icon.png"
    override val language = "de"
    override val changeUrlMutex = Mutex()

    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val USER_AGENT = "User-Agent: $BROWSER_UA"

    private val cookieJar = MyCookieJar()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private interface FilmoService {
        @Headers(USER_AGENT)
        @GET(".")
        suspend fun getHome(): Document

        @Headers(USER_AGENT)
        @GET("movies")
        suspend fun getMovies(@Query("page") page: Int? = null): Document

        @Headers(USER_AGENT)
        @GET("search")
        suspend fun search(@Query("q") query: String): Document

        @Headers(USER_AGENT, "Accept: application/json")
        @GET("search/suggest")
        suspend fun searchSuggest(@Query("q") query: String): ResponseBody

        @Headers(USER_AGENT)
        @GET("genres")
        suspend fun getGenres(): Document

        @Headers(USER_AGENT)
        @GET
        suspend fun getDocument(@Url url: String): Document

        @Headers(
            USER_AGENT,
            "Content-Type: application/json",
            "Accept: application/json",
            "X-Requested-With: XMLHttpRequest"
        )
        @POST("n")
        suspend fun mint(
            @Header("X-CSRF-TOKEN") csrf: String,
            @Header("X-XSRF-TOKEN") xsrf: String,
            @Header("Origin") origin: String,
            @Header("Referer") referer: String,
            @Body body: RequestBody
        ): ResponseBody

        @Headers(USER_AGENT)
        @GET
        suspend fun openToken(@Url url: String): Response<ResponseBody>

        companion object {
            fun build(baseUrl: String, jar: MyCookieJar): FilmoService {
                val client = OkHttpClient.Builder()
                    .cookieJar(jar)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()

                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                    .create(FilmoService::class.java)
            }
        }
    }

    @Volatile
    private var service = FilmoService.build(defaultBaseUrl, cookieJar)
    @Volatile
    private var serviceBaseUrl: String = defaultBaseUrl
    @Volatile
    private var cachedCsrf: String = ""

    private fun normalizedBaseUrl(): String =
        baseUrl.trim().removeSuffix("/") + "/"

    private fun origin(): String = normalizedBaseUrl().removeSuffix("/")

    private fun absoluteUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        if (path.startsWith("//")) return "https:$path"
        val base = origin()
        return if (path.startsWith("/")) "$base$path" else "$base/$path"
    }

    private fun getService(): FilmoService {
        val currentBase = normalizedBaseUrl()
        val cached = service
        if (serviceBaseUrl == currentBase) return cached
        synchronized(this) {
            if (serviceBaseUrl == currentBase) return service
            return FilmoService.build(currentBase, cookieJar).also {
                service = it
                serviceBaseUrl = currentBase
                cachedCsrf = ""
            }
        }
    }

    override suspend fun onChangeUrl(forceRefresh: Boolean): String {
        changeUrlMutex.withLock {
            val currentBase = normalizedBaseUrl()
            service = FilmoService.build(currentBase, cookieJar)
            serviceBaseUrl = currentBase
            cachedCsrf = ""
        }
        return normalizedBaseUrl()
    }

    private fun readXsrfToken(): String {
        val cookies = cookieJar.loadForRequest(origin().toHttpUrl())
        val raw = cookies.firstOrNull { it.name.equals("XSRF-TOKEN", ignoreCase = true) }?.value
            ?: return ""
        return runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrDefault(raw)
    }

    private fun extractCsrf(document: Document): String {
        val meta = document.selectFirst("meta[name=csrf-token]")?.attr("content")?.trim().orEmpty()
        if (meta.isNotBlank()) {
            cachedCsrf = meta
            return meta
        }
        return cachedCsrf
    }

    private suspend fun ensureSession(refererPath: String = "/"): Pair<String, String> {
        val doc = getService().getDocument(absoluteUrl(refererPath))
        val csrf = extractCsrf(doc)
        val xsrf = readXsrfToken()
        return csrf to xsrf
    }

    private fun parseVideoCard(el: Element): Movie? {
        val href = el.attr("href").trim().ifBlank {
            el.selectFirst("a[href*=/movies/]")?.attr("href")?.trim().orEmpty()
        }
        if (href.isBlank() || !href.contains("/movies/")) return null
        val title = el.selectFirst(".video-card__title, .movie-poster-grid-card__title, h2, h3")
            ?.text()?.trim()
            ?: el.selectFirst("img")?.attr("alt")?.trim()
            ?: href.substringAfterLast('/').replace('-', ' ')
        if (title.isBlank()) return null
        val poster = el.selectFirst("img")?.let { img ->
            img.attr("src").ifBlank { img.attr("data-src") }
        }.orEmpty()
        return Movie(
            id = absoluteUrl(href),
            title = title,
            poster = absoluteUrl(poster)
        )
    }

    private fun parseMovieCards(document: Document): List<Movie> {
        val cards = document.select("a.video-card[href*=/movies/], a.movie-poster-grid-card[href*=/movies/], a.popular-spotlight-card__link[href*=/movies/]")
        return cards.mapNotNull { parseVideoCard(it) }.distinctBy { it.id }
    }

    override suspend fun getHome(): List<Category> {
        val document = getService().getHome()
        extractCsrf(document)
        val movies = parseMovieCards(document)
        if (movies.isEmpty()) return emptyList()
        return listOf(Category(name = "Filme", list = movies))
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            if (page > 1) return emptyList()
            return try {
                val document = getService().getGenres()
                extractCsrf(document)
                document.select("a[href*=/genres/]").mapNotNull { a ->
                    val href = a.attr("href").trim()
                    val name = a.text().trim().ifBlank {
                        href.substringAfterLast('/').replace('-', ' ')
                            .replaceFirstChar { it.uppercase() }
                    }
                    if (href.isBlank() || name.isBlank()) return@mapNotNull null
                    Genre(id = absoluteUrl(href), name = name)
                }.distinctBy { it.id }
            } catch (_: Exception) {
                emptyList()
            }
        }

        if (page > 1) return emptyList()

        return try {
            val body = getService().searchSuggest(query).string()
            val movies = JSONObject(body).optJSONArray("movies") ?: return emptyList()
            buildList {
                for (i in 0 until movies.length()) {
                    val item = movies.optJSONObject(i) ?: continue
                    val title = item.optString("title").trim()
                    val url = item.optString("url").trim()
                    if (title.isBlank() || url.isBlank()) continue
                    add(Movie(id = absoluteUrl(url), title = title))
                }
            }
        } catch (_: Exception) {
            try {
                val document = getService().search(query)
                extractCsrf(document)
                parseMovieCards(document)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val document = getService().getMovies(if (page <= 1) null else page)
        extractCsrf(document)
        return parseMovieCards(document)
    }

    override suspend fun getTvShows(page: Int): List<TvShow> = emptyList()

    override suspend fun getMovie(id: String): Movie {
        val document = getService().getDocument(absoluteUrl(id))
        extractCsrf(document)

        val title = document.selectFirst("h1")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.substringBefore(" jetzt")
                ?.substringBefore(" kostenlos")
                ?.trim()
            ?: id.substringAfterLast('/')
        val tmdbMovie = TmdbUtils.getMovie(title, language = language)

        val poster = document.selectFirst("img[src*=/img/poster/], meta[property=og:image]")
            ?.let { el ->
                if (el.tagName() == "meta") el.attr("content") else el.attr("src")
            }.orEmpty()
        val overview = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val year = Regex("""Erscheinungsdatum\s+(\d{4})""")
            .find(document.text())?.groupValues?.get(1)
            ?: Regex("""\b(19|20)\d{2}\b""").find(
                document.selectFirst(".ft-meta, .movie-detail")?.text().orEmpty()
            )?.value
        val runtime = Regex("""Laufzeit\s+(\d+)\s*Min""", RegexOption.IGNORE_CASE)
            .find(document.text())?.groupValues?.get(1)?.toIntOrNull()
        val rating = Regex("""Bewertung\s+([\d.]+)\s*/\s*10""", RegexOption.IGNORE_CASE)
            .find(document.text())?.groupValues?.get(1)?.toDoubleOrNull()

        val genres = document.select("a[href*=/genres/]").mapNotNull { a ->
            val name = a.text().trim()
            if (name.isBlank()) null else Genre(id = absoluteUrl(a.attr("href")), name = name)
        }.distinctBy { it.name }

        return Movie(
            id = id,
            title = title,
            poster = tmdbMovie?.poster ?: absoluteUrl(poster),
            banner = tmdbMovie?.banner,
            overview = tmdbMovie?.overview ?: overview,
            released = tmdbMovie?.released?.let { "${it.get(Calendar.YEAR)}" } ?: year,
            runtime = tmdbMovie?.runtime ?: runtime,
            rating = tmdbMovie?.rating ?: rating,
            genres = tmdbMovie?.genres ?: genres,
            cast = tmdbMovie?.cast ?: emptyList(),
            trailer = tmdbMovie?.trailer,
            imdbId = tmdbMovie?.imdbId
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        throw Exception("TV shows not supported")
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> = emptyList()

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            val url = if (page <= 1) {
                absoluteUrl(id)
            } else {
                val base = absoluteUrl(id)
                if (base.contains("?")) "$base&page=$page" else "$base?page=$page"
            }
            val document = getService().getDocument(url)
            extractCsrf(document)
            val name = document.selectFirst("h1, h2")?.text()?.trim()
                ?: id.substringAfterLast('/').replace('-', ' ').replaceFirstChar { it.uppercase() }
            Genre(id = id, name = name, shows = parseMovieCards(document))
        } catch (_: Exception) {
            Genre(id = id, name = "")
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        return People(id = id, name = id.substringAfterLast('/').ifBlank { "Unknown" }, filmography = emptyList())
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        if (videoType !is Video.Type.Movie) return emptyList()

        val document = getService().getDocument(absoluteUrl(id))
        extractCsrf(document)

        val chips = document.select("[data-provider-chip][data-movie-link-id][data-p]")
        val servers = mutableListOf<Video.Server>()
        val seenLinkIds = mutableSetOf<String>()

        chips.forEach { chip ->
            val linkId = chip.attr("data-movie-link-id").trim()
            val payload = chip.attr("data-p").trim()
            if (payload.isBlank()) return@forEach
            if (linkId.isNotBlank() && !seenLinkIds.add(linkId)) return@forEach

            val name = chip.attr("aria-label").trim()
                .ifBlank { chip.selectFirst(".provider-chip__name")?.text()?.trim().orEmpty() }
                .ifBlank { "Server" }

            servers.add(
                Video.Server(
                    id = payload,
                    name = name,
                    src = payload
                )
            )
        }

        return servers
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val payload = server.src.ifBlank { server.id }
        if (payload.isBlank()) throw Exception("Missing Filmo stream payload")

        val (csrf, xsrf) = ensureSession("/")
        if (csrf.isBlank() || xsrf.isBlank()) {
            throw Exception("Filmo CSRF session unavailable")
        }

        val mintBody = JSONObject().put("p", payload).toString().toRequestBody(jsonMedia)
        val mintResponse = getService().mint(
            csrf = csrf,
            xsrf = xsrf,
            origin = origin(),
            referer = origin() + "/",
            body = mintBody
        ).string()
        val token = JSONObject(mintResponse).optString("x").trim()
        if (token.isBlank()) throw Exception("Filmo mint token missing")

        val tokenUrl = absoluteUrl("/n/$token")
        val response = getService().openToken(tokenUrl)
        val location = response.headers()["Location"]
            ?: response.raw().header("Location")
            ?: throw Exception("Filmo hoster redirect missing")

        val hosterUrl = when {
            location.startsWith("//") -> "https:$location"
            location.startsWith("http") -> location
            else -> absoluteUrl(location)
        }

        return Extractor.extract(hosterUrl)
    }
}
