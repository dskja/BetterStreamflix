package com.dskja.betterstreamflix.providers

import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.extractors.Extractor
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Genre
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.People
import com.dskja.betterstreamflix.models.Season
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import retrofit2.http.POST
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Headers
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

import MyCookieJar
import android.util.Base64
import com.dskja.betterstreamflix.utils.TmdbUtils
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.text.Charsets

object MEGAKinoProvider : Provider, ProviderConfigUrl {

    override val name = "MEGAKino"
    override val defaultBaseUrl = "https://megakino.me/"
    override val baseUrl: String = defaultBaseUrl
        get() {
            val cachedUrl = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL)
            return cachedUrl.ifBlank { field }
        }
    override val logo = "https://images2.imgbox.com/a2/83/OubSojBq_o.png"
    override val language = "de"
    override val changeUrlMutex = Mutex()

    private const val DEFAULT_AGENT = "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0"

    private interface MEGAKinoService {
        @Headers(DEFAULT_AGENT)
        @GET(".")
        suspend fun getHome(): Document

        @Headers(DEFAULT_AGENT)
        @GET("index.php?yg=token")
        suspend fun getToken(): ResponseBody

        @Headers(DEFAULT_AGENT)
        @GET
        suspend fun getDocument(@Url url: String): Document

        @Headers(DEFAULT_AGENT)
        @GET("{path}page/{page}/")
        suspend fun getPage(@Path(value = "path", encoded = true) path: String, @Path("page") page: Int): Document

        @Headers(DEFAULT_AGENT)
        @GET("/films/")
        suspend fun getFilms(): Document

        @Headers(DEFAULT_AGENT)
        @GET("/serials/")
        suspend fun getSerials(): Document

        @Headers(DEFAULT_AGENT)
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

        companion object {
            fun build(baseUrl: String): MEGAKinoService {
                val client = OkHttpClient.Builder()
                    .cookieJar(MyCookieJar())
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .build()

                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                    .create(MEGAKinoService::class.java)
            }
        }
    }

    @Volatile
    private var service = MEGAKinoService.build(defaultBaseUrl)
    @Volatile
    private var serviceBaseUrl: String = defaultBaseUrl

    private var lastTokenTime = 0L

    private fun normalizedBaseUrl(): String =
        baseUrl.trim().removeSuffix("/") + "/"

    private fun absoluteUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = normalizedBaseUrl().removeSuffix("/")
        return if (path.startsWith("/")) "$base$path" else "$base/$path"
    }

    private fun getService(): MEGAKinoService {
        val currentBase = normalizedBaseUrl()
        val cached = service
        if (serviceBaseUrl == currentBase) return cached
        synchronized(this) {
            if (serviceBaseUrl == currentBase) return service
            return MEGAKinoService.build(currentBase).also {
                service = it
                serviceBaseUrl = currentBase
                lastTokenTime = 0L
            }
        }
    }

    override suspend fun onChangeUrl(forceRefresh: Boolean): String {
        changeUrlMutex.withLock {
            val currentBase = normalizedBaseUrl()
            service = MEGAKinoService.build(currentBase)
            serviceBaseUrl = currentBase
            lastTokenTime = 0L
        }
        return normalizedBaseUrl()
    }

    private suspend fun ensureToken() {
        if (System.currentTimeMillis() - lastTokenTime > 10 * 60 * 1000) {
            try {
                getService().getToken()
                lastTokenTime = System.currentTimeMillis()
            } catch (e: Exception) {
            }
        }
    }

    private fun parseContentItems(element: Element): List<AppAdapter.Item> {
        return element.select("div#dle-content a.poster.grid-item").mapNotNull { el ->
            val href = el.attr("href")
            val title = el.select("h3.poster__title").text().trim()
            val posterPath = el.select("div.poster__img img").attr("data-src")
            val posterUrl = absoluteUrl(posterPath)
            
            if (href.contains("/serials/")) {
                TvShow(
                    id = href,
                    title = title,
                    poster = posterUrl
                )
            } else {
                Movie(
                    id = href,
                    title = title,
                    poster = posterUrl
                )
            }
        }
    }

    override suspend fun getHome(): List<Category> {
        ensureToken()
        val document = getService().getHome()
        val categories = mutableListOf<Category>()

        val sections = document.select("section.sect")
        val section = sections.find {
            it.select("h2.sect__title").text().contains("Topaktuelle Neuheiten", ignoreCase = true)
        } ?: sections.find {
            it.select("div#dle-content a.poster.grid-item").isNotEmpty()
        }

        if (section != null) {
            val items = parseContentItems(section)
            if (items.isNotEmpty()) {
                val title = section.select("h2.sect__title").text().trim()
                    .ifBlank { "Topaktuelle Neuheiten" }
                categories.add(Category(name = title, list = items))
            }
        }

        return categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        ensureToken()
        if (query.isEmpty()) {
            val document = getService().getHome()
            val genres = mutableListOf<AppAdapter.Item>()

            val genreBlock = document.select("div.side-block:has(div.side-block__title:contains(Genres))").firstOrNull() 
                ?: document.select("div.side-block").find { it.select("div.side-block__title").text() == "Genres" }

            if (genreBlock != null) {
                genreBlock.select("ul.side-block__content li a").forEach { element ->
                    val id = element.attr("href")
                    val name = element.text()
                    if (id.isNotEmpty() && name.isNotEmpty()) {
                        genres.add(Genre(id, name))
                    }
                }
            }
            return genres
        }
        
        val resultFrom = (page - 1) * 20 + 1
        
        return try {
            val document = getService().search(
                searchStart = page,
                resultFrom = resultFrom,
                story = query
            )
            
            parseContentItems(document)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        ensureToken()
        val document = if (page > 1) {
            val path = id.removePrefix(normalizedBaseUrl()).removePrefix(baseUrl).removePrefix("/").removeSuffix("/") + "/"
            getService().getPage(path, page)
        } else {
            getService().getDocument(absoluteUrl(id))
        }

        val genreName = document.select("h2.sect__title").text().trim().ifEmpty { id }

        val items = parseContentItems(document)
        
        if (items.isEmpty()) {
             if (page > 1) return Genre(id, genreName, emptyList())
        }

        return Genre(id, genreName, items.filterIsInstance<com.dskja.betterstreamflix.models.Show>())
    }

    override suspend fun getPeople(id: String, page: Int): People {
        ensureToken()
        val document = getService().getDocument(absoluteUrl(id))
        
        val name = document.select("h1").text().trim()
        
        if (page > 1) {
            return People(id = id, name = name, filmography = emptyList())
        }
        
        val filmography = parseContentItems(document).filterIsInstance<com.dskja.betterstreamflix.models.Show>()
        
        return People(
            id = id,
            name = name,
            filmography = filmography
        )
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        ensureToken()
        val document = if (page > 1) {
            getService().getPage("films/", page)
        } else {
            getService().getFilms()
        }
        
        return parseContentItems(document).filterIsInstance<Movie>()
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        ensureToken()
        val document = if (page > 1) {
            getService().getPage("serials/", page)
        } else {
            getService().getSerials()
        }
        
        return parseContentItems(document).filterIsInstance<TvShow>()
    }

    override suspend fun getMovie(id: String): Movie {
        ensureToken()
        val document = getService().getDocument(absoluteUrl(id))

        val title = document.select("h1[itemprop='name']").text().trim()
        val tmdbMovie = TmdbUtils.getMovie(title, language = language)
        
        val posterPath = document.select("div.pmovie__poster img[itemprop='image']").attr("data-src")
        val posterUrl = absoluteUrl(posterPath)
        val quality = document.select("div.pmovie__poster div.poster__label").text().trim()
        val overview = document.select("div.page__text[itemprop='description']").text().trim()
        
        val yearElement = document.select("div.pmovie__year span[itemprop='dateCreated']").text()
        val released = yearElement.trim()

        val trailer = document.select("link[itemprop='embedUrl']").attr("href")
            .replace("/embed/", "/watch?v=")

        val genres = document.select("div.pmovie__genres[itemprop='genre']").text()
            .split("/")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { Genre(it, it) }

        val cast = document.select("span[itemprop='actors'] a").map { element ->
            val actorName = element.text().trim()
            val tmdbPerson = tmdbMovie?.cast?.find { it.name.equals(actorName, ignoreCase = true) }
            People(
                id = element.attr("href"),
                name = actorName,
                image = tmdbPerson?.image
            )
        }

        return Movie(
            id = id,
            title = title,
            poster = tmdbMovie?.poster ?: posterUrl,
            banner = tmdbMovie?.banner,
            overview = tmdbMovie?.overview ?: overview,
            released = tmdbMovie?.released?.let { "${it.get(java.util.Calendar.YEAR)}" } ?: released,
            quality = quality,
            trailer = tmdbMovie?.trailer ?: trailer,
            rating = tmdbMovie?.rating,
            runtime = tmdbMovie?.runtime,
            genres = tmdbMovie?.genres ?: genres,
            cast = cast,
            imdbId = tmdbMovie?.imdbId
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        ensureToken()
        val document = getService().getDocument(absoluteUrl(id))

        val titleRaw = document.select("h1[itemprop='name']").text().trim()
        
        val seasonMatch = Regex("""- (\d+) Staffel""").find(titleRaw)
        val seasonNumber = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        
        val titleForTmdb = titleRaw.replace(Regex("""\s*-\s*\d+\s*Staffel\s*$"""), "").trim()
        
        val tmdbTvShow = TmdbUtils.getTvShow(titleForTmdb, language = language)
        
        val posterPath = document.select("div.pmovie__poster img[itemprop='image']").attr("data-src")
        val posterUrl = absoluteUrl(posterPath)
        val overview = document.select("div.page__text[itemprop='description']").text().trim()
        val released = document.select("div.pmovie__year span[itemprop='dateCreated']").text().trim()

        val seasons = listOf(
            Season(
                id = id, 
                number = seasonNumber,
                title = "Episode",
                poster = tmdbTvShow?.seasons?.find { it.number == seasonNumber }?.poster
            )
        )

        val trailer = document.select("link[itemprop='embedUrl']").attr("href")
            .replace("/embed/", "/watch?v=")

        val genres = document.select("div.pmovie__genres[itemprop='genre']").text()
            .split("/")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { Genre(id = it, name = it) }

        val cast = document.select("span[itemprop='actors'] a").map { element ->
            val actorName = element.text().trim()
            val tmdbPerson = tmdbTvShow?.cast?.find { it.name.equals(actorName, ignoreCase = true) }
            People(
                id = element.attr("href"),
                name = actorName,
                image = tmdbPerson?.image
            )
        }

        return TvShow(
            id = id,
            title = titleRaw,
            poster = tmdbTvShow?.poster ?: posterUrl,
            banner = tmdbTvShow?.banner,
            overview = tmdbTvShow?.overview ?: overview,
            released = tmdbTvShow?.released?.let { "${it.get(java.util.Calendar.YEAR)}" } ?: released,
            seasons = seasons,
            trailer = tmdbTvShow?.trailer ?: trailer,
            rating = tmdbTvShow?.rating,
            runtime = tmdbTvShow?.runtime,
            genres = tmdbTvShow?.genres ?: genres,
            cast = cast,
            imdbId = tmdbTvShow?.imdbId
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        ensureToken()
        val document = getService().getDocument(absoluteUrl(seasonId))
        
        val titleRaw = document.select("h1[itemprop='name']").text().trim()
        val seasonMatch = Regex("""- (\d+) Staffel""").find(titleRaw)
        val seasonNumber = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val titleForTmdb = titleRaw.replace(Regex("""\s*-\s*\d+\s*Staffel\s*$"""), "").trim()
        
        val tmdbTvShow = TmdbUtils.getTvShow(titleForTmdb, language = language)
        val tmdbEpisodes = tmdbTvShow?.let { 
            TmdbUtils.getEpisodesBySeason(it.id, seasonNumber, language = language) 
        } ?: emptyList()
        
        val episodes = mutableListOf<Episode>()
        val options = document.select("select.se-select option")
        
        options.forEach { option ->
            val value = option.attr("value")
            val name = option.text()
            val episodeId = "$seasonId|$value"
            
            val number = Regex("Episode\\s+(\\d+)").find(name)?.groupValues?.get(1)?.toIntOrNull() 
                ?: (episodes.size + 1)

            val tmdbEp = tmdbEpisodes.find { it.number == number }

            episodes.add(
                Episode(
                    id = episodeId,
                    number = number,
                    title = tmdbEp?.title ?: name,
                    poster = tmdbEp?.poster,
                    overview = tmdbEp?.overview
                )
            )
        }
        return episodes
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        ensureToken()
        val servers = mutableListOf<Video.Server>()

        if (videoType is Video.Type.Movie) {
            val document = getService().getDocument(absoluteUrl(id))

            val tabNames = document.select("div.tabs-block__select span, .tabs-block__select span, .player-tabs span, .nav-tabs a")
                .map { it.text().trim() }
            val contents = document.select("div.tabs-block__content, .tabs-block__content, .tab-content .tab-pane, .player iframe")

            contents.forEachIndexed { index, content ->
                val iframe = content.takeIf { it.tagName() == "iframe" } ?: content.selectFirst("iframe")
                val serverSrc = iframe?.attr("data-src")?.takeIf { it.isNotEmpty() }
                    ?: iframe?.attr("src")

                if (!serverSrc.isNullOrEmpty() && !serverSrc.contains("youtube", ignoreCase = true)) {
                    val serverName = tabNames.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Server ${index + 1}"
                    servers.add(Video.Server(id = serverSrc, name = serverName, src = absoluteUrl(serverSrc)))
                }

                // Current layout: tab panels link to /dl/<id> instead of iframes.
                content.select("a[href*='/dl/']").forEach { link ->
                    val href = link.attr("href").trim()
                    if (href.isBlank()) return@forEach
                    val resolved = resolveDlStream(href)
                    val name = tabNames.getOrNull(index)?.takeIf { it.isNotBlank() }
                        ?: link.text().trim().ifBlank { "Server ${servers.size + 1}" }
                    if (!resolved.isNullOrBlank()) {
                        servers.add(Video.Server(id = resolved, name = name, src = resolved))
                    } else {
                        // Keep the /dl/ URL so getVideo can retry / surface a clear VPN error.
                        servers.add(Video.Server(id = absoluteUrl(href), name = name, src = absoluteUrl(href)))
                    }
                }
            }

            if (servers.isEmpty()) {
                document.select("iframe[src], iframe[data-src], [data-src*=http]").forEachIndexed { index, iframe ->
                    val serverSrc = iframe.attr("data-src").ifBlank { iframe.attr("src") }
                    if (serverSrc.isNotBlank() && !serverSrc.contains("youtube", ignoreCase = true)) {
                        servers.add(Video.Server(id = serverSrc, name = "Server ${index + 1}", src = serverSrc))
                    }
                }
            }

            if (servers.isEmpty()) {
                document.select("a[href*='/dl/']").forEachIndexed { index, link ->
                    val href = link.attr("href").trim()
                    if (href.isBlank()) return@forEachIndexed
                    val resolved = resolveDlStream(href)
                    val src = resolved ?: absoluteUrl(href)
                    servers.add(Video.Server(id = src, name = "Server ${index + 1}", src = src))
                }
            }

            // Fallback: meinecloud embed via IMDb id when present on the page.
            if (servers.isEmpty()) {
                val imdb = Regex("""tt\d{7,8}""").find(document.html())?.value
                if (!imdb.isNullOrBlank()) {
                    val embed = "https://meinecloud.click/movie/$imdb"
                    servers.addAll(parseMeinecloudMirrors(embed))
                }
            }
        } else if (videoType is Video.Type.Episode) {
            val parts = id.split("|")
            if (parts.size >= 2) {
                val pageUrl = parts[0]
                val epId = parts[1]
                val document = getService().getDocument(absoluteUrl(pageUrl))

                val select = document.select("select#$epId, select.episode-servers, select[name*=server]")
                select.select("option").forEach { option ->
                    val serverUrl = option.attr("value")
                    val serverName = option.text()
                    if (serverUrl.isNotEmpty()) {
                        servers.add(Video.Server(id = serverUrl, name = serverName, src = serverUrl))
                    }
                }

                if (servers.isEmpty()) {
                    document.select("iframe[src], iframe[data-src], a[href*='/dl/']").forEachIndexed { index, el ->
                        val serverSrc = when {
                            el.tagName() == "a" -> el.attr("href")
                            else -> el.attr("data-src").ifBlank { el.attr("src") }
                        }
                        if (serverSrc.isNotBlank()) {
                            val resolved = if (serverSrc.contains("/dl/")) resolveDlStream(serverSrc) else serverSrc
                            servers.add(
                                Video.Server(
                                    id = resolved ?: absoluteUrl(serverSrc),
                                    name = "Server ${index + 1}",
                                    src = resolved ?: absoluteUrl(serverSrc),
                                )
                            )
                        }
                    }
                }
            }
        }

        val distinct = servers.distinctBy { it.src.ifBlank { it.id } }
        if (distinct.isEmpty()) {
            throw Exception(
                "Keine Stream-Server gefunden. MEGAKino verlangt oft eine VPN-Verbindung " +
                    "(/dl/ Seiten zeigen nur den VPN-Hinweis)."
            )
        }
        return distinct
    }

    /** Follow /dl/<id> softgate pages and extract an external embed if present. */
    private suspend fun resolveDlStream(href: String): String? {
        val url = absoluteUrl(href)
        return runCatching {
            val doc = getService().getDocument(url)
            val text = doc.text()
            if (text.contains("VPN", ignoreCase = true) &&
                (text.contains("Verschlüsselung", ignoreCase = true) || text.contains("einrichten", ignoreCase = true))
            ) {
                return@runCatching null
            }
            val iframe = doc.selectFirst("iframe[src], iframe[data-src]")
            val src = iframe?.attr("data-src")?.ifBlank { null } ?: iframe?.attr("src")
            src?.takeIf { it.isNotBlank() && !it.contains("youtube", ignoreCase = true) }
                ?: doc.selectFirst("a[href^=http]")?.attr("href")
                    ?.takeIf { link ->
                        listOf("voe", "mixdrop", "streamtape", "vidoza", "dood", "filemoon", "meinecloud")
                            .any { host -> link.contains(host, ignoreCase = true) }
                    }
        }.getOrNull()
    }

    private suspend fun parseMeinecloudMirrors(embedUrl: String): List<Video.Server> {
        return runCatching {
            val embedDoc = getService().getDocument(embedUrl)
            embedDoc.select("ul._source_list li[data-link], li[data-link]").mapNotNull { li ->
                val raw = li.attr("data-link").trim()
                if (raw.isBlank()) return@mapNotNull null
                val decoded = runCatching {
                    String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8).trim()
                }.getOrDefault(raw)
                val normalized = when {
                    decoded.startsWith("//") -> "https:$decoded"
                    decoded.startsWith("http") -> decoded
                    else -> return@mapNotNull null
                }
                val name = li.ownText().ifBlank { li.text() }.trim().ifBlank { "Server" }
                Video.Server(id = normalized, name = name, src = normalized)
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val src = server.src.ifBlank { server.id }
        if (src.contains("/dl/")) {
            val resolved = resolveDlStream(src)
                ?: throw Exception(
                    "MEGAKino Stream gesperrt (VPN erforderlich). " +
                        "Bitte VPN aktivieren oder die Provider-URL in den Einstellungen ändern."
                )
            return Extractor.extract(resolved)
        }
        return Extractor.extract(src)
    }
}
