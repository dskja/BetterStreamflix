package com.dskja.betterstreamflix.providers

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
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
import com.dskja.betterstreamflix.utils.MeinecloudEmbedHelper
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.TmdbUtils
import com.dskja.betterstreamflix.utils.UserPreferences
import com.dskja.betterstreamflix.utils.WebViewResolver
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url
import java.util.Calendar
import java.util.concurrent.TimeUnit

object KinoGerProvider : Provider, ProviderConfigUrl {

    override val name = "KinoGer"
    override val defaultBaseUrl = "https://kinoger.fun/"
    override val baseUrl: String = defaultBaseUrl
        get() {
            val cachedUrl = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL)
            return cachedUrl.ifBlank { field }
        }
    override val logo = "https://kinoger.fun/templates/kinoger/images/favicon.ico"
    override val language = "de"
    override val changeUrlMutex = Mutex()

    private const val TAG = "KinoGerBypass"
    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

    private var webViewResolver: WebViewResolver? = null
    private val providerMutex = Mutex()

    private interface KinoGerService {
        @GET(".")
        suspend fun getHome(): Document

        @GET("kinofilme-online/")
        suspend fun getMovies(): Document

        @GET("kinofilme-online/page/{page}/")
        suspend fun getMovies(@Path("page") page: Int): Document

        @GET("serienstream-deutsch/")
        suspend fun getTvShows(): Document

        @GET("serienstream-deutsch/page/{page}/")
        suspend fun getTvShows(@Path("page") page: Int): Document

        @GET
        suspend fun getDocument(@Url url: String): Document

        @GET("{path}page/{page}/")
        suspend fun getPage(
            @Path(value = "path", encoded = true) path: String,
            @Path("page") page: Int
        ): Document

        @FormUrlEncoded
        @POST("index.php?do=search")
        suspend fun search(
            @Field("do") doParam: String = "search",
            @Field("subaction") subaction: String = "search",
            @Field("search_start") searchStart: Int,
            @Field("full_search") fullSearch: Int = 0,
            @Field("result_from") resultFrom: Int,
            @Field("story") story: String
        ): Document
    }

    @Volatile
    private var client: OkHttpClient = buildOkHttpClient()
    @Volatile
    private var service = buildService(defaultBaseUrl)
    @Volatile
    private var serviceBaseUrl: String = defaultBaseUrl

    fun init(context: Context) {
        webViewResolver = WebViewResolver(context)
    }

    private fun getResolver(): WebViewResolver {
        return webViewResolver ?: WebViewResolver(BetterStreamflixApp.instance).also {
            webViewResolver = it
        }
    }

    private fun normalizedBaseUrl(): String =
        baseUrl.trim().removeSuffix("/") + "/"

    private fun absoluteUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        if (path.startsWith("//")) return "https:$path"
        val base = normalizedBaseUrl().removeSuffix("/")
        return if (path.startsWith("/")) "$base$path" else "$base/$path"
    }

    private fun providerHost(): String =
        runCatching { normalizedBaseUrl().toHttpUrl().host }.getOrDefault("kinoger.fun")

    fun isKinoGerHost(hostOrUrl: String?): Boolean {
        if (hostOrUrl.isNullOrBlank()) return false
        val host = runCatching {
            if (hostOrUrl.contains("://")) {
                android.net.Uri.parse(hostOrUrl).host
            } else {
                hostOrUrl
            }
        }.getOrNull()
            ?.lowercase()
            ?.removePrefix("www.")
            .orEmpty()
        if (host.isBlank()) return false
        val base = providerHost().lowercase().removePrefix("www.")
        return host == base ||
            host.endsWith(".$base") ||
            host.contains("kinoger")
    }

    private fun isProviderUrl(url: String): Boolean = isKinoGerHost(url)

    private fun buildOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val origin = normalizedBaseUrl().trimEnd('/')
                val request = chain.request().newBuilder()
                    .header("User-Agent", BROWSER_UA)
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
                    )
                    .header("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Referer", "$origin/")
                    .header("Origin", origin)
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .header("Sec-Ch-Ua", "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"133\", \"Chromium\";v=\"133\"")
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .build()
                chain.proceed(request)
            }
            .cookieJar(NetworkClient.cookieJar)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .dns(DnsResolver.doh)
            .build()
    }

    private fun buildService(root: String): KinoGerService {
        val normalized = if (root.endsWith("/")) root else "$root/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(JsoupConverterFactory.create())
            .build()
            .create(KinoGerService::class.java)
    }

    private fun getService(): KinoGerService {
        val currentBase = normalizedBaseUrl()
        val cached = service
        if (serviceBaseUrl == currentBase) return cached
        synchronized(this) {
            if (serviceBaseUrl == currentBase) return service
            client = buildOkHttpClient()
            return buildService(currentBase).also {
                service = it
                serviceBaseUrl = currentBase
            }
        }
    }

    override suspend fun onChangeUrl(forceRefresh: Boolean): String {
        changeUrlMutex.withLock {
            val currentBase = normalizedBaseUrl()
            client = buildOkHttpClient()
            service = buildService(currentBase)
            serviceBaseUrl = currentBase
        }
        return normalizedBaseUrl()
    }

    private fun requiresClearance(html: String): Boolean {
        return html.contains("Just a moment", ignoreCase = true) ||
            html.contains("cf-browser-verification", ignoreCase = true) ||
            html.contains("Checking your browser", ignoreCase = true) ||
            html.contains("cf-mitigated", ignoreCase = true) ||
            html.contains("challenge-platform", ignoreCase = true) &&
            html.contains("cdn-cgi", ignoreCase = true) &&
            !hasUsableContent(html)
    }

    private fun hasUsableContent(html: String): Boolean {
        return html.contains("div.short") ||
            html.contains("class=\"short\"") ||
            html.contains("player-mirrors") ||
            html.contains("ep-menu") ||
            html.contains("news-title") ||
            html.contains("kinofilme") ||
            html.contains("content_text") ||
            html.contains("movieList") ||
            html.contains("owl-iteml-post")
    }

    private suspend fun getDocument(url: String): Document {
        val svc = getService()
        try {
            val document = if (url == normalizedBaseUrl() || url == normalizedBaseUrl().trimEnd('/')) {
                svc.getHome()
            } else {
                svc.getDocument(url)
            }
            if (isProviderUrl(url) && requiresClearance(document.outerHtml())) {
                throw Exception("KinoGer Cloudflare challenge detected")
            }
            return document
        } catch (e: Exception) {
            if (!isProviderUrl(url)) throw e

            val httpCode = (e as? HttpException)?.code()
            val challengeBody = (e as? HttpException)?.response()?.errorBody()?.string().orEmpty()
            val needsWebView = requiresClearance(e.message.orEmpty()) ||
                requiresClearance(challengeBody) ||
                httpCode == 403 ||
                httpCode == 503 ||
                e.message?.contains("Cloudflare", ignoreCase = true) == true

            if (!needsWebView) throw e

            Log.d(TAG, "Using WebView bypass for $url")
            val result = providerMutex.withLock {
                getResolver().getResult(
                    url = url,
                    headers = mapOf(
                        "User-Agent" to BROWSER_UA,
                        "Accept-Language" to "de-DE,de;q=0.9,en;q=0.8",
                    ),
                    completion = { _, htmlText, cookies ->
                        val hasClearance = cookies.contains("cf_clearance=", ignoreCase = true)
                        (!requiresClearance(htmlText) && hasUsableContent(htmlText)) || hasClearance
                    },
                    shouldAllowNavigation = { targetUrl, _ ->
                        runCatching {
                            isProviderUrl(targetUrl) ||
                                targetUrl.contains("/cdn-cgi/", ignoreCase = true) ||
                                targetUrl.contains("challenges.cloudflare.com", ignoreCase = true)
                        }.getOrDefault(false)
                    },
                )
            }
            CookieManager.getInstance().flush()

            runCatching {
                val retried = svc.getDocument(url)
                if (!requiresClearance(retried.outerHtml()) && hasUsableContent(retried.outerHtml())) {
                    return retried
                }
            }

            val parsed = Jsoup.parse(result.html, url).apply { setBaseUri(normalizedBaseUrl()) }
            if (requiresClearance(parsed.outerHtml()) && !hasUsableContent(parsed.outerHtml())) {
                throw Exception(
                    "KinoGer Cloudflare challenge is still active. Complete the check, then retry.",
                    e,
                )
            }
            return parsed
        }
    }

    private fun cleanTitle(raw: String): String = KinoGerHtml.cleanTitle(raw)

    private fun extractYear(raw: String): Int? = KinoGerHtml.extractYear(raw)

    private fun parseShorts(document: Document): List<AppAdapter.Item> =
        KinoGerHtml.parseShorts(document, ::absoluteUrl)

    private fun hosterDisplayName(url: String, fallback: String): String =
        KinoGerHtml.hosterDisplayName(url, fallback)

    private fun normalizeStreamUrl(raw: String): String? =
        KinoGerHtml.normalizeStreamUrl(raw, ::absoluteUrl)

    private suspend fun expandWrapperServers(
        link: String,
        label: String,
    ): List<Video.Server> {
        if (!MeinecloudEmbedHelper.isEmbedWrapper(link)) {
            return listOf(
                Video.Server(
                    id = link,
                    name = hosterDisplayName(link, label),
                    src = link,
                ),
            )
        }
        val expanded = MeinecloudEmbedHelper.expandToServers(link, normalizedBaseUrl())
        if (expanded.isNotEmpty()) {
            return expanded.map { server ->
                server.copy(name = hosterDisplayName(server.src, server.name))
            }
        }
        return listOf(
            Video.Server(
                id = link,
                name = hosterDisplayName(link, label),
                src = link,
            ),
        )
    }

    override suspend fun getHome(): List<Category> {
        val document = getDocument(normalizedBaseUrl())
        val featured = KinoGerHtml.parseFeaturedCarousel(document, ::absoluteUrl)
        val latest = parseShorts(document)
        val sidebar = KinoGerHtml.parseMovieListSidebar(document, ::absoluteUrl)
        if (featured.isEmpty() && latest.isEmpty() && sidebar.isEmpty()) {
            throw Exception("KinoGer home returned no titles (site layout may have changed or CF blocked the scrape).")
        }
        return buildList {
            if (featured.isNotEmpty()) {
                add(Category(name = Category.FEATURED, list = featured))
            } else if (latest.isNotEmpty()) {
                add(Category(name = Category.FEATURED, list = latest))
            }
            if (latest.isNotEmpty() && featured.isNotEmpty()) {
                add(Category(name = "Neueste", list = latest))
            }
            if (sidebar.isNotEmpty()) {
                add(Category(name = "Beliebt", list = sidebar))
            }
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            if (page > 1) return emptyList()
            return listOf(
                Genre(id = absoluteUrl("/kinofilme-online/"), name = "Kinofilme"),
                Genre(id = absoluteUrl("/serienstream-deutsch/"), name = "Serien"),
                Genre(id = absoluteUrl("/aktuelle-kinofilme-im-kino/"), name = "Im Kino"),
            )
        }

        return try {
            // Live site uses GET /?do=search&subaction=search&story=… (Sep 2026 scrape).
            val searchUrl = KinoGerHtml.buildSearchUrl(normalizedBaseUrl(), query, page)
            val document = getDocument(searchUrl)
            parseShorts(document)
        } catch (_: Exception) {
            // Legacy POST fallback if GET layout fails after CF clearance.
            try {
                getDocument(normalizedBaseUrl())
                val resultFrom = (page - 1) * 20 + 1
                parseShorts(
                    getService().search(
                        searchStart = page,
                        resultFrom = resultFrom,
                        story = query,
                    ),
                )
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val url = if (page > 1) {
            absoluteUrl("/kinofilme-online/page/$page/")
        } else {
            absoluteUrl("/kinofilme-online/")
        }
        return parseShorts(getDocument(url)).filterIsInstance<Movie>()
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val url = if (page > 1) {
            absoluteUrl("/serienstream-deutsch/page/$page/")
        } else {
            absoluteUrl("/serienstream-deutsch/")
        }
        return parseShorts(getDocument(url)).filterIsInstance<TvShow>()
    }

    override suspend fun getMovie(id: String): Movie {
        val document = getDocument(absoluteUrl(id))
        val titleRaw = KinoGerHtml.pageTitle(document)
        val title = cleanTitle(titleRaw)
        val year = extractYear(titleRaw)
        val tmdbMovie = TmdbUtils.getMovie(title, year = year, language = language)

        val posterPath = document.selectFirst(".content_text img[src], .full-text img[src], img[itemprop=image]")
            ?.attr("src").orEmpty()
            .takeUnless { it.contains("postinfo-icon", true) || it.contains("favicon", true) }
            .orEmpty()
        val ogPoster = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        val scrapedPoster = listOf(posterPath, ogPoster)
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.let { absoluteUrl(it) }
            ?.takeIf { it.isNotBlank() && !it.endsWith("kinoger.fun/") && !it.endsWith("kinoger.fun") }
        val overview = document.selectFirst(".full-text, .content_text")?.text()?.trim()

        return Movie(
            id = id,
            title = title,
            poster = tmdbMovie?.poster ?: scrapedPoster,
            banner = tmdbMovie?.banner ?: scrapedPoster,
            overview = tmdbMovie?.overview ?: overview,
            released = tmdbMovie?.released?.let { "${it.get(Calendar.YEAR)}" } ?: year?.toString(),
            rating = tmdbMovie?.rating,
            runtime = tmdbMovie?.runtime,
            genres = tmdbMovie?.genres ?: emptyList(),
            cast = tmdbMovie?.cast ?: emptyList(),
            trailer = tmdbMovie?.trailer,
            imdbId = tmdbMovie?.imdbId
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        val document = getDocument(absoluteUrl(id))
        val titleRaw = KinoGerHtml.pageTitle(document)
        val seasonNumber = KinoGerHtml.extractStaffelNumber(titleRaw)
        val titleForTmdb = cleanTitle(titleRaw)
            .replace(Regex("""\s*-\s*Staffel\s+\d+\s*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        val year = extractYear(titleRaw)
        val tmdbTvShow = TmdbUtils.getTvShow(titleForTmdb, year = year, language = language)

        val posterPath = document.selectFirst(".content_text img[src], .full-text img[src], img[itemprop=image]")
            ?.attr("src").orEmpty()
            .takeUnless { it.contains("postinfo-icon", true) || it.contains("favicon", true) }
            .orEmpty()
        val ogPoster = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        val scrapedPoster = listOf(posterPath, ogPoster)
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.let { absoluteUrl(it) }
            ?.takeIf { it.isNotBlank() && !it.endsWith("kinoger.fun/") && !it.endsWith("kinoger.fun") }
        val overview = document.selectFirst(".full-text, .content_text")?.text()?.trim()

        val episodes = KinoGerHtml.parseEpisodes(absoluteUrl(id), seasonNumber, document, emptyList())
        val seasons = listOf(
            Season(
                id = "${absoluteUrl(id)}#season-$seasonNumber",
                number = seasonNumber,
                title = "Staffel $seasonNumber",
                poster = tmdbTvShow?.seasons?.find { it.number == seasonNumber }?.poster,
                episodes = episodes
            )
        )

        return TvShow(
            id = id,
            title = cleanTitle(titleRaw),
            poster = tmdbTvShow?.poster ?: scrapedPoster,
            banner = tmdbTvShow?.banner ?: scrapedPoster,
            overview = tmdbTvShow?.overview ?: overview,
            released = tmdbTvShow?.released?.let { "${it.get(Calendar.YEAR)}" } ?: year?.toString(),
            rating = tmdbTvShow?.rating,
            runtime = tmdbTvShow?.runtime,
            genres = tmdbTvShow?.genres ?: emptyList(),
            cast = tmdbTvShow?.cast ?: emptyList(),
            trailer = tmdbTvShow?.trailer,
            seasons = seasons,
            imdbId = tmdbTvShow?.imdbId
        )
    }

    private fun parseEpisodesFromDocument(
        showUrl: String,
        seasonNumber: Int,
        document: Document,
        tmdbEpisodes: List<Episode>
    ): List<Episode> = KinoGerHtml.parseEpisodes(showUrl, seasonNumber, document, tmdbEpisodes)

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val showUrl = seasonId.substringBefore("#")
        val seasonNumber = seasonId.substringAfter("#season-").toIntOrNull() ?: 1
        val document = getDocument(absoluteUrl(showUrl))

        val titleRaw = KinoGerHtml.pageTitle(document)
        val titleForTmdb = cleanTitle(titleRaw)
            .replace(Regex("""\s*-\s*Staffel\s+\d+\s*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        val year = extractYear(titleRaw)
        val tmdbTvShow = TmdbUtils.getTvShow(titleForTmdb, year = year, language = language)
        val tmdbEpisodes = tmdbTvShow?.let {
            TmdbUtils.getEpisodesBySeason(it.id, seasonNumber, language = language)
        } ?: emptyList()

        return parseEpisodesFromDocument(absoluteUrl(showUrl), seasonNumber, document, tmdbEpisodes)
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            val document = if (page <= 1) {
                getDocument(absoluteUrl(id))
            } else {
                val path = absoluteUrl(id)
                    .removePrefix(normalizedBaseUrl())
                    .removePrefix("/")
                    .removeSuffix("/") + "/"
                getService().getPage(path, page)
            }
            val name = document.selectFirst("h1#news-title, h1, title")?.text()?.trim().orEmpty()
                .ifBlank { id.substringAfterLast('/').ifBlank { "Genre" } }
            val shows = parseShorts(document).filterIsInstance<Show>()
            Genre(id = id, name = name, shows = shows)
        } catch (_: Exception) {
            Genre(id = id, name = "")
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        return People(id = id, name = id.substringAfterLast('/').ifBlank { "Unknown" }, filmography = emptyList())
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return when (videoType) {
            is Video.Type.Movie -> {
                val document = getDocument(absoluteUrl(id))
                KinoGerHtml.parseMovieServers(document, ::absoluteUrl)
                    .flatMap { (link, label) -> expandWrapperServers(link, label) }
                    .distinctBy { it.src }
            }
            is Video.Type.Episode -> {
                val pageUrl = id.substringBefore("#").substringBefore("|")
                val season: Int
                val episode: Int
                when {
                    id.contains("#s") && id.contains("e") -> {
                        val part = id.substringAfter("#")
                        season = part.substringAfter("s").substringBefore("e").toIntOrNull()
                            ?: videoType.season.number
                        episode = part.substringAfter("e").toIntOrNull() ?: videoType.number
                    }
                    id.contains("|") -> {
                        val parts = id.split("|")
                        season = parts.getOrNull(1)?.toIntOrNull() ?: videoType.season.number
                        episode = parts.getOrNull(2)?.toIntOrNull() ?: videoType.number
                    }
                    else -> {
                        season = videoType.season.number
                        episode = videoType.number
                    }
                }

                val document = getDocument(absoluteUrl(pageUrl))
                KinoGerHtml.parseEpisodeServers(document, season, episode, ::absoluteUrl)
                    .flatMap { (link, label) -> expandWrapperServers(link, label) }
                    .distinctBy { it.src }
            }
        }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        var src = server.src.ifBlank { server.id }
        if (MeinecloudEmbedHelper.isEmbedWrapper(src)) {
            src = MeinecloudEmbedHelper.resolveToHosterUrl(src, normalizedBaseUrl())
        }
        return Extractor.extract(src, server)
    }
}
