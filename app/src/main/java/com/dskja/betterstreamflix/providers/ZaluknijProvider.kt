package com.dskja.betterstreamflix.providers

import com.dskja.betterstreamflix.utils.UserPreferences

import android.content.Context
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.extractors.Extractor
import com.dskja.betterstreamflix.BetterStreamflixApp
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Genre
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.People
import com.dskja.betterstreamflix.models.Season
import com.dskja.betterstreamflix.models.Show
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.WebViewResolver
import com.dskja.betterstreamflix.utils.ArtworkRequestHeaders
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.http.GET
import retrofit2.http.Url
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object ZaluknijProvider : Provider, ProviderConfigUrl {

    override val name = "Zaluknij"
    // zaluknij.cc is Cloudflare-blocked (error 1005) from many networks; zaluknij.pl is the live dooplay mirror.
    override val defaultBaseUrl = "https://zaluknij.pl"
    override val baseUrl: String
        get() = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL).ifBlank { defaultBaseUrl }
    override val changeUrlMutex = Mutex()

    override suspend fun onChangeUrl(forceRefresh: Boolean): String = changeUrlMutex.withLock {
        baseUrl
    }
    override val logo: String
        get() = artworkUrl("$baseUrl/wp-content/uploads/2022/03/zaluknij.png")
            ?: "$baseUrl/wp-content/uploads/2022/03/zaluknij.png"
    override val language = "pl"

    private const val TAG = "ZaluknijProvider"
    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private var webViewResolver: WebViewResolver? = null
    private val providerMutex = Mutex()

    private interface Service {
        @GET
        suspend fun getDocument(@Url url: String): Document
    }

    private val service = Retrofit.Builder()
        .baseUrl("$defaultBaseUrl/")
        .client(
            NetworkClient.default.newBuilder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(40, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val cookieHeader = clearanceCookieHeader(original.url.toString())
                    val builder = original.newBuilder()
                        .header("User-Agent", BROWSER_UA)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                        .header("Referer", "$baseUrl/")
                        .header("Origin", baseUrl.trimEnd('/'))
                        .header("Sec-Fetch-Dest", "document")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Site", "same-origin")
                    if (!cookieHeader.isNullOrBlank() && original.header("Cookie") == null) {
                        builder.header("Cookie", cookieHeader)
                    }
                    chain.proceed(builder.build())
                }
                .build()
        )
        .addConverterFactory(JsoupConverterFactory.create())
        .build()
        .create(Service::class.java)

    fun init(context: Context) {
        webViewResolver = WebViewResolver(context)
    }

    private fun getResolver(): WebViewResolver {
        return webViewResolver ?: WebViewResolver(BetterStreamflixApp.instance).also {
            webViewResolver = it
        }
    }

    override suspend fun getHome(): List<Category> {
        val document = getDocument(baseUrl)
        val categories = mutableListOf<Category>()

        document.select("div.module").forEach { module ->
            val title = module.selectFirst("header h2, header h1, h2")?.text()?.trim().orEmpty()
            if (title.isBlank()) return@forEach
            val items = parseTiles(module).take(20)
            if (items.isNotEmpty()) {
                categories.add(Category(title, items))
            }
        }

        if (categories.isEmpty()) {
            val movies = parseTiles(document).filterIsInstance<Movie>().take(20)
            if (movies.isNotEmpty()) categories.add(Category("FILMY ONLINE", movies))
        }

        return categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            return listOf(
                Genre(id = "/filmy-online/", name = "Filmy"),
                Genre(id = "/seriale-online/", name = "Seriale"),
            )
        }

        val url = buildString {
            append("$baseUrl/?s=${encodeQuery(query)}")
            if (page > 1) append("&page=$page")
        }
        return parseSearchResults(getDocument(url)).distinctBy(::itemKey)
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val url = if (page <= 1) "$baseUrl/filmy-online/" else "$baseUrl/filmy-online/page/$page/"
        return parseTiles(getDocument(url)).filterIsInstance<Movie>().distinctBy { it.id }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val url = if (page <= 1) "$baseUrl/seriale-online/" else "$baseUrl/seriale-online/page/$page/"
        return parseTiles(getDocument(url)).filterIsInstance<TvShow>().distinctBy { it.id }
    }

    override suspend fun getMovie(id: String): Movie {
        val url = toAbsoluteUrl(id)
        return parseMovie(getDocument(url), url)
    }

    override suspend fun getTvShow(id: String): TvShow {
        val url = toAbsoluteUrl(id)
        return parseTvShow(getDocument(url), url)
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val showUrl = seasonId.substringBefore("|").takeIf { it.isNotBlank() } ?: return emptyList()
        val seasonNumber = seasonId.substringAfter("|").toIntOrNull() ?: return emptyList()
        return parseTvShow(getDocument(toAbsoluteUrl(showUrl)), toAbsoluteUrl(showUrl))
            .seasons
            .firstOrNull { it.number == seasonNumber }
            ?.episodes
            .orEmpty()
            .sortedBy { it.number }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return when {
            id.contains("filmy-online") -> Genre(id = id, name = "Filmy", shows = getMovies(page).map { it as Show })
            id.contains("seriale-online") -> Genre(id = id, name = "Seriale", shows = getTvShows(page).map { it as Show })
            else -> {
                val url = when {
                    id.startsWith("http") -> id
                    id.startsWith("/") -> "$baseUrl$id"
                    else -> "$baseUrl/$id"
                }
                val finalUrl = if (page > 1 && !url.contains("page/")) {
                    url.trimEnd('/') + "/page/$page/"
                } else url
                val document = getDocument(finalUrl)
                Genre(
                    id = id,
                    name = document.selectFirst("h1, .section-header .headline-gradient")?.text()?.trim()
                        ?: document.title().substringBefore(" - ").trim().ifBlank { id },
                    shows = parseTiles(document).filterIsInstance<Show>(),
                )
            }
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        return People(id = id, name = id, filmography = emptyList())
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val document = getDocument(toAbsoluteUrl(id))
        val servers = mutableListOf<Video.Server>()

        // Dooplay player options (skip trailers)
        document.select("li.dooplay_player_option, ul#playeroptionsul li").forEach { option ->
            val nume = option.attr("data-nume")
            if (nume.equals("trailer", ignoreCase = true)) return@forEach
            val post = option.attr("data-post")
            val type = option.attr("data-type").ifBlank { "movie" }
            val title = option.selectFirst(".title")?.text()?.trim().orEmpty().ifBlank { "Server" }
            if (post.isBlank() || nume.isBlank()) return@forEach
            servers += Video.Server(
                id = "dooplay|$post|$type|$nume",
                name = title,
                src = toAbsoluteUrl(id),
            )
        }

        // Direct non-intro video sources
        document.select("video source[src], #player-frame source[src], video#video1 source[src]").forEach { source ->
            val src = source.attr("abs:src").ifBlank { source.attr("src") }
            if (src.isBlank()) return@forEach
            if (src.contains("/intro", ignoreCase = true) || src.contains("intro2.mp4", ignoreCase = true)) {
                return@forEach
            }
            val label = source.attr("label").ifBlank { "Direct" }
            servers += Video.Server(id = src, name = label, src = src)
        }

        // Legacy zaluknij.cc iframe table
        document.select("div#link-list tbody tr").forEach { row ->
            val link = row.selectFirst("a[href]") ?: return@forEach
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            if (href.isBlank()) return@forEach
            val host = link.selectFirst("img")?.attr("alt").blankToNull() ?: link.text().trim()
            val iframe = decodeIframeSrc(link.attr("data-iframe")).ifBlank { href }
            servers += Video.Server(id = href, name = host.ifBlank { "Server" }, src = iframe)
        }

        if (servers.isEmpty() &&
            (document.html().contains("require_login", ignoreCase = true) ||
                document.selectFirst("#lock-info") != null)
        ) {
            throw Exception("Zaluknij wymaga zalogowania, aby odtworzyć ten tytuł")
        }

        return servers.distinctBy { it.id }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        if (server.id.startsWith("dooplay|")) {
            val parts = server.id.split("|")
            val post = parts.getOrNull(1).orEmpty()
            val type = parts.getOrNull(2).orEmpty()
            val nume = parts.getOrNull(3).orEmpty()
            val ajaxUrl = "$baseUrl/wp-admin/admin-ajax.php?action=doo_player_ajax&post=$post&nume=$nume&type=$type"
            val document = getDocument(ajaxUrl)
            val embed = document.selectFirst("iframe[src], iframe[data-src]")
                ?.let { it.attr("abs:src").ifBlank { it.attr("src") }.ifBlank { it.attr("data-src") } }
                ?: Regex("""src=["']([^"']+)["']""").find(document.html())?.groupValues?.getOrNull(1)
                ?: throw Exception("Zaluknij player option returned no embed")
            val absolute = when {
                embed.startsWith("//") -> "https:$embed"
                embed.startsWith("http") -> embed
                else -> toAbsoluteUrl(embed)
            }
            return Extractor.extract(absolute, server)
        }

        val src = server.src.ifBlank { server.id }
        if (src.contains(".mp4", ignoreCase = true) || src.contains(".m3u8", ignoreCase = true)) {
            return Video(
                source = src,
                headers = mapOf(
                    "User-Agent" to BROWSER_UA,
                    "Referer" to "$baseUrl/",
                )
            )
        }
        return Extractor.extract(src, server)
    }

    private fun parseTiles(container: Element): List<AppAdapter.Item> {
        return container.select("article.item, .items article, div.item").mapNotNull { article ->
            val anchor = article.selectFirst("a[href]") ?: return@mapNotNull null
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            if (href.isBlank()) return@mapNotNull null

            val image = article.selectFirst("img")
            val title = article.selectFirst(".data h3 a, .data h3, h3 a, h3, .title")?.text()?.trim()
                .blankToNull()
                ?: image?.attr("alt").blankToNull()
                ?: anchor.attr("title").blankToNull()
                ?: return@mapNotNull null
            val poster = artworkUrl(
                image?.attr("src").blankToNull() ?: image?.attr("data-src").blankToNull(),
                referer = baseUrl
            )
            val year = article.selectFirst(".data span, .year")?.text()?.extractYear()

            when {
                href.contains("/filmy-online/") || article.hasClass("movies") || article.className().contains("movies") ->
                    Movie(id = href, title = title, released = year, poster = poster, banner = poster)
                href.contains("/seriale-online/") || article.hasClass("tvshows") || article.className().contains("tvshows") ->
                    TvShow(id = href, title = title, poster = poster, banner = poster)
                href.contains("/film/") ->
                    Movie(id = href, title = title, released = year, poster = poster, banner = poster)
                href.contains("/serial-online/") ->
                    TvShow(id = href, title = title, poster = poster, banner = poster)
                else -> null
            }
        }.distinctBy(::itemKey)
    }

    private fun parseSearchResults(document: Document): List<AppAdapter.Item> {
        val fromResultItems = document.select(".result-item article, .search-page .result-item").mapNotNull { article ->
            val anchor = article.selectFirst("a[href]") ?: return@mapNotNull null
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val title = article.selectFirst(".title a, .title, h3")?.text()?.trim().orEmpty()
            val poster = artworkUrl(article.selectFirst("img")?.attr("src"), referer = baseUrl)
            when {
                href.contains("/filmy-online/") || href.contains("/film/") ->
                    Movie(id = href, title = title, poster = poster, banner = poster)
                href.contains("/seriale-online/") || href.contains("/serial-online/") ->
                    TvShow(id = href, title = title, poster = poster, banner = poster)
                else -> null
            }
        }
        return (fromResultItems + parseTiles(document)).distinctBy(::itemKey)
    }

    private fun parseMovie(document: Document, id: String): Movie {
        val title = document.selectFirst("h1, .sheader .data h1")?.text()?.trim()
            ?: document.title().substringBefore(" - ").trim()
        val poster = artworkUrl(
            document.selectFirst("meta[property=og:image]")?.attr("content").blankToNull()
                ?: document.selectFirst(".poster img, #single-poster img")?.attr("src").blankToNull(),
            referer = id
        )
        val overview = document.selectFirst(".wp-content p, .description, p.description")?.text()?.trim()
        return Movie(
            id = id,
            title = title,
            overview = overview,
            released = title.extractYear(),
            poster = poster,
            banner = poster,
        )
    }

    private fun parseTvShow(document: Document, id: String): TvShow {
        val title = document.selectFirst("h1, .sheader .data h1")?.text()?.trim()
            ?: document.title().substringBefore(" - ").trim()
        val poster = artworkUrl(
            document.selectFirst("meta[property=og:image]")?.attr("content").blankToNull()
                ?: document.selectFirst(".poster img, #single-poster img")?.attr("src").blankToNull(),
            referer = id
        )
        val overview = document.selectFirst(".wp-content p, .description, p.description")?.text()?.trim()

        val seasons = document.select("#seasons .se-c, .seasons .se-c, ul#episode-list > li").mapNotNull { seasonElement ->
            if (seasonElement.hasClass("se-c") || seasonElement.className().contains("se-c")) {
                val seasonNumber = seasonElement.selectFirst(".se-t")?.text()?.trim()?.toIntOrNull()
                    ?: seasonElement.selectFirst("> span")?.text()?.extractSeasonNumber()
                    ?: return@mapNotNull null
                val episodes = seasonElement.select("ul.episodios li, ul > li").mapNotNull { li ->
                    val anchor = li.selectFirst("a[href]") ?: return@mapNotNull null
                    val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                    if (href.isBlank()) return@mapNotNull null
                    val numerando = li.selectFirst(".numerando")?.text()?.trim().orEmpty()
                    val episodeNumber = numerando.substringAfter("-").trim().toIntOrNull()
                        ?: anchor.text().extractEpisodeNumber()
                        ?: href.extractEpisodeNumberFromUrl()
                        ?: return@mapNotNull null
                    Episode(
                        id = href,
                        number = episodeNumber,
                        title = li.selectFirst(".episodiotitle a, .episodiotitle")?.text()?.trim()
                            ?: anchor.text().trim().ifBlank { "Odcinek $episodeNumber" },
                        poster = poster,
                    )
                }.sortedBy { it.number }
                Season(
                    id = "$id|$seasonNumber",
                    number = seasonNumber,
                    title = "Sezon $seasonNumber",
                    poster = poster,
                    episodes = episodes,
                )
            } else {
                parseLegacySeason(seasonElement, id, poster)
            }
        }.sortedBy { it.number }

        return TvShow(
            id = id,
            title = title,
            overview = overview,
            poster = poster,
            banner = poster,
            seasons = seasons,
        )
    }

    private fun parseLegacySeason(seasonElement: Element, showUrl: String, poster: String?): Season? {
        val seasonLabel = seasonElement.selectFirst("> span")?.text()?.trim().orEmpty()
        val seasonNumber = seasonLabel.extractSeasonNumber() ?: return null
        val episodes = seasonElement.select("ul > li > a[href*=\"/odcinek-\"]").mapNotNull { anchor ->
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            if (href.isBlank()) return@mapNotNull null
            val episodeNumber = anchor.text().extractEpisodeNumber() ?: return@mapNotNull null
            Episode(
                id = href,
                number = episodeNumber,
                title = anchor.text().substringAfter("] ").trim().ifBlank { "Odcinek $episodeNumber" },
                poster = poster,
            )
        }.sortedBy { it.number }
        return Season(
            id = "$showUrl|$seasonNumber",
            number = seasonNumber,
            title = seasonLabel.ifBlank { "Sezon $seasonNumber" },
            poster = poster,
            episodes = episodes,
        )
    }

    private fun decodeIframeSrc(encoded: String): String {
        if (encoded.isBlank()) return ""
        return try {
            val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8).trim()
            JSONObject(decoded).optString("src").ifBlank {
                if (decoded.startsWith("http")) decoded else ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun itemKey(item: AppAdapter.Item): String {
        return when (item) {
            is Movie -> "movie:${item.id}"
            is TvShow -> "tv:${item.id}"
            is Genre -> "genre:${item.id}"
            else -> item.toString()
        }
    }

    private fun String.extractYear(): String? = Regex("""\b(19|20)\d{2}\b""").find(this)?.value

    private fun String.extractSeasonNumber(): Int? =
        Regex("""\b(?:Sezon|S)\s*0*(\d+)\b""", RegexOption.IGNORE_CASE)
            .find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun String.extractEpisodeNumber(): Int? =
        Regex("""\b(?:Odcinek|E)\s*0*(\d+)\b""", RegexOption.IGNORE_CASE)
            .find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""e0*(\d+)\b""", RegexOption.IGNORE_CASE)
                .find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun String.extractEpisodeNumberFromUrl(): Int? =
        Regex("""(?:/odcinek-|s\d+e)(\d+)""", RegexOption.IGNORE_CASE)
            .find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private suspend fun getDocument(url: String): Document {
        return try {
            val document = service.getDocument(url)
            val html = document.outerHtml()
            if (requiresClearance(html)) {
                throw IllegalStateException("Cloudflare clearance required")
            }
            if (html.contains("error code: 1005", ignoreCase = true)) {
                throw Exception("Zaluknij zablokował to połączenie (Cloudflare 1005). Spróbuj zmienić URL dostawcy.")
            }
            document
        } catch (e: HttpException) {
            if (e.code() != 403 && e.code() != 503) throw e
            Log.d(TAG, "Resolving clearance with WebView for $url: HTTP ${e.code()}")
            val html = providerMutex.withLock { getResolver().get(url) }
            promoteClearanceCookies(url)
            org.jsoup.Jsoup.parse(html).apply { setBaseUri(url) }
        } catch (e: Exception) {
            if (!looksLikeClearanceFailure(e)) throw e
            Log.d(TAG, "Resolving clearance with WebView for $url: ${e.message}")
            val html = providerMutex.withLock { getResolver().get(url) }
            promoteClearanceCookies(url)
            org.jsoup.Jsoup.parse(html).apply { setBaseUri(url) }
        }
    }

    private fun looksLikeClearanceFailure(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("Cloudflare clearance required", ignoreCase = true) ||
            message.contains("Just a moment", ignoreCase = true) ||
            message.contains("One moment, please", ignoreCase = true) ||
            message.contains("Proszę czekać", ignoreCase = true) ||
            message.contains("Checking your browser", ignoreCase = true) ||
            message.contains("cf-browser-verification", ignoreCase = true)
    }

    private fun requiresClearance(html: String): Boolean {
        return html.contains("cf-browser-verification", ignoreCase = true) ||
            html.contains("Checking your browser", ignoreCase = true) ||
            html.contains("Just a moment...", ignoreCase = true) ||
            html.contains("One moment, please", ignoreCase = true) ||
            html.contains("Proszę czekać", ignoreCase = true) ||
            // Soft antibot interstitial that auto-reloads every few seconds.
            (html.contains("window.location.reload()", ignoreCase = true) &&
                html.contains("spinner", ignoreCase = true) &&
                !html.contains("dooplay", ignoreCase = true) &&
                !html.contains("article", ignoreCase = true))
    }

    private fun promoteClearanceCookies(sourceUrl: String) {
        val cookieManager = CookieManager.getInstance()
        val cookieHeader = listOf(sourceUrl, baseUrl, "$baseUrl/")
            .firstNotNullOfOrNull { candidate -> cookieManager.getCookie(candidate)?.takeIf { it.isNotBlank() } }
            .orEmpty()
        if (cookieHeader.isBlank()) return
        cookieHeader.split(";").map { it.trim() }.filter { it.isNotBlank() }.forEach { cookie ->
            val rootCookie = if (cookie.contains("Path=", ignoreCase = true)) cookie else "$cookie; Path=/"
            listOf(sourceUrl, baseUrl, "$baseUrl/").distinct().forEach { target ->
                cookieManager.setCookie(target, rootCookie)
            }
        }
        cookieManager.flush()
    }

    private fun clearanceCookieHeader(requestUrl: String): String? {
        val cookieManager = CookieManager.getInstance()
        return listOf(requestUrl, baseUrl, "$baseUrl/")
            .firstNotNullOfOrNull { candidate -> cookieManager.getCookie(candidate)?.takeIf { it.isNotBlank() } }
    }

    private fun artworkUrl(url: String?, referer: String = baseUrl): String? {
        val image = url?.trim().orEmpty()
        if (image.isBlank()) return null
        return ArtworkRequestHeaders.withHeaders(
            url = image,
            referer = referer,
            userAgent = BROWSER_UA,
            cookie = clearanceCookieHeader(referer),
        )
    }

    private fun encodeQuery(query: String): String = URLEncoder.encode(query, Charsets.UTF_8.name())

    private fun toAbsoluteUrl(url: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("/") -> "$baseUrl$url"
            else -> "$baseUrl/$url"
        }
    }
}
