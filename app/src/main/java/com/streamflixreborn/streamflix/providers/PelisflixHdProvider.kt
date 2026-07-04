package com.streamflixreborn.streamflix.providers

import android.util.Base64
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.Show
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object PelisflixHdProvider : Provider {

    override val name = "PelisflixHD"
    override val baseUrl = "https://pelisflixhd.win"
    override val language = "es"
    override val logo = "https://s.pelisflixhd.win/cat/logo-mini.png"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
            chain.proceed(request)
        }
        .dns(DnsResolver.doh)
        .build()

    private interface PelisflixHdService {
        @GET
        suspend fun getPage(@Url url: String): Document
    }

    private val service = Retrofit.Builder()
        .baseUrl("$baseUrl/")
        .addConverterFactory(JsoupConverterFactory.create())
        .client(client)
        .build()
        .create(PelisflixHdService::class.java)

    override suspend fun getHome(): List<Category> {
        val document = service.getPage(baseUrl)

        return document.select("section.section-separator.container").mapNotNull { section ->
            val title = section.selectFirst("dt.section-title")?.text()?.trim() ?: return@mapNotNull null
            val shows = parseShowLinks(section.select("a[href*='/pelicula/'], a[href*='/serie/']"))
            if (shows.isEmpty()) null else Category(title, shows)
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            return getGenres()
        }

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = buildPagedUrl("$baseUrl/busqueda/$encodedQuery", page)
        val document = service.getPage(searchUrl)

        return parseShowLinks(document.select("a[href*='/pelicula/'], a[href*='/serie/']"))
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val document = service.getPage(buildPagedUrl("$baseUrl/peliculas", page))
        return parseShowLinks(document.select("a[href*='/pelicula/']")).filterIsInstance<Movie>()
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val document = service.getPage(buildPagedUrl("$baseUrl/series", page))
        return parseShowLinks(document.select("a[href*='/serie/']")).filterIsInstance<TvShow>()
    }

    override suspend fun getMovie(id: String): Movie {
        val document = service.getPage(normalizeUrl(id))
        val info = document.selectFirst("article.backdrop-info") ?: return Movie(id = id)

        return Movie(
            id = normalizeUrl(id),
            title = info.selectFirst("h1 .itemprop, h1 [itemprop=name], h1")
                ?.text()
                ?.substringAfter("Ver Película")
                ?.trim()
                .orEmpty(),
            overview = info.selectFirst(".description p")?.text()?.trim(),
            released = info.selectFirst("[itemprop=datePublished]")?.text()?.trim(),
            runtime = parseRuntimeMinutes(info.selectFirst("[itemprop=duration]")?.text()),
            quality = document.selectFirst(".card-hover-meta-quality")?.text()?.trim(),
            poster = normalizeUrl(info.selectFirst("figure.poster img")?.attr("src")),
            banner = extractBackdrop(document),
            genres = info.select(".info-list a[href*='/genero/']").map {
                Genre(id = normalizeUrl(it.attr("href")), name = it.text().trim().trimEnd(','))
            },
            cast = info.select(".info-list a[href*='/buscar/']").map {
                People(id = normalizeUrl(it.attr("href")), name = it.text().trim().trimEnd(','))
            },
            recommendations = extractRecommendations(document)
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        val document = service.getPage(normalizeUrl(id))
        val info = document.selectFirst("article.backdrop-info") ?: return TvShow(id = id)
        val seasons = linkedMapOf<String, Season>()

        document.select("a[href*='/temporada/']").forEach { link ->
            val href = normalizeUrl(link.attr("href"))
            val seasonNumber = Regex("""-([0-9]+)/?$""").find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("""Temporada\s+(\d+)""", RegexOption.IGNORE_CASE)
                    .find(link.text())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                ?: return@forEach

            seasons.putIfAbsent(
                href,
                Season(
                    id = href,
                    number = seasonNumber,
                    title = "Temporada $seasonNumber",
                    poster = normalizeUrl(info.selectFirst("figure.poster img")?.attr("src"))
                )
            )
        }

        return TvShow(
            id = normalizeUrl(id),
            title = info.selectFirst("h1 .itemprop, h1 [itemprop=name], h1")
                ?.text()
                ?.substringAfter("Ver Serie")
                ?.trim()
                .orEmpty(),
            overview = info.selectFirst(".description p")?.text()?.trim(),
            released = info.selectFirst("[itemprop=datePublished]")?.text()?.trim(),
            runtime = parseRuntimeMinutes(info.selectFirst("[itemprop=duration]")?.text()),
            quality = document.selectFirst(".card-hover-meta-quality")?.text()?.trim(),
            poster = normalizeUrl(info.selectFirst("figure.poster img")?.attr("src")),
            banner = extractBackdrop(document),
            genres = info.select(".info-list a[href*='/genero/']").map {
                Genre(id = normalizeUrl(it.attr("href")), name = it.text().trim().trimEnd(','))
            },
            cast = info.select(".info-list a[href*='/buscar/']").map {
                People(id = normalizeUrl(it.attr("href")), name = it.text().trim().trimEnd(','))
            },
            seasons = seasons.values.sortedBy { it.number },
            recommendations = extractRecommendations(document)
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val document = service.getPage(normalizeUrl(seasonId))

        return document.select("a[href*='/episodio/']").mapNotNull { link ->
            val spans = link.select("span")
            val code = spans.getOrNull(1)?.text()?.trim().orEmpty()
            val episodeNumber = Regex("""x(\d+)""", RegexOption.IGNORE_CASE).find(code)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: return@mapNotNull null

            Episode(
                id = normalizeUrl(link.attr("href")),
                number = episodeNumber,
                title = spans.firstOrNull()?.text()?.trim(),
                poster = normalizeUrl(link.selectFirst("img")?.attr("src"))
            )
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val genreUrl = buildPagedUrl(normalizeUrl(id), page)
        val document = service.getPage(genreUrl)
        val name = document.selectFirst("dt.section-title, h1, h2")?.text()?.trim()
            ?: normalizeLabel(id.substringAfterLast('/'))

        return Genre(
            id = normalizeUrl(id),
            name = name,
            shows = parseShowLinks(document.select("a[href*='/pelicula/'], a[href*='/serie/']")).filterIsInstance<Show>()
        )
    }

    override suspend fun getPeople(id: String, page: Int): People {
        val personUrl = buildPagedUrl(normalizeUrl(id), page)
        val document = service.getPage(personUrl)
        val fallbackName = URLDecoder.decode(normalizeUrl(id).substringAfterLast('/'), "UTF-8")
            .replace('+', ' ')
            .trim()

        return People(
            id = normalizeUrl(id),
            name = document.selectFirst("dt.section-title, h1, h2")?.text()?.trim()?.substringAfter(':')?.trim()
                ?: fallbackName,
            filmography = parseShowLinks(document.select("a[href*='/pelicula/'], a[href*='/serie/']")).filterIsInstance<Show>()
        )
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val document = service.getPage(normalizeUrl(id))

        return document.select("#player li[data-server]").mapIndexedNotNull { index, item ->
            val encoded = item.attr("data-server")
            if (encoded.isBlank()) {
                return@mapIndexedNotNull null
            }

            val decoded = runCatching { String(Base64.decode(encoded, Base64.DEFAULT)).trim() }.getOrNull()
                ?: return@mapIndexedNotNull null

            Video.Server(
                id = decoded,
                name = item.selectFirst("span")?.text()?.trim().orEmpty().ifBlank { "Opción ${index + 1}" },
                src = decoded
            )
        }.distinctBy { it.src }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.src, server)
    }

    private suspend fun getGenres(): List<Genre> {
        val document = runCatching { service.getPage(baseUrl) }.getOrNull() ?: return emptyList()

        return document.select(".showcase-sidebar-navigation a[href*='/genero/']").mapNotNull { link ->
            val href = normalizeUrl(link.attr("href"))
            val name = link.ownText().trim().ifBlank { link.text().trim() }
            if (href.isBlank() || name.isBlank()) null else Genre(id = href, name = name)
        }.distinctBy { it.id }
    }

    private fun parseShowLinks(links: List<Element>): List<AppAdapter.Item> {
        val items = linkedMapOf<String, AppAdapter.Item>()

        links.forEach { link ->
            val href = normalizeUrl(link.attr("href"))
            if (href.isBlank()) return@forEach

            val title = link.selectFirst(".item-detail p")?.text()?.trim()
                ?.ifBlank { null }
                ?: link.selectFirst("img")?.attr("alt")
                    ?.removePrefix("Poster ")
                    ?.trim()
                    ?.ifBlank { null }
                ?: return@forEach

            val item = when {
                href.contains("/pelicula/") -> Movie(
                    id = href,
                    title = title,
                    overview = link.selectFirst(".card-hover-info-overview p")?.text()?.trim()?.ifBlank { null },
                    released = link.selectFirst(".card-hover-year, .item-picture .year")?.text()?.trim()?.ifBlank { null },
                    runtime = parseRuntimeMinutes(link.selectFirst(".card-hover-info-meta > div")?.text()),
                    quality = link.selectFirst(".card-hover-meta-quality")?.text()?.trim()?.ifBlank { null },
                    poster = normalizeUrl(link.selectFirst("img.poster, img")?.attr("src")),
                    banner = extractBackdrop(link)
                )

                href.contains("/serie/") -> TvShow(
                    id = href,
                    title = title,
                    overview = link.selectFirst(".card-hover-info-overview p")?.text()?.trim()?.ifBlank { null },
                    released = link.selectFirst(".card-hover-year")?.text()?.trim()?.ifBlank { null },
                    runtime = parseRuntimeMinutes(link.selectFirst(".card-hover-info-meta > div")?.text()),
                    quality = link.selectFirst(".card-hover-meta-quality")?.text()?.trim()?.ifBlank { null },
                    poster = normalizeUrl(link.selectFirst("img.poster, img")?.attr("src")),
                    banner = extractBackdrop(link)
                )

                else -> null
            } ?: return@forEach

            items.putIfAbsent(href, item)
        }

        return items.values.toList()
    }

    private fun extractRecommendations(document: Document): List<Show> {
        val currentUrl = document.location()

        return document.select("section.section-separator a[href*='/pelicula/'], section.section-separator a[href*='/serie/']")
            .let(::parseShowLinks)
            .filterIsInstance<Show>()
            .filter { show ->
                when (show) {
                    is Movie -> show.id != currentUrl
                    is TvShow -> show.id != currentUrl
                }
            }
            .distinctBy { show ->
                when (show) {
                    is Movie -> show.id
                    is TvShow -> show.id
                }
            }
    }

    private fun extractBackdrop(element: Element): String? {
        val direct = element.selectFirst(".backdrop-image img, .card-hover-backdrop") ?: return null
        val dataBackdrop = direct.attr("data-backdrop")
        if (dataBackdrop.isNotBlank()) {
            return normalizeUrl(dataBackdrop)
        }

        val src = direct.attr("src")
        if (src.isNotBlank()) {
            return normalizeUrl(src)
        }

        return Regex("""url\(["']?([^"')]+)""").find(direct.attr("style"))?.groupValues?.getOrNull(1)?.let(::normalizeUrl)
    }

    private fun parseRuntimeMinutes(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val hours = Regex("""(\d+)h""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val minutes = Regex("""(\d+)min""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return if (hours == 0 && minutes == 0) null else hours * 60 + minutes
    }

    private fun buildPagedUrl(base: String, page: Int): String {
        return if (page <= 1) base.trimEnd('/') else "${base.trimEnd('/')}/page/$page"
    }

    private fun normalizeUrl(url: String?): String {
        val value = url?.trim().orEmpty()
        return when {
            value.isBlank() -> ""
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> baseUrl + value
            else -> "$baseUrl/${value.removePrefix("/")}"
        }
    }

    private fun normalizeLabel(slug: String): String {
        return slug.substringBefore('?')
            .replace('-', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
