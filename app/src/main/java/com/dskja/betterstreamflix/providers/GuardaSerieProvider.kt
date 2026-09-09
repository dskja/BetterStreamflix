package com.dskja.betterstreamflix.providers

import com.dskja.betterstreamflix.utils.UserPreferences

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.dskja.betterstreamflix.BetterStreamflixApp
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Genre
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.models.People
import com.dskja.betterstreamflix.models.Season
import com.dskja.betterstreamflix.models.Show
import com.dskja.betterstreamflix.extractors.Extractor
import com.dskja.betterstreamflix.utils.DnsResolver
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.TmdbUtils
import com.dskja.betterstreamflix.utils.WebViewResolver
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import retrofit2.http.Headers
import java.util.concurrent.TimeUnit
import java.net.URLEncoder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object GuardaSerieProvider : Provider, ProviderConfigUrl {

    override val name = "GuardaSerie"
    // Official WP mirrors sit behind Cloudflare; guarda-serie.ovh is the live Sept 2026 catalog (VixSrc embeds).
    override val defaultBaseUrl = "https://guarda-serie.ovh/"
    override val baseUrl: String
        get() = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL).ifBlank { defaultBaseUrl }
    override val changeUrlMutex = Mutex()

    override suspend fun onChangeUrl(forceRefresh: Boolean): String = changeUrlMutex.withLock {
        service = GuardaSerieService.build(baseUrl.let { if (it.endsWith("/")) it else "$it/" })
        baseUrl
    }
    override val logo: String = "$baseUrl/static/logo.png"
    override val language = "it"

    private const val USER_AGENT = "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val TAG = "GuardaSerieBypass"
    private const val VIXSRC = "https://vixsrc.to"

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

    private interface GuardaSerieService {
        companion object {
            fun build(baseUrl: String): GuardaSerieService {
                val clientBuilder = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .callTimeout(45, TimeUnit.SECONDS)
                    .cookieJar(NetworkClient.cookieJar)
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                            )
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                            .header("Referer", baseUrl)
                            .header("Origin", baseUrl.trimEnd('/'))
                            .build()
                        chain.proceed(request)
                    }

                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(clientBuilder.dns(DnsResolver.doh).build())
                    .build()
                    .create(GuardaSerieService::class.java)
            }
        }

        @Headers(USER_AGENT)
        @GET(".")
        suspend fun getHome(): Document

        @Headers(USER_AGENT)
        @GET
        suspend fun getPage(@Url url: String): Document
    }

    private var service = GuardaSerieService.build(defaultBaseUrl)

    private class CloudflareChallengeException(val url: String) : Exception("Cloudflare challenge detected for $url")

    private fun requiresClearance(html: String): Boolean {
        return html.contains("Just a moment...", ignoreCase = true) ||
               html.contains("cf-browser-verification", ignoreCase = true) ||
               html.contains("Checking your browser", ignoreCase = true)
    }

    private fun isAnnouncementPage(doc: Document): Boolean {
        val html = doc.outerHtml()
        return html.contains("announcement-box", ignoreCase = true) ||
            (doc.select("a[href]").size <= 5 && html.contains("Nuovo Indirizzo", ignoreCase = true))
    }

    private fun extractMirrorFromAnnouncement(doc: Document): String? {
        return doc.select("a[href]")
            .map { it.attr("href").trim() }
            .firstOrNull { href ->
                href.startsWith("http") &&
                    (href.contains("guardo", ignoreCase = true) || href.contains("guarda-serie", ignoreCase = true) ||
                        href.contains("guardaserie", ignoreCase = true)) &&
                    !href.contains("guardaserie.click", ignoreCase = true) &&
                    !href.contains("guardaserie.foo", ignoreCase = true) &&
                    !href.contains("style/", ignoreCase = true)
            }?.let { if (it.endsWith("/")) it else "$it/" }
    }

    private suspend fun getDocument(url: String): Document {
        try {
            val doc = service.getPage(url)
            val html = doc.outerHtml()
            if (requiresClearance(html)) {
                throw CloudflareChallengeException(url)
            }
            if (isAnnouncementPage(doc)) {
                extractMirrorFromAnnouncement(doc)?.let { mirror ->
                    Log.d(TAG, "Announcement page detected, following mirror $mirror")
                    UserPreferences.setProviderCache(this, UserPreferences.PROVIDER_URL, mirror)
                    onChangeUrl(forceRefresh = true)
                    return getDocument(url.replace(Regex("^https?://[^/]+"), mirror.trimEnd('/')))
                }
            }
            return doc
        } catch (e: Exception) {
            val httpException = e as? retrofit2.HttpException
            val isChallengeException = e is CloudflareChallengeException

            val isCloudflareChallengeOn403 = if (httpException?.code() == 403) {
                val errorBody = httpException.response()?.errorBody()?.string().orEmpty()
                requiresClearance(errorBody)
            } else false

            if (!isChallengeException && !isCloudflareChallengeOn403) {
                throw e
            }

            Log.d(TAG, "Using WebView bypass for $url (Cloudflare challenge detected)")
            val result = providerMutex.withLock {
                getResolver().getResult(
                    url = url,
                    headers = mapOf("User-Agent" to NetworkClient.USER_AGENT),
                    completion = { _, htmlText, _ ->
                        val isChallenge = requiresClearance(htmlText)
                        val hasContent = htmlText.contains("movies-list") ||
                            htmlText.contains("mvic-desc") ||
                            htmlText.contains("player2") ||
                            htmlText.contains("playerBaseURL") ||
                            htmlText.contains("detail/tv-") ||
                            htmlText.contains("front-title")
                        !isChallenge && hasContent
                    }
                )
            }
            CookieManager.getInstance().flush()

            val parsedDoc = Jsoup.parse(result.html, url).apply { setBaseUri(baseUrl) }
            if (requiresClearance(parsedDoc.outerHtml())) {
                throw CloudflareChallengeException(url)
            }
            return parsedDoc
        }
    }

    private fun absUrl(path: String): String {
        return when {
            path.startsWith("http") -> path
            path.startsWith("//") -> "https:$path"
            else -> baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        }
    }

    private fun parseDetailItem(href: String, title: String, poster: String = "", rating: Double? = null): Show? {
        val absolute = absUrl(href)
        if (!absolute.contains("/detail/")) return null
        return if (absolute.contains("/detail/movie-")) {
            Movie(id = absolute, title = title, poster = poster, rating = rating)
        } else {
            TvShow(id = absolute, title = title, poster = poster, rating = rating)
        }
    }

    private fun parseSliderItems(doc: Document): List<Show> {
        return doc.select(".slider-item a[href*=/detail/]").mapNotNull { a ->
            val href = a.attr("href")
            val img = a.selectFirst("img")
            val title = img?.attr("alt")?.ifBlank { null }
                ?: a.attr("title").ifBlank { a.text() }
            if (title.isBlank()) return@mapNotNull null
            val poster = img?.attr("src")?.let { absUrl(it) }.orEmpty()
            parseDetailItem(href, title.trim(), poster)
        }.distinctBy { it.id }
    }

    private fun parseListItems(doc: Document): List<Show> {
        val fromMlnew = doc.select("div.mlnew").mapNotNull { row ->
            val a = row.selectFirst("a[href*=/detail/]") ?: return@mapNotNull null
            val href = a.attr("href")
            val title = row.selectFirst("h2 a, a[title]")?.attr("title")?.ifBlank { null }
                ?: row.selectFirst("h2 a")?.text()
                ?: a.attr("title")
                ?: a.text()
            if (title.isBlank()) return@mapNotNull null
            val poster = row.selectFirst("img")?.attr("src")?.let { absUrl(it) }.orEmpty()
            val rating = row.selectFirst(".mlnh-imdb")?.text()
                ?.replace("★", "")?.trim()?.toDoubleOrNull()
            parseDetailItem(href, title.trim(), poster, rating)
        }
        if (fromMlnew.isNotEmpty()) return fromMlnew.distinctBy { it.id }

        return doc.select("a[href*=/detail/]").mapNotNull { a ->
            val href = a.attr("href")
            val title = a.attr("title").ifBlank { a.selectFirst("img")?.attr("alt") }.orEmpty()
                .ifBlank { a.text() }
            if (title.isBlank() || title.equals("Guarda ora", ignoreCase = true)) return@mapNotNull null
            val poster = a.selectFirst("img")?.attr("src")?.let { absUrl(it) }.orEmpty()
            parseDetailItem(href, title.trim(), poster)
        }.distinctBy { it.id }
    }

    // Legacy WP grid (guardoserie.yachts after CF clearance)
    private fun parseGridItem(el: Element): AppAdapter.Item? {
        val link = el.selectFirst("a.ml-mask[href], a[href*=/serie/], a[href*=/detail/]") ?: return null
        val href = absUrl(link.attr("href").trim())
        if (href.isBlank()) return null
        val title = el.selectFirst("span.mli-info h2, h2, .entry-title")?.text()?.trim()
            ?: link.attr("title").trim()
        if (title.isBlank()) return null
        val img = el.selectFirst("img")
        val poster = img?.attr("data-original")?.takeIf { it.isNotBlank() }
            ?: img?.attr("src")?.takeIf { it.isNotBlank() }
            ?: ""
        return if (href.contains("/serie/") || href.contains("/detail/tv-")) {
            TvShow(id = href, title = title, poster = absUrl(poster))
        } else {
            Movie(id = href, title = title, poster = absUrl(poster))
        }
    }

    override suspend fun getHome(): List<Category> = coroutineScope {
        val doc = getDocument(baseUrl)
        val categories = mutableListOf<Category>()

        doc.select("h2.front-title").forEach { heading ->
            val title = heading.text().trim().ifBlank { return@forEach }
            val section = heading.parent() ?: return@forEach
            val items = parseSliderItems(section).ifEmpty { parseListItems(section) }
            if (items.isNotEmpty()) {
                categories.add(Category(name = title, list = items))
            }
        }

        if (categories.isEmpty()) {
            val items = parseSliderItems(doc).ifEmpty { parseListItems(doc) }
            if (items.isNotEmpty()) {
                categories.add(Category(name = "Serie", list = items))
            }
        }

        // Legacy WP home fallback
        if (categories.isEmpty()) {
            val legacy = doc.select("div.movies-list.movies-list-full div.ml-item").mapNotNull { parseGridItem(it) }
            if (legacy.isNotEmpty()) {
                categories.add(Category(name = "Serie", list = legacy))
            }
        }

        categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            if (page > 1) return emptyList()
            val doc = getDocument(baseUrl)
            val genres = doc.select("a[href*=genre_id=]").mapNotNull { a ->
                val href = absUrl(a.attr("href"))
                val text = a.text().trim()
                if (href.isBlank() || text.isBlank()) return@mapNotNull null
                Genre(id = href, name = text)
            }.distinctBy { it.id }
            if (genres.isNotEmpty()) return genres

            return doc.select("li.menu-item:has(> a:matchesOwn(^Genere$)) ul.sub-menu li a[href]")
                .mapNotNull { a ->
                    val href = a.attr("href").trim()
                    val text = a.text().trim()
                    if (href.isBlank() || text.isBlank()) null else Genre(id = href, name = text)
                }
        }

        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = when {
            page > 1 && baseUrl.contains("guarda-serie.ovh") -> "$baseUrl/search?q=$encoded&page=$page"
            baseUrl.contains("guarda-serie.ovh") -> "$baseUrl/search?q=$encoded"
            page > 1 -> "$baseUrl/page/$page/?s=$encoded"
            else -> "$baseUrl/?s=$encoded"
        }
        val doc = getDocument(searchUrl)
        val modern = parseListItems(doc)
        if (modern.isNotEmpty()) return modern
        return doc.select("div.movies-list.movies-list-full div.ml-item").mapNotNull { parseGridItem(it) }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        // Current mirror is TV-focused; keep empty rather than scrape an unrelated film site.
        return emptyList()
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val url = when {
            page > 1 && baseUrl.contains("guarda-serie.ovh") -> "$baseUrl/archive?type=tv&page=$page"
            baseUrl.contains("guarda-serie.ovh") -> "$baseUrl/archive?type=tv"
            page > 1 -> "$baseUrl/serie/page/$page/"
            else -> "$baseUrl/serie/"
        }
        val doc = getDocument(url)
        val modern = parseListItems(doc).filterIsInstance<TvShow>()
        if (modern.isNotEmpty()) return modern
        return doc.select("div.movies-list.movies-list-full div.ml-item")
            .mapNotNull { parseGridItem(it) as? TvShow }
    }

    override suspend fun getMovie(id: String): Movie {
        val doc = getDocument(id)
        val title = doc.selectFirst("h1, div.mvic-desc h3")?.text()?.trim().orEmpty()
        val tmdbMovie = TmdbUtils.getMovie(title, language = language)
        val poster = tmdbMovie?.poster
            ?: doc.selectFirst(".post-thumbnail img, .thumb img, img")?.attr("src")?.let { absUrl(it) }
            ?: ""
        return Movie(
            id = id,
            title = title,
            poster = poster,
            overview = tmdbMovie?.overview ?: doc.selectFirst("p.f-desc, .description")?.text()?.trim(),
            rating = tmdbMovie?.rating,
            banner = tmdbMovie?.banner,
            imdbId = tmdbMovie?.imdbId,
            runtime = tmdbMovie?.runtime,
            genres = tmdbMovie?.genres ?: emptyList(),
            cast = emptyList(),
            trailer = tmdbMovie?.trailer,
        )
    }

    private fun extractTmdbId(doc: Document, id: String): Int? {
        Regex("""tmdbID\s*=\s*(\d+)""").find(doc.html())?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        Regex("""/detail/tv-(\d+)""").find(id)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        Regex("""/detail/movie-(\d+)""").find(id)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        return null
    }

    override suspend fun getTvShow(id: String): TvShow {
        val doc = getDocument(id)
        val title = doc.selectFirst("h1, div.mvic-desc h3")?.text()?.trim().orEmpty()
        val tmdbTvShow = TmdbUtils.getTvShow(title, language = language)
        val poster = tmdbTvShow?.poster
            ?: doc.selectFirst("img[src*=/img/], .thumb img, div.thumb.mvic-thumb img")?.attr("src")?.let { absUrl(it) }
            ?: ""

        val overview = tmdbTvShow?.overview ?: run {
            val raw = doc.selectFirst("p.f-desc")?.text()?.trim()
                ?: Regex("""Trama[^:]*:(?:</b>)?<br>\s*([^<]+)""", RegexOption.IGNORE_CASE)
                    .find(doc.html())?.groupValues?.getOrNull(1)?.trim()
            raw?.takeUnless { text ->
                val l = text.lowercase()
                l.contains("streaming community ita su guardaserie") && l.contains("guardare serie")
            }
        }

        val seasonTabs = doc.select("a[href^=#season-]").mapNotNull { a ->
            Regex("""season-(\d+)""").find(a.attr("href"))?.groupValues?.getOrNull(1)?.toIntOrNull()
        }.distinct().sorted()

        val seasons = when {
            seasonTabs.isNotEmpty() -> seasonTabs.map { num ->
                Season(
                    id = "$id|$num",
                    number = num,
                    poster = tmdbTvShow?.seasons?.find { it.number == num }?.poster
                )
            }
            else -> doc.select("div#seasons div.tvseason").mapNotNull { seasonEl ->
                if (seasonEl.selectFirst("div.les-content a.ep-404") != null) return@mapNotNull null
                val titleText = seasonEl.selectFirst("div.les-title strong")?.text()?.trim() ?: return@mapNotNull null
                val seasonNumber = Regex("Stagione\\s+(\\d+)", RegexOption.IGNORE_CASE)
                    .find(titleText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                Season(
                    id = "$id#s$seasonNumber",
                    number = seasonNumber,
                    poster = tmdbTvShow?.seasons?.find { it.number == seasonNumber }?.poster
                )
            }
        }

        return TvShow(
            id = id,
            title = title,
            poster = poster,
            overview = overview,
            rating = tmdbTvShow?.rating,
            genres = tmdbTvShow?.genres ?: emptyList(),
            cast = emptyList(),
            seasons = seasons,
            banner = tmdbTvShow?.banner,
            imdbId = tmdbTvShow?.imdbId,
            trailer = tmdbTvShow?.trailer,
            runtime = tmdbTvShow?.runtime,
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        if ("|" in seasonId) {
            val showId = seasonId.substringBefore("|")
            val seasonNum = seasonId.substringAfter("|").toIntOrNull() ?: return emptyList()
            val doc = getDocument(showId)
            val tmdbTvShow = TmdbUtils.getTvShow(
                cleanTitle(doc.selectFirst("h1, div.mvic-desc h3")?.text().orEmpty()),
                language = language
            )
            val tmdbEpisodes = if (tmdbTvShow != null) {
                TmdbUtils.getEpisodesBySeason(tmdbTvShow.id, seasonNum, language = language)
            } else emptyList()

            val pane = doc.selectFirst("#season-$seasonNum") ?: doc
            return pane.select("a[data-season=$seasonNum][data-episode], a[data-episode]").mapNotNull { a ->
                val epNumber = a.attr("data-episode").toIntOrNull()
                    ?: Regex("""e(?:p(?:isode)?)?\s*(\d+)""", RegexOption.IGNORE_CASE).find(a.text())
                        ?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: return@mapNotNull null
                val tmdbEp = tmdbEpisodes.find { it.number == epNumber }
                Episode(
                    id = "$showId|$seasonNum|$epNumber",
                    number = epNumber,
                    title = tmdbEp?.title ?: a.text().ifBlank { "Episodio $epNumber" },
                    poster = tmdbEp?.poster,
                    overview = tmdbEp?.overview,
                )
            }.distinctBy { it.number }.sortedBy { it.number }
        }

        // Legacy WP seasons (#sN)
        val showId = seasonId.substringBefore("#s")
        val seasonNum = seasonId.substringAfter("#s").toIntOrNull() ?: return emptyList()
        val doc = getDocument(showId)
        val tmdbTvShow = TmdbUtils.getTvShow(cleanTitle(doc.selectFirst("div.mvic-desc h3")?.text() ?: ""), language = language)
        val tmdbEpisodes = if (tmdbTvShow != null) TmdbUtils.getEpisodesBySeason(tmdbTvShow.id, seasonNum, language = language) else emptyList()

        val seasonEl = doc.select("div#seasons div.tvseason").firstOrNull { el ->
            val titleText = el.selectFirst("div.les-title strong")?.text()?.trim() ?: ""
            Regex("Stagione\\s+$seasonNum", RegexOption.IGNORE_CASE).containsMatchIn(titleText)
        } ?: return emptyList()

        val contentEl = seasonEl.selectFirst("div.les-content") ?: return emptyList()
        if (contentEl.selectFirst("a.ep-404") != null) return emptyList()

        return contentEl.select("a[href]").mapNotNull { a ->
            val href = a.attr("href").trim()
            val text = a.text().trim()
            if (href.isBlank() || text.isBlank()) return@mapNotNull null
            val epNumber = Regex("Episodio\\s+(\\d+)", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val tmdbEp = tmdbEpisodes.find { it.number == epNumber }
            Episode(
                id = href,
                number = epNumber,
                title = tmdbEp?.title ?: text,
                poster = tmdbEp?.poster,
                overview = tmdbEp?.overview
            )
        }
    }

    private fun cleanTitle(title: String): String {
        return title.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "").trim()
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            val url = when {
                id.contains("genre_id=") && page > 1 -> {
                    if (id.contains("page=")) id.replace(Regex("page=\\d+"), "page=$page")
                    else id + (if ("?" in id) "&" else "?") + "page=$page"
                }
                page > 1 -> "${id.trimEnd('/')}/page/$page/"
                else -> id
            }
            val doc = getDocument(url)
            val shows = parseListItems(doc).ifEmpty {
                doc.select("div.movies-list.movies-list-full div.ml-item").mapNotNull { parseGridItem(it) as? Show }
            }
            Genre(id = id, name = "", shows = shows)
        } catch (_: Exception) {
            Genre(id = id, name = "", shows = emptyList())
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        if (page > 1) return People(id = id, name = "", filmography = emptyList())
        return try {
            val doc = getDocument(id)
            val filmography = parseListItems(doc).ifEmpty {
                doc.select("div.movies-list.movies-list-full div.ml-item").mapNotNull { parseGridItem(it) as? Show }
            }
            People(id = id, name = "", filmography = filmography)
        } catch (_: Exception) {
            People(id = id, name = "", filmography = emptyList())
        }
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        // Modern ovh episode ids: showUrl|season|episode
        if (id.count { it == '|' } >= 2) {
            val parts = id.split("|")
            val showUrl = parts[0]
            val season = parts.getOrNull(1)?.toIntOrNull()
            val episode = parts.getOrNull(2)?.toIntOrNull()
            if (season != null && episode != null) {
                val doc = getDocument(showUrl)
                val tmdbId = extractTmdbId(doc, showUrl)
                val playerBase = Regex("""playerBaseURL\s*=\s*"([^"]+)"""")
                    .find(doc.html())?.groupValues?.getOrNull(1)
                    ?.replace("\\/", "/")
                    ?: VIXSRC
                if (tmdbId != null) {
                    val src = "$playerBase/tv/$tmdbId/$season/$episode?lang=it"
                    return listOf(Video.Server(id = src, name = "VixSrc", src = src))
                }
            }
        }

        val doc = getDocument(id)
        val html = doc.html()

        // Modern detail page with VixSrc player
        val playerBase = Regex("""playerBaseURL\s*=\s*"([^"]+)"""")
            .find(html)?.groupValues?.getOrNull(1)?.replace("\\/", "/")
        val tmdbId = extractTmdbId(doc, id)
        val mediaType = Regex("""mediaType\s*=\s*"([^"]+)"""")
            .find(html)?.groupValues?.getOrNull(1) ?: "tv"
        if (playerBase != null && tmdbId != null) {
            val src = when (mediaType) {
                "movie" -> "$playerBase/movie/$tmdbId?lang=it"
                else -> {
                    val (season, episode) = when (videoType) {
                        is Video.Type.Episode -> videoType.season.number to videoType.number
                        else -> 1 to 1
                    }
                    "$playerBase/tv/$tmdbId/$season/$episode?lang=it"
                }
            }
            return listOf(Video.Server(id = src, name = "VixSrc", src = src))
        }

        // Legacy WP embeds
        val legacy = doc.select("div#player2 div[id^=tab], div.movieplay iframe, iframe[src], iframe[data-src]")
            .mapIndexedNotNull { index, el ->
                val iframe = if (el.tagName() == "iframe") el else el.selectFirst("iframe")
                val iframeSrc = iframe?.attr("data-src")?.takeIf { it.isNotBlank() }
                    ?: iframe?.attr("src")?.takeIf { it.isNotBlank() }
                    ?: return@mapIndexedNotNull null
                val finalUrl = absUrl(iframeSrc.trim())
                try {
                    val hostName = finalUrl.toHttpUrl().host
                        .replaceFirst("www.", "")
                        .substringBefore(".")
                        .replaceFirstChar { char ->
                            if (char.isLowerCase()) char.titlecase() else char.toString()
                        }
                    Video.Server(
                        id = finalUrl,
                        name = "Server ${index + 1} - $hostName",
                        src = finalUrl
                    )
                } catch (_: Exception) {
                    null
                }
            }

        return legacy.distinctBy { it.src.ifBlank { it.id } }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.src.ifBlank { server.id }, server)
    }
}
