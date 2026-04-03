package com.streamflixreborn.streamflix.providers

import MyCookieJar
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
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

object ZeriunProvider : Provider {

    override val name = "Zeriun"
    override val baseUrl = "https://zeriun.cc"
    override val logo = "$baseUrl/assets/img/logo.png"
    override val language = "pl"

    private val client = OkHttpClient.Builder()
        .cookieJar(MyCookieJar())
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    private val service = ZeriunService.build(client)

    override suspend fun getHome(): List<Category> {
        val document = service.getDocument(baseUrl)

        val featured = document.select("#h-swiper .swiper-slide")
            .mapNotNull(::parseFeaturedItem)
            .distinctBy(::itemKey)
            .take(20)

        val updated = document.select(".recently-updated").firstOrNull()
            ?.select("ul.list > li")
            ?.mapNotNull(::parseCompactItem)
            ?.filterIsInstance<TvShow>()
            ?.distinctBy { it.id }
            .orEmpty()
            .take(20)

        val movies = document.select(".recently-updated").getOrNull(1)
            ?.select("ul.list > li")
            ?.mapNotNull(::parseCompactItem)
            ?.filterIsInstance<Movie>()
            ?.distinctBy { it.id }
            .orEmpty()
            .take(20)

        return buildList {
            if (featured.isNotEmpty()) add(Category(Category.FEATURED, featured))
            if (updated.isNotEmpty()) add(Category("Ostatnio zaktualizowane", updated))
            if (movies.isNotEmpty()) add(Category("Ostatnio dodane filmy", movies))
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            return buildGenreItems()
        }

        val document = service.getDocument("$baseUrl/szukaj?query=${encodeQuery(query)}")
        return document.select("#search-page #list > li")
            .mapNotNull(::parseCompactItem)
            .distinctBy(::itemKey)
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return service.getDocument("$baseUrl/filmy?page=$page")
            .select("#series-list-page #list > li")
            .mapNotNull(::parseCatalogItem)
            .filterIsInstance<Movie>()
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return service.getDocument("$baseUrl/seriale?page=$page")
            .select("#series-list-page #list > li")
            .mapNotNull(::parseCatalogItem)
            .filterIsInstance<TvShow>()
    }

    override suspend fun getMovie(id: String): Movie {
        val document = service.getDocument(toAbsoluteUrl(id))
        return parseMovieDetails(document, toAbsoluteUrl(id))
    }

    override suspend fun getTvShow(id: String): TvShow {
        val document = service.getDocument(toAbsoluteUrl(id))
        return parseTvShowDetails(document, toAbsoluteUrl(id))
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val showUrl = seasonId.substringBefore("|")
        val seasonNumber = seasonId.substringAfter("|").toIntOrNull() ?: return emptyList()
        val document = service.getDocument(toAbsoluteUrl(showUrl))
        val poster = normalizeUrl(document.selectFirst("#series-page .poster img")?.attr("src"))
        return document.select("#series-page .seasons-block .season")
            .firstOrNull { parseSeasonNumber(it.selectFirst(".title-text")?.text()) == seasonNumber }
            ?.let { parseSeasonEpisodes(it, poster) }
            .orEmpty()
            .sortedBy { it.number }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val url = when {
            id.startsWith("http") -> id
            id.startsWith("/") -> "$baseUrl$id"
            else -> "$baseUrl/$id"
        }
        val document = service.getDocument(withPage(url, page))
        val shows = document.select("#series-list-page #list > li")
            .mapNotNull(::parseCatalogItem)
            .filterIsInstance<Show>()
        val name = document.selectFirst("title")?.text()
            ?.substringBefore(" - Zeriun")
            ?.substringAfter("Katalog ")
            ?.replaceFirstChar { it.uppercase() }
            ?: "Zeriun"

        return Genre(id = id, name = name, shows = shows)
    }

    override suspend fun getPeople(id: String, page: Int): People {
        return People(id = id, name = id, filmography = emptyList())
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val pageUrl = when (videoType) {
            is Video.Type.Movie -> toAbsoluteUrl(id)
            is Video.Type.Episode -> toAbsoluteUrl(id)
        }
        val document = service.getDocument(pageUrl)
        val mode = if (document.selectFirst("#movie-page") != null) "movie" else "series"

        return document.select(".video-list table").flatMap { table ->
            val typeKey = table.attr("data-key").ifBlank { "default" }
            table.select("tbody tr").mapNotNull { row ->
                val button = row.selectFirst(".watch-btn[data-id]") ?: return@mapNotNull null
                val host = row.selectFirst("td")?.text()
                    ?.replace("premium", "", ignoreCase = true)
                    ?.trim()
                    .orEmpty()
                if (host.isBlank()) return@mapNotNull null

                Video.Server(
                    id = encodeServerId(mode, pageUrl, button.attr("data-id")),
                    name = "$host [$typeKey]"
                )
            }
        }.distinctBy { it.id }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val serverData = parseServerId(server.id)
        val document = service.getDocument(serverData.pageUrl)
        val csrf = extractCsrf(document) ?: throw Exception("Missing Zeriun csrf token")
        val endpoint = if (serverData.mode == "movie") {
            "$baseUrl/api/movie/get-embed"
        } else {
            "$baseUrl/api/series/get-embed"
        }

        val body = FormBody.Builder()
            .add("id", serverData.watchId)
            .build()

        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .header("x-csrf-token", csrf)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", serverData.pageUrl)
            .header("Origin", baseUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        val json = client.newCall(request).execute().use { response ->
            response.body?.string()
        } ?: throw Exception("Empty Zeriun embed response")

        val root = JSONObject(json)
        val url = root.optJSONObject("data")
            ?.optString("url")
            ?.takeIf { it.isNotBlank() }
            ?: throw Exception("Unable to resolve Zeriun embed")

        return Extractor.extract(url, server)
    }

    private suspend fun buildGenreItems(): List<Genre> {
        val movieDocument = service.getDocument("$baseUrl/filmy")
        val tvDocument = service.getDocument("$baseUrl/seriale")
        val movieGenres = movieDocument.select(".genres li[data-num]").mapNotNull { element ->
            val num = element.attr("data-num").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Genre(id = "/filmy?gen=$num", name = element.text().trim().replaceFirstChar { it.uppercase() })
        }
        val tvGenres = tvDocument.select(".genres li[data-num]").mapNotNull { element ->
            val num = element.attr("data-num").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Genre(id = "/seriale?gen=$num", name = element.text().trim().replaceFirstChar { it.uppercase() })
        }

        return listOf(
            Genre(id = "/filmy", name = "Filmy"),
            Genre(id = "/seriale", name = "Seriale")
        ) + (movieGenres + tvGenres).distinctBy { it.id }
    }

    private fun parseFeaturedItem(element: Element): AppAdapter.Item? {
        val link = element.selectFirst("a[href]")?.attr("href") ?: return null
        val title = element.selectFirst(".title")?.text()?.trim().orEmpty()
        val poster = normalizeUrl(element.selectFirst("img")?.attr("src"))
        return toItem(link, title, null, null, null, poster, poster)
    }

    private fun parseCompactItem(element: Element): AppAdapter.Item? {
        val link = element.selectFirst("a[href]")?.attr("href") ?: return null
        val title = element.selectFirst(".title")?.text()?.trim().orEmpty()
        val originalTitle = element.selectFirst(".title-original")?.text()?.trim()
        val poster = normalizeUrl(element.selectFirst("img")?.attr("src"))
        return toItem(link, title, originalTitle, null, null, poster, poster)
    }

    private fun parseCatalogItem(element: Element): AppAdapter.Item? {
        val link = element.selectFirst("a.poster[href]")?.attr("href")
            ?: element.selectFirst(".info a[href]")?.attr("href")
            ?: return null
        val title = element.selectFirst(".title")?.text()?.trim().orEmpty()
        val originalTitle = element.selectFirst(".title-original")?.text()?.trim()
        val overview = element.selectFirst(".desc")?.text()?.trim()
        val year = element.selectFirst(".date span")?.text()?.trim()
        val rating = element.selectFirst(".rate span")?.text()?.replace(",", ".")?.toDoubleOrNull()
        val poster = normalizeUrl(element.selectFirst("img")?.attr("src"))
        return toItem(link, title, originalTitle, overview, year, poster, poster, rating)
    }

    private fun toItem(
        url: String,
        title: String,
        originalTitle: String?,
        overview: String?,
        released: String?,
        poster: String?,
        banner: String?,
        rating: Double? = null
    ): AppAdapter.Item? {
        val fullUrl = normalizeUrl(url) ?: return null
        val resolvedTitle = title.ifBlank { originalTitle.orEmpty() }
        if (resolvedTitle.isBlank()) return null

        return if (fullUrl.contains("/serial/")) {
            TvShow(
                id = fullUrl,
                title = resolvedTitle,
                overview = overview,
                released = released,
                rating = rating,
                poster = poster,
                banner = banner
            )
        } else {
            Movie(
                id = fullUrl,
                title = resolvedTitle,
                overview = overview,
                released = released,
                rating = rating,
                poster = poster,
                banner = banner
            )
        }
    }

    private fun parseMovieDetails(document: Document, id: String): Movie {
        val title = document.selectFirst("#movie-page .info h2.title")?.text()?.trim().orEmpty()
        val originalTitle = document.selectFirst("#movie-page .info h3.original-title")?.text()?.trim()
        val overview = document.selectFirst("#movie-page .right-side .desc")?.text()?.trim()
        val poster = normalizeUrl(document.selectFirst("#movie-page .right-side .poster img")?.attr("src"))
        val released = normalizeReleasedDate(document.selectFirst("#movie-page .date span")?.text()?.trim())
        val runtime = document.selectFirst("#movie-page .duration span")?.text()
            ?.substringBefore("min")
            ?.trim()
            ?.toIntOrNull()
        val rating = document.selectFirst("#movie-page .rate span")?.text()?.replace(",", ".")?.toDoubleOrNull()
        val genres = document.select("#movie-page .genres a").mapNotNull { genre ->
            val href = genre.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Genre(id = href.removePrefix(baseUrl), name = genre.text().trim())
        }

        return Movie(
            id = id,
            title = title.ifBlank { originalTitle.orEmpty() },
            overview = overview,
            released = released,
            runtime = runtime,
            rating = rating,
            poster = poster,
            banner = poster,
            genres = genres
        )
    }

    private fun parseTvShowDetails(document: Document, id: String): TvShow {
        val title = document.selectFirst("#series-page .info h2.title")?.text()?.trim().orEmpty()
        val originalTitle = document.selectFirst("#series-page .info h3.title-original")?.text()?.trim()
        val overview = document.selectFirst("#series-page .info .desc")?.text()?.trim()
        val poster = normalizeUrl(document.selectFirst("#series-page .poster img")?.attr("src"))
        val released = normalizeReleasedDate(document.selectFirst("#series-page .date span")?.text()?.trim())
        val runtime = document.selectFirst("#series-page .duration span")?.text()
            ?.substringBefore("min")
            ?.trim()
            ?.toIntOrNull()
        val rating = document.selectFirst("#series-page .rate span")?.text()?.replace(",", ".")?.toDoubleOrNull()
        val genres = document.select("#series-page .genres a").mapNotNull { genre ->
            val href = genre.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Genre(id = href.removePrefix(baseUrl), name = genre.text().trim())
        }
        val seasons = document.select("#series-page .seasons-block .season")
            .mapNotNull { seasonElement ->
                val seasonNumber = parseSeasonNumber(seasonElement.selectFirst(".title-text")?.text())
                    ?: return@mapNotNull null
                Season(
                    id = "$id|$seasonNumber",
                    number = seasonNumber,
                    title = "Sezon $seasonNumber",
                    poster = poster,
                    episodes = parseSeasonEpisodes(seasonElement, poster).sortedBy { it.number }
                )
            }
            .sortedBy { it.number }

        return TvShow(
            id = id,
            title = title.ifBlank { originalTitle.orEmpty() },
            overview = overview,
            released = released,
            runtime = runtime,
            rating = rating,
            poster = poster,
            banner = poster,
            genres = genres,
            seasons = seasons
        )
    }

    private fun parseSeasonEpisodes(seasonElement: Element, poster: String?): List<Episode> {
        return seasonElement.select("ul > li")
            .mapNotNull { episodeElement ->
                val href = episodeElement.selectFirst("a[href]")?.attr("href") ?: return@mapNotNull null
                val heading = episodeElement.selectFirst("h4.title")?.text()?.trim().orEmpty()
                val episodeNumber = Regex("""e(\d{1,3})""", RegexOption.IGNORE_CASE)
                    .find(heading)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: return@mapNotNull null
                val title = heading.replace(Regex("""^s\d{1,3}e\d{1,3}\s*""", RegexOption.IGNORE_CASE), "").trim()
                val released = normalizeReleasedDate(episodeElement.selectFirst(".date")?.text()?.trim())
                val image = normalizeUrl(episodeElement.selectFirst("img")?.attr("src")) ?: poster

                Episode(
                    id = normalizeUrl(href) ?: return@mapNotNull null,
                    number = episodeNumber,
                    title = title.ifBlank { "Odcinek $episodeNumber" },
                    released = released,
                    poster = image
                )
            }
    }

    private fun extractCsrf(document: Document): String? {
        return Regex("""var _csrf = '([^']+)'""")
            .find(document.outerHtml())
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun normalizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$baseUrl$url"
            else -> "$baseUrl/$url"
        }
    }

    private fun toAbsoluteUrl(url: String): String = normalizeUrl(url)
        ?: throw IllegalArgumentException("Invalid Zeriun url: $url")

    private fun parseSeasonNumber(text: String?): Int? {
        return Regex("""(\d{1,3})""").find(text.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun normalizeReleasedDate(value: String?): String? {
        if (value.isNullOrBlank()) return null

        Regex("""^(\d{2})\.(\d{2})\.(\d{4})$""").find(value)?.let { match ->
            val day = match.groupValues[1]
            val month = match.groupValues[2]
            val year = match.groupValues[3]
            return "$year-$month-$day"
        }

        return value
    }

    private fun encodeServerId(mode: String, pageUrl: String, watchId: String): String {
        return listOf(mode, pageUrl, watchId).joinToString("|")
    }

    private fun parseServerId(id: String): ServerData {
        val parts = id.split("|")
        return ServerData(
            mode = parts.getOrNull(0).orEmpty(),
            pageUrl = parts.getOrNull(1).orEmpty(),
            watchId = parts.getOrNull(2).orEmpty()
        )
    }

    private fun itemKey(item: AppAdapter.Item): String {
        return when (item) {
            is Movie -> "movie:${item.id}"
            is TvShow -> "tv:${item.id}"
            is Genre -> "genre:${item.id}"
            else -> item.toString()
        }
    }

    private fun encodeQuery(query: String): String {
        return java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
    }

    private fun withPage(url: String, page: Int): String {
        if (page <= 1 || url.contains("page=")) return url
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}page=$page"
    }

    private data class ServerData(
        val mode: String,
        val pageUrl: String,
        val watchId: String
    )

    private interface ZeriunService {
        @GET
        suspend fun getDocument(@Url url: String): Document

        companion object {
            fun build(client: OkHttpClient): ZeriunService {
                return Retrofit.Builder()
                    .baseUrl("$baseUrl/")
                    .client(client)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                    .create(ZeriunService::class.java)
            }
        }
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
}
