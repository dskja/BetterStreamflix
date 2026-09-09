package com.dskja.betterstreamflix.providers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import com.dskja.betterstreamflix.utils.UserPreferences

import android.util.Log
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object PelisplustoProvider : Provider, ProviderConfigUrl {

    override val name = "Pelisplusto"
    // pelisplus.to often fails DNS; pelisplushd.bz is the live Sept 2026 mirror.
    override val defaultBaseUrl = "https://pelisplushd.bz/"
    override val baseUrl: String
        get() = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL).ifBlank { defaultBaseUrl }
    override val changeUrlMutex = Mutex()

    override suspend fun onChangeUrl(forceRefresh: Boolean): String = changeUrlMutex.withLock {
        baseUrl
    }
    override val language = "es"
    override val logo = "https://pelisplushd.bz/images/logo2.png"
    private const val TAG = "PelisplustoProvider"

    private val VIDEO_ASSIGN_REGEX = Regex("""video\[(\d+)\]\s*=\s*['"]([^'"]+)['"]""")
    private val TEMPORADA_CAPITULO_REGEX =
        Regex("""/(?:serie|anime)/[^/]+/temporada/(\d+)/capitulo/(\d+)""", RegexOption.IGNORE_CASE)

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .header("Referer", baseUrl)
                .build()
            chain.proceed(request)
        }
        .readTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .dns(DnsResolver.doh)
        .build()

    private val service = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .addConverterFactory(JsoupConverterFactory.create())
        .client(client)
        .build()
        .create(PelisplustoService::class.java)

    private suspend fun getPageOrThrow(url: String): Document {
        return try {
            service.getPage(url)
        } catch (e: Exception) {
            val hostError = e.message.orEmpty()
            if (hostError.contains("Unable to resolve host", ignoreCase = true) ||
                hostError.contains("UnknownHost", ignoreCase = true) ||
                hostError.contains("No address associated", ignoreCase = true)
            ) {
                throw Exception(
                    "Pelisplusto DNS failed for $baseUrl. Try setting the provider URL to pelisplushd.bz. (${e.message})"
                )
            }
            throw e
        }
    }

    private interface PelisplustoService {
        @GET
        suspend fun getPage(@Url url: String): Document
    }

    override suspend fun getHome(): List<Category> = coroutineScope {
        val categories = mutableListOf<Category>()

        val mainPageDeferred = async { getPageOrThrow(baseUrl) }
        val moviesDeferred = async { getPageOrThrow("$baseUrl/peliculas") }
        val seriesDeferred = async { getPageOrThrow("$baseUrl/series") }
        val animesDeferred = async { getPageOrThrow("$baseUrl/animes") }

        try {
            val featured = parseShows(mainPageDeferred.await()).take(12).mapNotNull { item ->
                when (item) {
                    is Movie -> item.copy(banner = item.poster, poster = null)
                    is TvShow -> item.copy(banner = item.poster, poster = null)
                    else -> null
                }
            }
            if (featured.isNotEmpty()) {
                categories.add(Category(Category.FEATURED, featured))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getHome (featured): ${e.message}")
        }

        try {
            val movies = parseShows(moviesDeferred.await()).filterIsInstance<Movie>()
            if (movies.isNotEmpty()) categories.add(Category("Películas", movies))
        } catch (e: Exception) {
            Log.e(TAG, "getHome (movies): ${e.message}")
        }

        try {
            val series = parseShows(seriesDeferred.await()).filterIsInstance<TvShow>()
            if (series.isNotEmpty()) categories.add(Category("Series", series))
        } catch (e: Exception) {
            Log.e(TAG, "getHome (series): ${e.message}")
        }

        try {
            val animes = parseShows(animesDeferred.await()).filterIsInstance<TvShow>()
            if (animes.isNotEmpty()) categories.add(Category("Animes", animes))
        } catch (e: Exception) {
            Log.e(TAG, "getHome (animes): ${e.message}")
        }

        if (categories.isEmpty()) {
            throw Exception(
                "PelisPlus+ returned an empty homepage at $baseUrl. " +
                    "The mirror may be down or blocking this network — try pelisplushd.bz."
            )
        }

        categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            return listOf(
                Genre("generos/accion", "Acción"),
                Genre("generos/animacion", "Animación"),
                Genre("generos/anime", "Anime"),
                Genre("generos/aventura", "Aventura"),
                Genre("generos/belica", "Bélica"),
                Genre("generos/ciencia-ficcion", "Ciencia ficción"),
                Genre("generos/comedia", "Comedia"),
                Genre("generos/crimen", "Crimen"),
                Genre("generos/documental", "Documental"),
                Genre("generos/drama", "Drama"),
                Genre("generos/familia", "Familia"),
                Genre("generos/fantasia", "Fantasía"),
                Genre("generos/guerra", "Guerra"),
                Genre("generos/historia", "Historia"),
                Genre("generos/misterio", "Misterio"),
                Genre("generos/musica", "Música"),
                Genre("generos/romance", "Romance"),
                Genre("generos/suspense", "Suspenso"),
                Genre("generos/terror", "Terror")
            )
        }

        if (page > 1) {
            return emptyList()
        }

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val document = getPageOrThrow("$baseUrl/search?s=$encodedQuery")
        return parseShows(document)
    }

    private fun parseShows(document: Document): List<AppAdapter.Item> {
        val elements = document.select("a.Posters-link, a[href*='/pelicula/'], a[href*='/serie/'], a[href*='/anime/']")
            .filter { el ->
                val href = el.attr("href")
                (href.contains("/pelicula/") || href.contains("/serie/") || href.contains("/anime/")) &&
                    !href.contains("/temporada/")
            }
            .distinctBy { it.attr("href") }

        return elements.mapNotNull { anchor ->
            val url = anchor.attr("abs:href").ifBlank { anchor.attr("href") }
            val img = anchor.selectFirst("img")
            val posterUrl = img?.attr("abs:src")?.ifBlank { null }
                ?: img?.attr("src")?.takeIf { it.startsWith("http") }
                ?: img?.attr("data-src")
                ?: ""
            val rawTitle = anchor.attr("data-title").ifBlank {
                img?.attr("alt").orEmpty()
            }.ifBlank {
                anchor.selectFirst("h2, .listing-content p, .title")?.text().orEmpty()
            }
            val title = cleanTitle(rawTitle)
            if (title.isBlank()) return@mapNotNull null

            when {
                url.contains("/pelicula/") -> Movie(
                    id = url.substringAfter("/pelicula/").substringBefore("/").substringBefore("?"),
                    title = title,
                    poster = posterUrl
                )
                url.contains("/serie/") -> TvShow(
                    id = url.substringAfter("/serie/").substringBefore("/").substringBefore("?"),
                    title = title,
                    poster = posterUrl
                )
                url.contains("/anime/") -> TvShow(
                    id = "anime/${url.substringAfter("/anime/").substringBefore("/").substringBefore("?")}",
                    title = title,
                    poster = posterUrl
                )
                else -> null
            }
        }
    }

    private fun cleanTitle(raw: String): String {
        return raw
            .replace(Regex("""(?i)^VER\s+"""), "")
            .replace(Regex("""(?i)\s*\(\)\s*"""), " ")
            .substringBefore(" Online")
            .substringBefore(" online")
            .substringBefore(" (")
            .trim()
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val url = if (page <= 1) "$baseUrl/peliculas" else "$baseUrl/peliculas?page=$page"
        return parseShows(getPageOrThrow(url)).filterIsInstance<Movie>()
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val url = if (page <= 1) "$baseUrl/series" else "$baseUrl/series?page=$page"
        return parseShows(getPageOrThrow(url)).filterIsInstance<TvShow>()
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val normalized = id
            .removePrefix("/")
            .replace(Regex("""^genero/"""), "generos/")
        val url = if (page <= 1) {
            "$baseUrl/$normalized"
        } else {
            "$baseUrl/$normalized?page=$page"
        }
        val document = getPageOrThrow(url)
        val shows = parseShows(document).filterIsInstance<Show>()
        val genreName = normalized.substringAfter("generos/").replaceFirstChar { it.uppercase() }
        return Genre(id = normalized, name = genreName, shows = shows)
    }

    private fun getAbsoluteUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        val cleanUrl = url.removePrefix("background-image: url(\"").removeSuffix("\");")
        return if (cleanUrl.startsWith("http")) {
            cleanUrl
        } else {
            "$baseUrl${cleanUrl.trimStart('/')}"
        }
    }

    private fun parseDetailTitle(document: Document): String {
        val h1 = document.selectFirst("h1")?.text().orEmpty()
        val og = document.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
        return cleanTitle(h1.ifBlank { og })
    }

    private fun parseGenres(document: Document): List<Genre> {
        return document.select("a[href*='/generos/']").mapNotNull { a ->
            val href = a.attr("href")
            val slug = href.substringAfter("/generos/").substringBefore("/").substringBefore("?")
            if (slug.isBlank()) return@mapNotNull null
            Genre(id = "generos/$slug", name = a.text().ifBlank { slug.replaceFirstChar { it.uppercase() } })
        }.distinctBy { it.id }
    }

    override suspend fun getMovie(id: String): Movie {
        val document = getPageOrThrow("$baseUrl/pelicula/$id")
        val posterUrl = document.selectFirst("meta[property=og:image]")?.attr("content")

        return Movie(
            id = id,
            title = parseDetailTitle(document),
            overview = document.selectFirst("meta[property=og:description]")?.attr("content")
                ?: document.selectFirst(".description p, .card-body p")?.text(),
            poster = getAbsoluteUrl(posterUrl),
            banner = getAbsoluteUrl(posterUrl),
            genres = parseGenres(document),
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        val url = if (id.startsWith("anime/")) "$baseUrl/$id" else "$baseUrl/serie/$id"
        val document = getPageOrThrow(url)
        val posterUrl = document.selectFirst("meta[property=og:image]")?.attr("content")

        val seasonNumbers = document.select("a[href*='/temporada/']")
            .mapNotNull { a ->
                TEMPORADA_CAPITULO_REGEX.find(a.attr("href"))?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
            .distinct()
            .sorted()
            .ifEmpty { listOf(1) }

        return TvShow(
            id = id,
            title = parseDetailTitle(document),
            overview = document.selectFirst("meta[property=og:description]")?.attr("content")
                ?: document.selectFirst(".description p, .card-body p")?.text(),
            poster = getAbsoluteUrl(posterUrl),
            banner = getAbsoluteUrl(posterUrl),
            genres = parseGenres(document),
            seasons = seasonNumbers.map { seasonNumber ->
                Season(
                    id = "$id/$seasonNumber",
                    number = seasonNumber,
                    title = "Temporada $seasonNumber"
                )
            }.sortedByDescending { it.number }
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val lastSlashIndex = seasonId.lastIndexOf('/')
        if (lastSlashIndex == -1) return emptyList()

        val showId = seasonId.substring(0, lastSlashIndex)
        val seasonNumber = seasonId.substring(lastSlashIndex + 1).toIntOrNull() ?: return emptyList()
        val url = if (showId.startsWith("anime/")) "$baseUrl/$showId" else "$baseUrl/serie/$showId"

        return try {
            val document = getPageOrThrow(url)
            document.select("a[href*='/temporada/$seasonNumber/capitulo/']")
                .mapNotNull { a ->
                    val href = a.attr("abs:href").ifBlank { a.attr("href") }
                    val match = TEMPORADA_CAPITULO_REGEX.find(href) ?: return@mapNotNull null
                    val episodeNumber = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
                    Episode(
                        id = "$seasonId/$episodeNumber",
                        number = episodeNumber,
                        title = a.text().trim().ifBlank { "Capítulo $episodeNumber" },
                    )
                }
                .distinctBy { it.number }
                .sortedBy { it.number }
        } catch (e: Exception) {
            Log.e(TAG, "getEpisodesBySeason falló: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val url = when (videoType) {
            is Video.Type.Movie -> "$baseUrl/pelicula/${videoType.id}"
            is Video.Type.Episode -> {
                val showId = videoType.tvShow.id
                val season = videoType.season.number
                val episode = videoType.number
                val basePath = if (showId.startsWith("anime/")) "$baseUrl/$showId" else "$baseUrl/serie/$showId"
                "$basePath/temporada/$season/capitulo/$episode"
            }
        }

        return try {
            val document = getPageOrThrow(url)
            val html = document.html()
            val videoMap = VIDEO_ASSIGN_REGEX.findAll(html).associate { match ->
                match.groupValues[1].toInt() to match.groupValues[2]
            }

            val tabLabels = document.select(".TbVideoNv li[data-id], ul.nav-tabs li[data-id]")
                .associate { li ->
                    val dataId = li.attr("data-id").toIntOrNull() ?: -1
                    val label = li.selectFirst("a")?.text()?.trim().orEmpty()
                        .ifBlank { li.text().trim() }
                        .ifBlank { "Server" }
                    dataId to label
                }

            val servers = mutableListOf<Video.Server>()
            videoMap.forEach { (index, src) ->
                if (src.isBlank() || !src.startsWith("http")) return@forEach
                val name = tabLabels[index] ?: "Server $index"
                servers += Video.Server(id = src, name = "$name [LAT]", src = src)
            }

            if (servers.isEmpty()) {
                document.select("iframe[src]").forEachIndexed { idx, iframe ->
                    val src = iframe.attr("abs:src").ifBlank { iframe.attr("src") }
                    if (src.startsWith("http")) {
                        servers += Video.Server(id = src, name = "Embed ${idx + 1} [LAT]", src = src)
                    }
                }
            }

            servers.distinctBy { it.src }
        } catch (e: Exception) {
            Log.e(TAG, "Fallo crítico en getServers: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.src, server)
    }

    override suspend fun getPeople(id: String, page: Int): People {
        throw Exception("Not yet implemented")
    }
}
