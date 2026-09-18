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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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
            html.contains("content_text")
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

    private fun cleanTitle(raw: String): String =
        raw.replace(Regex("""\s*\(\d{4}\)\s*$"""), "").trim()

    private fun extractYear(raw: String): Int? =
        Regex("""\((\d{4})\)""").find(raw)?.groupValues?.get(1)?.toIntOrNull()

    private fun isSeriesCard(el: Element, title: String): Boolean {
        if (el.selectFirst(".serie-num") != null) return true
        if (title.contains("Staffel", ignoreCase = true)) return true
        val cats = el.select(".content_text").text()
        return cats.contains("Serien", ignoreCase = true)
    }

    private fun parseShort(el: Element): AppAdapter.Item? {
        val link = el.selectFirst(".title a[href$=.html]")
            ?: el.selectFirst("a[href$=.html]")
            ?: return null
        val href = link.attr("href").trim()
        if (href.isBlank()) return null
        val titleRaw = link.text().trim().ifBlank {
            el.selectFirst(".title")?.text()?.trim().orEmpty()
        }
        if (titleRaw.isBlank()) return null
        val posterPath = el.selectFirst(".content_text img, img")?.attr("src").orEmpty()
        val poster = absoluteUrl(posterPath)
        val title = cleanTitle(titleRaw)

        return if (isSeriesCard(el, titleRaw)) {
            TvShow(id = absoluteUrl(href), title = title, poster = poster)
        } else {
            Movie(id = absoluteUrl(href), title = title, poster = poster)
        }
    }

    private fun parseShorts(document: Document): List<AppAdapter.Item> =
        document.select("div.short").mapNotNull { parseShort(it) }
            .ifEmpty {
                document.select("article, .movie-item, .item").mapNotNull { parseShort(it) }
            }

    private fun hosterDisplayName(url: String, fallback: String): String {
        val host = runCatching {
            url.toHttpUrlOrNull()?.host
                ?.removePrefix("www.")
                ?.substringBefore('.')
        }.getOrNull().orEmpty()
        return when {
            host.equals("voe", ignoreCase = true) -> "Voe"
            host.equals("meinecloud", ignoreCase = true) -> "Meinecloud"
            host.equals("vidara", ignoreCase = true) -> "Vidara"
            host.equals("firestream", ignoreCase = true) -> "Firestream"
            host.equals("mixdrop", ignoreCase = true) -> "Mixdrop"
            host.equals("streamtape", ignoreCase = true) -> "Streamtape"
            host.equals("dood", ignoreCase = true) || host.startsWith("dood", ignoreCase = true) -> "Doodstream"
            fallback.isNotBlank() -> fallback
            host.isNotBlank() -> host.replaceFirstChar { it.uppercase() }
            else -> "Server"
        }
    }

    private fun normalizeStreamUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.contains("/vod/vpn", ignoreCase = true)) return null
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http") -> trimmed
            trimmed.startsWith("/") -> absoluteUrl(trimmed)
            else -> MeinecloudEmbedHelper.decodeDataLink(trimmed)?.let { decoded ->
                when {
                    decoded.startsWith("//") -> "https:$decoded"
                    decoded.startsWith("http") -> decoded
                    else -> null
                }
            }
        }
    }

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
        // Keep wrapper as last-resort — getVideo will try to resolve again.
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
        val items = parseShorts(document)
        if (items.isEmpty()) {
            throw Exception("KinoGer home returned no titles (site layout may have changed or CF blocked the scrape).")
        }
        return listOf(Category(name = Category.FEATURED, list = items))
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

        val resultFrom = (page - 1) * 20 + 1
        return try {
            // Search goes through getDocument path via service; CF may block POST —
            // fall back to fetching search results page after clearance seed.
            val document = try {
                getService().search(
                    searchStart = page,
                    resultFrom = resultFrom,
                    story = query,
                )
            } catch (e: Exception) {
                // Seed clearance first, then retry search.
                getDocument(normalizedBaseUrl())
                getService().search(
                    searchStart = page,
                    resultFrom = resultFrom,
                    story = query,
                )
            }
            if (requiresClearance(document.outerHtml())) {
                getDocument(normalizedBaseUrl())
                parseShorts(
                    getService().search(
                        searchStart = page,
                        resultFrom = resultFrom,
                        story = query,
                    ),
                )
            } else {
                parseShorts(document)
            }
        } catch (_: Exception) {
            emptyList()
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
        val titleRaw = document.selectFirst("h1#news-title, h1.title, h1")?.text()?.trim().orEmpty()
        val title = cleanTitle(titleRaw)
        val year = extractYear(titleRaw)
        val tmdbMovie = TmdbUtils.getMovie(title, year = year, language = language)

        val posterPath = document.selectFirst(".content_text img, .full-text img, img[itemprop=image]")
            ?.attr("src").orEmpty()
        val overview = document.selectFirst(".full-text, .content_text")?.text()?.trim()

        return Movie(
            id = id,
            title = title,
            poster = tmdbMovie?.poster ?: absoluteUrl(posterPath),
            banner = tmdbMovie?.banner,
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
        val titleRaw = document.selectFirst("h1#news-title, h1.title, h1")?.text()?.trim().orEmpty()
        val seasonNumber = Regex("""Staffel\s+(\d+)""", RegexOption.IGNORE_CASE)
            .find(titleRaw)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val titleForTmdb = cleanTitle(titleRaw)
            .replace(Regex("""\s*-\s*Staffel\s+\d+\s*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        val year = extractYear(titleRaw)
        val tmdbTvShow = TmdbUtils.getTvShow(titleForTmdb, year = year, language = language)

        val posterPath = document.selectFirst(".content_text img, .full-text img, img[itemprop=image]")
            ?.attr("src").orEmpty()
        val overview = document.selectFirst(".full-text, .content_text")?.text()?.trim()

        val episodes = parseEpisodesFromDocument(absoluteUrl(id), seasonNumber, document, emptyList())
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
            poster = tmdbTvShow?.poster ?: absoluteUrl(posterPath),
            banner = tmdbTvShow?.banner,
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
    ): List<Episode> {
        return document.select("ul.ep-menu li[id^=serie-]").mapNotNull { li ->
            val idAttr = li.id().removePrefix("serie-")
            val parts = idAttr.split("_")
            val s = parts.getOrNull(0)?.toIntOrNull() ?: seasonNumber
            val e = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            if (s != seasonNumber) return@mapNotNull null
            val tmdbEp = tmdbEpisodes.find { it.number == e }
            Episode(
                id = "$showUrl#s${s}e$e",
                number = e,
                title = tmdbEp?.title ?: "Episode $e",
                poster = tmdbEp?.poster,
                overview = tmdbEp?.overview
            )
        }.distinctBy { it.number }.sortedBy { it.number }
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val showUrl = seasonId.substringBefore("#")
        val seasonNumber = seasonId.substringAfter("#season-").toIntOrNull() ?: 1
        val document = getDocument(absoluteUrl(showUrl))

        val titleRaw = document.selectFirst("h1#news-title, h1.title, h1")?.text()?.trim().orEmpty()
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
            val name = document.selectFirst("h1, title")?.text()?.trim().orEmpty()
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
                val raw = document.select(".player-mirrors span[data-link], .player-mirrors a[data-link], [data-link]")
                    .mapNotNull { el ->
                        val link = normalizeStreamUrl(el.attr("data-link")) ?: return@mapNotNull null
                        if (link.contains("youtube", ignoreCase = true)) return@mapNotNull null
                        val label = el.ownText().ifBlank { el.text() }.trim()
                        link to label
                    }
                    .distinctBy { it.first }
                raw.flatMap { (link, label) -> expandWrapperServers(link, label) }
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
                val li = document.selectFirst("ul.ep-menu li#serie-${season}_${episode}")
                    ?: document.selectFirst("ul.ep-menu li[id=serie-${season}_${episode}]")
                val links = li?.select("a[data-link], [data-link]").orEmpty()
                links.mapNotNull { a ->
                    val link = normalizeStreamUrl(a.attr("data-link")) ?: return@mapNotNull null
                    if (link.contains("youtube", ignoreCase = true)) return@mapNotNull null
                    val label = a.ownText().ifBlank { a.text() }.trim()
                    link to label
                }.distinctBy { it.first }
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
