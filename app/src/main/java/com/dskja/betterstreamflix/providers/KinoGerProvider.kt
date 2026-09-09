package com.dskja.betterstreamflix.providers

import MyCookieJar
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
import com.dskja.betterstreamflix.utils.TmdbUtils
import com.dskja.betterstreamflix.utils.UserPreferences
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
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

    private const val DEFAULT_AGENT =
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0"

    private interface KinoGerService {
        @Headers(DEFAULT_AGENT)
        @GET(".")
        suspend fun getHome(): Document

        @Headers(DEFAULT_AGENT)
        @GET("kinofilme-online/")
        suspend fun getMovies(): Document

        @Headers(DEFAULT_AGENT)
        @GET("kinofilme-online/page/{page}/")
        suspend fun getMovies(@Path("page") page: Int): Document

        @Headers(DEFAULT_AGENT)
        @GET("serienstream-deutsch/")
        suspend fun getTvShows(): Document

        @Headers(DEFAULT_AGENT)
        @GET("serienstream-deutsch/page/{page}/")
        suspend fun getTvShows(@Path("page") page: Int): Document

        @Headers(DEFAULT_AGENT)
        @GET
        suspend fun getDocument(@Url url: String): Document

        @Headers(DEFAULT_AGENT)
        @GET("{path}page/{page}/")
        suspend fun getPage(
            @Path(value = "path", encoded = true) path: String,
            @Path("page") page: Int
        ): Document

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
            fun build(baseUrl: String): KinoGerService {
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
                    .create(KinoGerService::class.java)
            }
        }
    }

    @Volatile
    private var service = KinoGerService.build(defaultBaseUrl)
    @Volatile
    private var serviceBaseUrl: String = defaultBaseUrl

    private fun normalizedBaseUrl(): String =
        baseUrl.trim().removeSuffix("/") + "/"

    private fun absoluteUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        if (path.startsWith("//")) return "https:$path"
        val base = normalizedBaseUrl().removeSuffix("/")
        return if (path.startsWith("/")) "$base$path" else "$base/$path"
    }

    private fun getService(): KinoGerService {
        val currentBase = normalizedBaseUrl()
        val cached = service
        if (serviceBaseUrl == currentBase) return cached
        synchronized(this) {
            if (serviceBaseUrl == currentBase) return service
            return KinoGerService.build(currentBase).also {
                service = it
                serviceBaseUrl = currentBase
            }
        }
    }

    override suspend fun onChangeUrl(forceRefresh: Boolean): String {
        changeUrlMutex.withLock {
            val currentBase = normalizedBaseUrl()
            service = KinoGerService.build(currentBase)
            serviceBaseUrl = currentBase
        }
        return normalizedBaseUrl()
    }

    private fun cleanTitle(raw: String): String =
        raw.replace(Regex("""\s*\(\d{4}\)\s*$"""), "").trim()

    private fun isSeriesCard(el: Element, title: String): Boolean {
        if (el.selectFirst(".serie-num") != null) return true
        if (title.contains("Staffel", ignoreCase = true)) return true
        val cats = el.select(".content_text").text()
        return cats.contains("Serien", ignoreCase = true)
    }

    private fun parseShort(el: Element): AppAdapter.Item? {
        val link = el.selectFirst(".title a[href$=.html]") ?: return null
        val href = link.attr("href").trim()
        if (href.isBlank()) return null
        val titleRaw = link.text().trim()
        if (titleRaw.isBlank()) return null
        val posterPath = el.selectFirst(".content_text img")?.attr("src").orEmpty()
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
            else -> null
        }
    }

    override suspend fun getHome(): List<Category> {
        val document = getService().getHome()
        val items = parseShorts(document)
        if (items.isEmpty()) return emptyList()
        return listOf(Category(name = "Kino Stream", list = items))
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
            val document = getService().search(
                searchStart = page,
                resultFrom = resultFrom,
                story = query
            )
            parseShorts(document)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val document = if (page > 1) {
            getService().getMovies(page)
        } else {
            getService().getMovies()
        }
        return parseShorts(document).filterIsInstance<Movie>()
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val document = if (page > 1) {
            getService().getTvShows(page)
        } else {
            getService().getTvShows()
        }
        return parseShorts(document).filterIsInstance<TvShow>()
    }

    override suspend fun getMovie(id: String): Movie {
        val document = getService().getDocument(absoluteUrl(id))
        val titleRaw = document.selectFirst("h1#news-title, h1.title, h1")?.text()?.trim().orEmpty()
        val title = cleanTitle(titleRaw)
        val tmdbMovie = TmdbUtils.getMovie(title, language = language)

        val posterPath = document.selectFirst(".content_text img, .full-text img, img[itemprop=image]")
            ?.attr("src").orEmpty()
        val overview = document.selectFirst(".full-text, .content_text")?.text()?.trim()
        val year = Regex("""\((\d{4})\)""").find(titleRaw)?.groupValues?.get(1)

        return Movie(
            id = id,
            title = title,
            poster = tmdbMovie?.poster ?: absoluteUrl(posterPath),
            banner = tmdbMovie?.banner,
            overview = tmdbMovie?.overview ?: overview,
            released = tmdbMovie?.released?.let { "${it.get(Calendar.YEAR)}" } ?: year,
            rating = tmdbMovie?.rating,
            runtime = tmdbMovie?.runtime,
            genres = tmdbMovie?.genres ?: emptyList(),
            cast = tmdbMovie?.cast ?: emptyList(),
            trailer = tmdbMovie?.trailer,
            imdbId = tmdbMovie?.imdbId
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        val document = getService().getDocument(absoluteUrl(id))
        val titleRaw = document.selectFirst("h1#news-title, h1.title, h1")?.text()?.trim().orEmpty()
        val seasonNumber = Regex("""Staffel\s+(\d+)""", RegexOption.IGNORE_CASE)
            .find(titleRaw)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val titleForTmdb = cleanTitle(titleRaw)
            .replace(Regex("""\s*-\s*Staffel\s+\d+\s*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        val tmdbTvShow = TmdbUtils.getTvShow(titleForTmdb, language = language)

        val posterPath = document.selectFirst(".content_text img, .full-text img, img[itemprop=image]")
            ?.attr("src").orEmpty()
        val overview = document.selectFirst(".full-text, .content_text")?.text()?.trim()
        val year = Regex("""\((\d{4})\)""").find(titleRaw)?.groupValues?.get(1)

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
            released = tmdbTvShow?.released?.let { "${it.get(Calendar.YEAR)}" } ?: year,
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
        val document = getService().getDocument(absoluteUrl(showUrl))

        val titleRaw = document.selectFirst("h1#news-title, h1.title, h1")?.text()?.trim().orEmpty()
        val titleForTmdb = cleanTitle(titleRaw)
            .replace(Regex("""\s*-\s*Staffel\s+\d+\s*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        val tmdbTvShow = TmdbUtils.getTvShow(titleForTmdb, language = language)
        val tmdbEpisodes = tmdbTvShow?.let {
            TmdbUtils.getEpisodesBySeason(it.id, seasonNumber, language = language)
        } ?: emptyList()

        return parseEpisodesFromDocument(absoluteUrl(showUrl), seasonNumber, document, tmdbEpisodes)
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            val document = if (page <= 1) {
                getService().getDocument(absoluteUrl(id))
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
        // KinoGer does not expose cast filmography pages; keep a safe placeholder.
        return People(id = id, name = id.substringAfterLast('/').ifBlank { "Unknown" }, filmography = emptyList())
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return when (videoType) {
            is Video.Type.Movie -> {
                val document = getService().getDocument(absoluteUrl(id))
                document.select(".player-mirrors span[data-link]").mapNotNull { span ->
                    val link = normalizeStreamUrl(span.attr("data-link")) ?: return@mapNotNull null
                    if (link.contains("youtube", ignoreCase = true)) return@mapNotNull null
                    val label = span.ownText().ifBlank { span.text() }.trim()
                    val name = hosterDisplayName(link, label)
                    Video.Server(id = link, name = name, src = link)
                }.distinctBy { it.src }
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

                val document = getService().getDocument(absoluteUrl(pageUrl))
                val li = document.selectFirst("ul.ep-menu li#serie-${season}_${episode}")
                    ?: document.selectFirst("ul.ep-menu li[id=serie-${season}_${episode}]")
                val links = li?.select("a[data-link]").orEmpty()
                links.mapNotNull { a ->
                    val link = normalizeStreamUrl(a.attr("data-link")) ?: return@mapNotNull null
                    if (link.contains("youtube", ignoreCase = true)) return@mapNotNull null
                    val label = a.ownText().ifBlank { a.text() }.trim()
                    val name = hosterDisplayName(link, label)
                    Video.Server(id = link, name = name, src = link)
                }.distinctBy { it.src }
            }
        }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val src = server.src.ifBlank { server.id }
        return Extractor.extract(src)
    }
}
