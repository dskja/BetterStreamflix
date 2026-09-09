package com.dskja.betterstreamflix.providers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import com.dskja.betterstreamflix.utils.UserPreferences

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Genre
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.models.People
import com.dskja.betterstreamflix.models.Show
import com.dskja.betterstreamflix.utils.TmdbUtils
import com.dskja.betterstreamflix.extractors.Extractor
import com.dskja.betterstreamflix.utils.DnsResolver
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Path
import java.util.concurrent.TimeUnit
import java.net.URLEncoder
import java.util.Base64

object GuardaFlixProvider : Provider, ProviderConfigUrl {

    override val name: String = "GuardaFlix"
    override val defaultBaseUrl = "https://www.guardaflix.org"
    override val baseUrl: String
        get() = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL).ifBlank { defaultBaseUrl }
    override val changeUrlMutex = Mutex()

    override suspend fun onChangeUrl(forceRefresh: Boolean): String = changeUrlMutex.withLock {
        service = GuardaFlixService.build(baseUrl.let { if (it.endsWith("/")) it else "$it/" })
        baseUrl
    }
    override val logo: String = "$baseUrl/favicon.ico"
    override val language: String = "it"

    private const val USER_AGENT = "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private interface GuardaFlixService {
        companion object {
            fun build(baseUrl: String): GuardaFlixService {
                val clientBuilder = OkHttpClient.Builder()
                    .readTimeout(35, TimeUnit.SECONDS)
                    .connectTimeout(25, TimeUnit.SECONDS)
                    .callTimeout(50, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .addInterceptor { chain ->
                        val origin = baseUrl.trimEnd('/')
                        val request = chain.request().newBuilder()
                            .header("User-Agent", BROWSER_UA)
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                            .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                            .header("Referer", "$origin/")
                            .header("Origin", origin)
                            .header("Sec-Fetch-Dest", "document")
                            .header("Sec-Fetch-Mode", "navigate")
                            .header("Sec-Fetch-Site", "same-origin")
                            .header("Upgrade-Insecure-Requests", "1")
                            .build()
                        chain.proceed(request)
                    }

                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(clientBuilder.dns(DnsResolver.doh).build())
                    .build()
                    .create(GuardaFlixService::class.java)
            }
        }

        @Headers(USER_AGENT)
        @GET(".")
        suspend fun getHome(): Document

        @Headers(USER_AGENT)
        @GET
        suspend fun getPage(@Url url: String): Document

        @Headers(USER_AGENT)
        @GET(".")
        suspend fun search(@Query(value = "s", encoded = true) query: String): Document

        @Headers(USER_AGENT)
        @GET("page/{page}/")
        suspend fun search(@Path("page") page: Int, @Query(value = "s", encoded = true) query: String): Document

        @Headers(USER_AGENT)
        @GET("page/{page}/")
        suspend fun movies(@Path("page") page: Int): Document
    }

    private var service = GuardaFlixService.build(defaultBaseUrl)

    private fun normalizeUrl(url: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> baseUrl.trimEnd('/') + url
            url.isBlank() -> ""
            else -> baseUrl.trimEnd('/') + "/" + url.trimStart('/')
        }
    }

    override suspend fun getHome(): List<Category> {
        val doc = service.getHome()
        val categories = mutableListOf<Category>()

        doc.select("section.section").forEach { section: Element ->
            val title = section.selectFirst(".section-title, header .section-title")?.text()?.trim()
                ?: return@forEach
            val items = section.select("a.card[href*=/film-streaming/], .post-lst li, a.lnk-blk")
                .mapNotNull { el: Element -> parseGridItem(el) }
            if (items.isNotEmpty()) {
                categories.add(Category(name = title, list = items.distinctBy { (it as? Movie)?.id ?: it.hashCode() }))
            }
        }

        if (categories.isEmpty()) {
            val items = doc.select("a.card[href*=/film-streaming/]").mapNotNull { parseGridItem(it) }
            if (items.isNotEmpty()) categories.add(Category(name = "Film", list = items))
        }

        return categories
    }

    private fun parseGridItem(el: Element): AppAdapter.Item? {
        // New GuardaFlix cards: <a class="card" href="/film-streaming/tt...">
        if (el.tagName() == "a" && el.hasClass("card")) {
            val href = normalizeUrl(el.attr("href"))
            val title = el.selectFirst(".card-title")?.text()?.trim()
                ?: el.selectFirst("img")?.attr("alt")?.trim()
                ?: return null
            val poster = el.selectFirst("img")?.attr("src")?.let { normalizeUrl(it) } ?: ""
            val rating = el.selectFirst(".card-rating")?.ownText()?.trim()?.toDoubleOrNull()
                ?: el.selectFirst(".card-rating")?.text()?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()
            return Movie(id = href, title = title, poster = poster, rating = rating)
        }

        val title = el.selectFirst(".entry-title, .card-title")?.text()?.trim() ?: return null
        val href = el.selectFirst("a.lnk-blk, a.card, a[href*=/film-streaming/], a[href*=/movies/]")
            ?.attr("href")
            ?.let { normalizeUrl(it) }
            ?: return null
        val poster = el.selectFirst("img")?.attr("src")?.let { normalizeUrl(it) } ?: ""
        val rating = el.selectFirst(".vote, .card-rating")?.ownText()?.trim()?.toDoubleOrNull()
            ?: el.selectFirst(".vote, .card-rating")?.text()?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()

        return Movie(
            id = href,
            title = title,
            poster = poster,
            rating = rating
        )
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            if (page > 1) return emptyList()
            val doc = service.getHome()
            val links = doc.select("a[href*=/film-per-genere/], li.menu-item:has(> a[href*='/movies']) ul.sub-menu li a[href]")
            return links.mapNotNull { a: Element ->
                val href = a.attr("href").trim()
                val text = a.text().trim()
                if (href.isBlank() || text.isBlank()) return@mapNotNull null
                Genre(id = href, name = text)
            }.distinctBy { it.id }
        }

        val encoded = URLEncoder.encode(query, "UTF-8")
        if (page > 1) {
            val firstDoc = service.search(encoded)
            val hasPager = firstDoc.selectFirst(".navigation.pagination .nav-links a.page-link, a[href*=page/]") != null
            if (!hasPager) return emptyList()
        }

        val doc = if (page > 1) service.search(page, encoded) else service.search(encoded)

        return doc.select("a.card[href*=/film-streaming/], .post-lst li")
            .mapNotNull { el: Element -> parseGridItem(el) }
            .distinctBy { (it as? Movie)?.id ?: it.hashCode() }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val doc = if (page > 1) service.movies(page) else service.getHome()

        return doc.select("a.card[href*=/film-streaming/], section.section.movies .post-lst li")
            .mapNotNull { el: Element -> parseGridItem(el) as? Movie }
            .distinctBy { it.id }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return emptyList() // GuardaFlix is movies only
    }

    override suspend fun getMovie(id: String): Movie {
        val doc = service.getPage(id)

        val title = doc.selectFirst("h1.entry-title, h1")?.text()?.trim() ?: ""

        val tmdbMovie = TmdbUtils.getMovie(title, language = language)

        val poster = tmdbMovie?.poster
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")?.let { normalizeUrl(it) }
            ?: doc.selectFirst(".post-thumbnail img, img.movie-poster, .hero img")?.attr("src")?.let { normalizeUrl(it) }
            ?: ""
        val description = tmdbMovie?.overview
            ?: doc.selectFirst(".description p, .movie-overview, .overview, meta[name=description]")?.let {
                it.attr("content").ifBlank { it.text() }
            }?.trim()
            ?: ""
        val rating = tmdbMovie?.rating ?: doc.selectFirst("span.vote.fa-star .num, .card-rating, .rating")?.text()?.trim()
            ?.replace(',', '.')
            ?.replace(Regex("[^0-9.]"), "")
            ?.toDoubleOrNull()

        val runtime = doc.selectFirst("span.duration.fa-clock.far, .duration")?.text()?.trim()?.let { text ->
            val hours = Regex("(\\d+)h").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val minutes = Regex("(\\d+)m").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            if (hours > 0 || minutes > 0) hours * 60 + minutes else null
        }

        val genres = tmdbMovie?.genres ?: doc.select("span.genres a[href], a[href*=/film-per-genere/]").map { a: Element ->
            Genre(
                id = a.attr("href"),
                name = a.text().trim()
            )
        }

        val cast = doc.select("ul.cast-lst p a[href], .cast a[href]").map { a: Element ->
            val name = a.text().trim()
            val tmdbPerson = tmdbMovie?.cast?.find { it.name.equals(name, ignoreCase = true) }
            People(
                id = a.attr("href").trim(),
                name = name,
                image = tmdbPerson?.image
            )
        }

        // Trailer: extract from inlined base64 script (funciones_public_js-js-extra) when present
        val trailer: String? = tmdbMovie?.trailer ?: runCatching {
            val b64Src = doc.selectFirst("script#funciones_public_js-js-extra[src^=data:text/javascript;base64,]")
                ?.attr("src")
                ?.substringAfter("base64,")
                ?: ""
            if (b64Src.isBlank()) null else {
                val decoded = String(Base64.getDecoder().decode(b64Src))
                val fromTrailer = Regex("""\"trailer\"\s*:\s*\".*?src=\\\"(https?:\\/\\/www\.youtube\.com\\/embed\\/[^\\\"]+)\\\"""")
                    .find(decoded)
                    ?.groupValues?.getOrNull(1)
                fromTrailer
                    ?.replace("\\/", "/")
                    ?.let { mapTrailerToWatchUrl(it) }
            }
        }.getOrNull()

        return Movie(
            id = id,
            title = title,
            poster = poster,
            overview = description,
            rating = rating,
            released = tmdbMovie?.released?.let { "${it.get(java.util.Calendar.YEAR)}" },
            genres = genres,
            cast = cast,
            trailer = trailer,
            banner = tmdbMovie?.banner,
            runtime = tmdbMovie?.runtime ?: runtime,
            imdbId = tmdbMovie?.imdbId
        )
    }

    private fun mapTrailerToWatchUrl(url: String): String {
        return when {
            url.contains("youtube.com/embed/") -> url
                .replace("/embed/", "/watch?v=")
                .substringBefore("?")
            else -> url
        }
    }

    override suspend fun getTvShow(id: String): TvShow {
        throw Exception("TV shows not supported")
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        throw Exception("TV shows not supported")
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val base = if (id.startsWith("http")) id.removeSuffix("/") else "$baseUrl/${id.removePrefix("/").removeSuffix("/")}"
        if (page > 1) {
            // Check if the category has pagination on the first page
            val firstDoc = service.getPage("$base/")
            val name = firstDoc.selectFirst(".section-header .section-title, h1.section-title, h1")?.text()?.trim() ?: ""
            val hasPager = firstDoc.selectFirst(".navigation.pagination .nav-links a.page-link") != null
            if (!hasPager) {
                return Genre(id = id, name = name, shows = emptyList())
            }
            val doc = service.getPage("$base/page/$page/")
        val shows: List<Show> = doc.select("a.card[href*=/film-streaming/], ul.post-lst li").mapNotNull { li: Element -> parseGridItem(li) as? Show }
            return Genre(id = id, name = name, shows = shows)
        } else {
            val doc = service.getPage("$base/")
            val name = doc.selectFirst(".section-header .section-title, h1.section-title, h1")?.text()?.trim() ?: ""
            val shows: List<Show> = doc.select("a.card[href*=/film-streaming/], ul.post-lst li").mapNotNull { li: Element -> parseGridItem(li) as? Show }
            return Genre(id = id, name = name, shows = shows)
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        val doc = service.getPage(id)

        val name = doc.selectFirst(".section-header .section-title, h1.section-title, h1")?.text()?.trim()
            ?: ""

        if (page > 1) {
            return People(
                id = id,
                name = name,
                filmography = emptyList()
            )
        }

        val filmography = doc.select("a.card[href*=/film-streaming/], ul.post-lst li").mapNotNull { li: Element ->
            parseGridItem(li) as? Show
        }

        return People(
            id = id,
            name = name,
            filmography = filmography
        )
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val doc = service.getPage(id)
        val servers = mutableListOf<Video.Server>()
        val html = doc.html()
        val origin = baseUrl.trimEnd('/')

        // New GuardaFlix self-hosted HLS player: initialSrc='/hls/sN/movie/tt.../playlist.m3u8'
        Regex("""initialSrc\s*=\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.getOrNull(1)?.let { path ->
            val src = normalizeUrl(path)
            if (src.contains(".m3u8")) {
                servers += Video.Server(id = src, name = "GuardaFlix HLS", src = src)
            }
        }
        Regex("""hls_url\s*:\s*['"]([^'"]+)['"]""").findAll(html).forEach { match ->
            val src = normalizeUrl(match.groupValues[1])
            if (src.contains(".m3u8") && servers.none { it.src == src }) {
                servers += Video.Server(id = src, name = "GuardaFlix HLS", src = src)
            }
        }

        if (servers.isEmpty()) {
            val imdbId = Regex("""/(tt\d+)""").find(id)?.groupValues?.getOrNull(1)
            if (imdbId != null) {
                for (slot in 1..5) {
                    val candidate = "$origin/hls/s$slot/movie/$imdbId/playlist.m3u8"
                    servers += Video.Server(id = candidate, name = "Server s$slot", src = candidate)
                }
            }
        }

        suspend fun addEmbed(raw: String?, index: Int, label: String? = null) {
            val firstUrl = raw?.trim().orEmpty()
            if (firstUrl.isBlank()) return
            try {
                val embedDoc = service.getPage(firstUrl)
                val finalIframe = embedDoc.selectFirst(".Video iframe[src], iframe[src], iframe[data-src]")
                    ?.let { it.attr("src").ifBlank { it.attr("data-src") } }
                    ?.trim()
                    ?: firstUrl
                val hostName = runCatching {
                    finalIframe.toHttpUrl().host
                        .replaceFirst("www.", "")
                        .substringBefore(".")
                        .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
                }.getOrDefault("Server")
                servers += Video.Server(
                    id = finalIframe,
                    name = label ?: "Opzione ${index + 1} - $hostName",
                    src = finalIframe
                )
            } catch (_: Exception) {
                servers += Video.Server(id = firstUrl, name = label ?: "Opzione ${index + 1}", src = firstUrl)
            }
        }

        // Legacy WordPress aa-options embeds
        doc.select("#aa-options div[id^=options-]").forEachIndexed { index, optionDiv ->
            val rawIframe = optionDiv.selectFirst("iframe[data-src]")?.attr("data-src")
                ?: optionDiv.selectFirst("iframe")?.attr("src")
            addEmbed(rawIframe, index)
        }

        if (servers.isEmpty()) {
            doc.select("iframe[src], iframe[data-src], li[data-src], .player iframe").forEachIndexed { index, el ->
                val src = el.attr("data-src").ifBlank { el.attr("src") }
                addEmbed(src, index)
            }
        }

        return servers.distinctBy { it.src.ifBlank { it.id } }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val link = server.src.ifBlank { server.id }
        if (link.contains(".m3u8")) {
            val origin = baseUrl.trimEnd('/')
            val signed = resolveGrantedHls(link)
            return Video(
                source = signed,
                headers = mapOf(
                    "Referer" to "$origin/",
                    "Origin" to origin,
                    "User-Agent" to BROWSER_UA,
                    "Accept" to "*/*",
                )
            )
        }
        return Extractor.extract(link, server)
    }

    /**
     * GuardaFlix serves unsigned `/hls/sN/...` playlists that 404 until
     * `/api/play/grant` returns a signed `/hls/p/{exp}/{sig}/...` URL.
     * Guests get preview mode; members get full streams.
     */
    private fun resolveGrantedHls(link: String): String {
        val origin = baseUrl.trimEnd('/')
        val plainPath = link.substringAfter(origin, missingDelimiterValue = link).let {
            if (it.startsWith("http")) {
                try {
                    it.toHttpUrl().encodedPath
                } catch (_: Exception) {
                    it
                }
            } else it
        }.let { if (it.startsWith("/")) it else "/$it" }

        val grantQuery = when {
            Regex("""^/hls/s[1-5]/movie/([^/]+)/playlist\.m3u8$""").matchEntire(plainPath) != null -> {
                val id = Regex("""^/hls/s[1-5]/movie/([^/]+)/playlist\.m3u8$""")
                    .matchEntire(plainPath)!!.groupValues[1]
                "type=movie&id=${URLEncoder.encode(id, "UTF-8")}"
            }
            Regex("""^/hls/s[1-5]/serial/([^/]+)/(\d+)/(\d+)/playlist\.m3u8$""").matchEntire(plainPath) != null -> {
                val m = Regex("""^/hls/s[1-5]/serial/([^/]+)/(\d+)/(\d+)/playlist\.m3u8$""")
                    .matchEntire(plainPath)!!
                "type=episode&id=${URLEncoder.encode(m.groupValues[1], "UTF-8")}" +
                    "&season=${m.groupValues[2]}&episode=${m.groupValues[3]}"
            }
            else -> null
        } ?: return if (link.startsWith("http")) link else normalizeUrl(link)

        // Already signed
        if (plainPath.contains("/hls/p/")) {
            return if (link.startsWith("http")) link else normalizeUrl(link)
        }

        return try {
            val client = OkHttpClient.Builder()
                .dns(DnsResolver.doh)
                .followRedirects(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            val grantUrl = "$origin/api/play/grant?$grantQuery&_=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(grantUrl)
                .header("User-Agent", BROWSER_UA)
                .header("Accept", "application/json")
                .header("Referer", "$origin/")
                .header("Origin", origin)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return if (link.startsWith("http")) link else normalizeUrl(link)
                }
                val body = response.body?.string().orEmpty()
                val signedPath = JSONObject(body).optString("url").orEmpty()
                when {
                    signedPath.startsWith("http") -> signedPath
                    signedPath.startsWith("/") -> "$origin$signedPath"
                    signedPath.isNotBlank() -> normalizeUrl(signedPath)
                    else -> if (link.startsWith("http")) link else normalizeUrl(link)
                }
            }
        } catch (_: Exception) {
            if (link.startsWith("http")) link else normalizeUrl(link)
        }
    }
}
