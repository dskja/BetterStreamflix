package com.streamflixreborn.streamflix.providers

import MyCookieJar
import com.streamflixreborn.streamflix.StreamFlixApp
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
import com.streamflixreborn.streamflix.utils.TmdbUtils
import com.streamflixreborn.streamflix.utils.WebViewResolver
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

object EkinoTVProvider : Provider {

    override val name = "EkinoTV"
    override val baseUrl = "https://ekino-tv.pl"
    override val logo = "$baseUrl/views/img/logo.png"
    override val language = "pl"

    private val service = EkinoTVService.build()
    private var webViewResolver: WebViewResolver? = null

    private val seriesCatalogKeys = listOf("0-9") + ('A'..'Z').map(Char::toString)
    private val seriesCatalogPageCache = mutableMapOf<String, Int>()

    override suspend fun getHome(): List<Category> {
        val homeDocument = service.getHome()
        val seriesDocument = service.getDocument("$baseUrl/serie/")
        val latestMoviesDocument = service.getDocument("$baseUrl/movie/cat/+")

        val featuredMovies = parseSliderItems(homeDocument, isTvShow = false)
            .take(15)

        val featuredSeries = parseSliderItems(seriesDocument, isTvShow = true)
            .take(15)

        val latestMovies = parseListItems(latestMoviesDocument)
            .filterIsInstance<Movie>()
            .take(20)

        return buildList {
            if (featuredMovies.isNotEmpty()) add(Category(Category.FEATURED, featuredMovies))
            if (latestMovies.isNotEmpty()) add(Category("Najnowsze Filmy", latestMovies))
            if (featuredSeries.isNotEmpty()) add(Category("Seriale", featuredSeries))
        }
    }

    private fun parseSliderItems(document: Document, isTvShow: Boolean): List<AppAdapter.Item> {
        val slideBackgrounds = document.select("#owl-slider .slide_bb")
            .map { slide ->
                Regex("""background-image:\s*url\((['"]?)([^'")]+)\1\)""")
                    .find(slide.attr("style"))
                    ?.groupValues
                    ?.getOrNull(2)
                    ?.let(::normalizeUrl)
            }

        return document.select("#slider-nav #posters a")
            .mapIndexedNotNull { index, anchor ->
                parseSliderItem(anchor, isTvShow, slideBackgrounds.getOrNull(index))
            }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            val movieDocument = service.getDocument("$baseUrl/movie/cat/+")
            val seriesDocument = service.getDocument("$baseUrl/serie/")

            val movieGenres = movieDocument.select("ul.movieCategories li a").mapNotNull { element ->
                val id = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Genre(id = id, name = element.text().trim())
            }
            val seriesCatalog = seriesDocument.select("ul.serialsmenu li a").mapNotNull { element ->
                val id = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Genre(id = id, name = element.selectFirst("span.name")?.text()?.trim().orEmpty())
            }
            return (movieGenres + seriesCatalog).distinctBy { it.id }
        }

        val document = service.search(query)
        return parseListItems(document)
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val url = if (page <= 1) {
            "$baseUrl/movie/cat/+"
        } else {
            "$baseUrl/movie/cat/+strona[$page]+"
        }
        return parseListItems(service.getDocument(url)).filterIsInstance<Movie>()
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        if (page <= 1) {
            val document = service.getDocument("$baseUrl/serie/")
            return parseListItems(document)
                .filterIsInstance<TvShow>()
                .distinctBy { it.id }
        }

        val (catalogKey, catalogPage) = resolveSeriesCatalogPage(page)
        val suffix = if (catalogPage <= 1) catalogKey else "$catalogKey,strona=$catalogPage"
        val document = service.getDocument("$baseUrl/serie/catalog/$suffix")
        return parseListItems(document).filterIsInstance<TvShow>()
    }

    override suspend fun getMovie(id: String): Movie {
        val document = service.getDocument(toAbsoluteUrl(id))

        val title = document.selectFirst("h1.title")?.text()?.trim().orEmpty()
        val altTitle = document.select("h2.title").firstOrNull()?.text()?.trim().orEmpty()
        val tmdbMovie = TmdbUtils.getMovie(altTitle.ifBlank { title }, language = language)

        val poster = normalizeUrl(document.selectFirst("img.moviePoster")?.attr("src"))
        val overview = document.selectFirst("div.descriptionMovie")?.text()?.trim()
        val released = document.selectFirst("div.catBox div.cat")?.text()
            ?.let { Regex("""\b(19|20)\d{2}\b""").find(it)?.value }
        val rating = document.selectFirst("[itemprop=ratingValue]")?.text()?.toDoubleOrNull()
            ?: document.selectFirst("div.scorediv div.score span")?.text()?.toDoubleOrNull()
        val genres = document.select("div.catBox div.cat a[href*='/movie/cat/kategoria']")
            .map { Genre(id = it.attr("href"), name = it.text().trim()) }
        val cast = document.select("div.movieActors ul.actors li a[href*='aktor']").map { actor ->
            val actorName = actor.text().trim()
            val tmdbPerson = tmdbMovie?.cast?.find { it.name.equals(actorName, ignoreCase = true) }
            People(
                id = actor.attr("href"),
                name = actorName,
                image = tmdbPerson?.image
            )
        }
        val recommendations = document.select("div.relatedmovie > a[href*='/movie/show/']")
            .mapNotNull { anchor -> parseRecommendation(anchor) }

        return Movie(
            id = normalizeId(id),
            title = title,
            overview = tmdbMovie?.overview ?: overview,
            released = tmdbMovie?.released?.let { "${it.get(java.util.Calendar.YEAR)}" } ?: released,
            runtime = tmdbMovie?.runtime,
            trailer = tmdbMovie?.trailer,
            rating = tmdbMovie?.rating ?: rating,
            poster = tmdbMovie?.poster ?: poster,
            banner = tmdbMovie?.banner,
            imdbId = tmdbMovie?.imdbId,
            genres = tmdbMovie?.genres ?: genres,
            cast = cast,
            recommendations = recommendations
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        val document = service.getDocument(toAbsoluteUrl(id))

        val title = document.selectFirst("h1.title")?.text()?.trim().orEmpty()
        val altTitle = document.select("h2.title").firstOrNull()?.text()?.trim().orEmpty()
        val tmdbTvShow = TmdbUtils.getTvShow(altTitle.ifBlank { title }, language = language)
        val poster = normalizeUrl(document.selectFirst("img.moviePoster")?.attr("src"))
        val overview = document.selectFirst("div.descriptionMovie")?.text()?.trim()
        val released = Regex("""\b(19|20)\d{2}\b""").find(altTitle)?.value

        val seasons = parseSeasons(document, normalizeId(id), tmdbTvShow)

        return TvShow(
            id = normalizeId(id),
            title = title,
            overview = tmdbTvShow?.overview ?: overview,
            released = tmdbTvShow?.released?.let { "${it.get(java.util.Calendar.YEAR)}" } ?: released,
            runtime = tmdbTvShow?.runtime,
            trailer = tmdbTvShow?.trailer,
            rating = tmdbTvShow?.rating ?: document.selectFirst("div.scorediv #scoreSum")?.ownText()?.trim()?.toDoubleOrNull(),
            poster = tmdbTvShow?.poster ?: poster,
            banner = tmdbTvShow?.banner,
            imdbId = tmdbTvShow?.imdbId,
            seasons = seasons,
            genres = tmdbTvShow?.genres ?: emptyList(),
            cast = tmdbTvShow?.cast ?: emptyList()
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val showId = seasonId.substringBefore("|")
        val seasonNumber = seasonId.substringAfter("|").toIntOrNull() ?: return emptyList()

        val document = service.getDocument(toAbsoluteUrl(showId))
        val title = document.select("h2.title").firstOrNull()?.text()?.trim()
            ?: document.selectFirst("h1.title")?.text()?.trim()
            ?: ""
        val tmdbTvShow = TmdbUtils.getTvShow(title, language = language)
        val tmdbEpisodes = tmdbTvShow?.let {
            TmdbUtils.getEpisodesBySeason(it.id, seasonNumber, language = language)
        } ?: emptyList()

        return extractSeasonBlocks(document, normalizeId(showId))
            .firstOrNull { it.number == seasonNumber }
            ?.episodes
            ?.map { episode ->
                val tmdbEpisode = tmdbEpisodes.find { it.number == episode.number }
                episode.copy(
                    title = tmdbEpisode?.title ?: episode.title,
                    poster = tmdbEpisode?.poster,
                    overview = tmdbEpisode?.overview
                )
            }
            ?.sortedBy { it.number }
            .orEmpty()
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val normalizedId = normalizeId(id)
        val url = when {
            normalizedId.startsWith("/serie/catalog/") -> {
                val catalogKey = normalizedId.substringAfterLast("/").substringBefore(",strona=")
                val suffix = if (page <= 1) catalogKey else "$catalogKey,strona=$page"
                "$baseUrl/serie/catalog/$suffix"
            }
            normalizedId.startsWith("/movie/cat/") && page > 1 -> {
                if (normalizedId.endsWith("+")) {
                    "$baseUrl${normalizedId.removeSuffix("+")}strona[$page]+"
                } else {
                    "$baseUrl$normalizedId"
                }
            }
            else -> toAbsoluteUrl(normalizedId)
        }

        val document = service.getDocument(url)
        val shows = parseListItems(document).filterIsInstance<Show>()
        val name = document.selectFirst("div.profilHead font")?.text()?.trim()
            ?: document.selectFirst("title")?.text()?.substringAfter(":")?.substringBefore(" -")?.trim()
            ?: normalizedId

        return Genre(id = normalizedId, name = name, shows = shows)
    }

    override suspend fun getPeople(id: String, page: Int): People {
        if (page > 1) return People(id = normalizeId(id), name = decodeActorName(id))

        val normalizedId = normalizeId(id)
        val document = service.getDocument(toAbsoluteUrl(normalizedId))
        val filmography = parseListItems(document).filterIsInstance<Show>()

        return People(
            id = normalizedId,
            name = decodeActorName(normalizedId),
            filmography = filmography
        )
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val document = when (videoType) {
            is Video.Type.Movie -> service.getDocument(toAbsoluteUrl(id))
            is Video.Type.Episode -> service.getDocument(toAbsoluteUrl(id))
        }

        return document.select("ul.players li a[href^='#']")
            .filterNot { it.attr("href") == "#premium" }
            .mapNotNull { anchor ->
                val tabId = anchor.attr("href").removePrefix("#")
                val host = tabId.substringAfterLast("-")
                val token = tabId.removeSuffix("-$host")
                if (host.isBlank() || token.isBlank()) return@mapNotNull null

                val languageHint = anchor.selectFirst("i[title]")?.attr("title")?.trim()
                val serverName = buildString {
                    append(anchor.ownText().trim().ifBlank { host })
                    if (!languageHint.isNullOrBlank()) append(" ($languageHint)")
                }

                Video.Server(
                    id = "$baseUrl/watch/f/$host/$token",
                    name = serverName
                )
            }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val watchUrl = toAbsoluteUrl(server.id)
        val document = getWatchDocument(watchUrl)
        val externalUrl = extractWatchTarget(document, watchUrl)
            ?: throw Exception("Unable to resolve EkinoTV player URL")

        return Extractor.extract(normalizeUrl(externalUrl) ?: externalUrl, server)
    }

    private suspend fun getWatchDocument(url: String): Document {
        val initialDocument = runCatching { service.getDocument(url) }.getOrNull()
        if (initialDocument != null && !isChallengePage(initialDocument)) {
            return initialDocument
        }

        val html = getResolver().get(
            url,
            headers = mapOf("Referer" to baseUrl)
        )
        return Jsoup.parse(html, url)
    }

    private fun extractWatchTarget(document: Document, watchUrl: String): String? {
        val directCandidates = listOfNotNull(
            document.selectFirst("a.buttonprch[href]")?.attr("href"),
            document.selectFirst("iframe[src]")?.attr("src"),
            document.selectFirst("meta[http-equiv=refresh]")?.attr("content")
                ?.substringAfter("url=", "")
        )

        directCandidates.firstNotNullOfOrNull { candidate ->
            normalizeWatchTarget(candidate, watchUrl)
        }?.let { return it }

        val scriptMatches = document.select("script")
            .asSequence()
            .flatMap { script ->
                Regex("""https?://[^"'\\\s<]+""")
                    .findAll(script.data())
                    .map { it.value }
            }
            .mapNotNull { candidate -> normalizeWatchTarget(candidate, watchUrl) }
            .firstOrNull()

        if (scriptMatches != null) return scriptMatches

        return null
    }

    private fun normalizeWatchTarget(candidate: String?, watchUrl: String): String? {
        if (candidate.isNullOrBlank()) return null
        val normalized = when {
            candidate.startsWith("http://") || candidate.startsWith("https://") -> candidate
            candidate.startsWith("//") -> "https:$candidate"
            candidate.startsWith("/") -> "$baseUrl$candidate"
            else -> candidate
        }

        return normalized
            .takeIf { it.isNotBlank() }
            ?.takeIf { it != watchUrl }
            ?.takeIf { !it.contains("/watch/verify.php") }
    }

    private fun isChallengePage(document: Document): Boolean {
        val html = document.outerHtml()
        return html.contains("turnstile.render", ignoreCase = true) ||
            html.contains("Just a moment...", ignoreCase = true) ||
            html.contains("/watch/verify.php", ignoreCase = true) ||
            html.contains("cf-browser-verification", ignoreCase = true)
    }

    private fun getResolver(): WebViewResolver {
        return webViewResolver ?: WebViewResolver(StreamFlixApp.instance).also {
            webViewResolver = it
        }
    }

    private suspend fun resolveSeriesCatalogPage(targetPage: Int): Pair<String, Int> {
        var remaining = targetPage - 1

        for (catalogKey in seriesCatalogKeys) {
            val maxPages = seriesCatalogPageCache[catalogKey] ?: run {
                val resolvedPages = runCatching {
                    val document = service.getDocument("$baseUrl/serie/catalog/$catalogKey")
                    extractMaxPage(document, pagePattern = Regex("""strona=(\d+)"""))
                }.getOrDefault(1)
                seriesCatalogPageCache[catalogKey] = resolvedPages
                resolvedPages
            }

            if (remaining <= maxPages) {
                return catalogKey to remaining.coerceAtLeast(1)
            }
            remaining -= maxPages
        }

        return "Z" to 1
    }

    private fun parseSliderItem(anchor: Element, isTvShow: Boolean, banner: String? = null): AppAdapter.Item? {
        val href = anchor.attr("href").takeIf { it.isNotBlank() } ?: return null
        val title = anchor.attr("title")
            .removePrefix("Oglądaj:")
            .trim()
            .substringBefore(" / ")
            .ifBlank { anchor.text().trim() }
        val poster = normalizeUrl(anchor.selectFirst("img")?.attr("src"))

        return if (isTvShow) {
            TvShow(id = normalizeId(href), title = title, poster = poster, banner = banner ?: poster)
        } else {
            Movie(id = normalizeId(href), title = title, poster = poster, banner = banner ?: poster)
        }
    }

    private fun parseListItems(document: Document): List<AppAdapter.Item> {
        return document.select("div.movies-list-item").mapNotNull { item ->
            val href = item.selectFirst("div.cover-list a[href]")?.attr("href")
                ?: item.selectFirst("div.title > a[href]")?.attr("href")
                ?: return@mapNotNull null
            val normalizedId = normalizeId(href)
            val title = item.selectFirst("div.title > a:not(.blue)")?.text()?.trim()
                ?.removeSuffix("\u00a0")
                .orEmpty()
            val altTitle = item.selectFirst("div.title a.blue")?.text()?.trim().orEmpty()
            val displayTitle = title.ifBlank { altTitle }
            val poster = normalizeUrl(item.selectFirst("div.cover-list img")?.attr("src"))
            val overview = item.selectFirst("div.movieDesc")?.text()?.trim()
            val infoText = item.selectFirst("div.info-categories")?.text().orEmpty()
            val released = Regex("""\b(19|20)\d{2}\b""").find(infoText)?.value
            val rating = item.selectFirst("div.sum-vote div")?.text()?.trim()?.replace(",", ".")?.toDoubleOrNull()

            if (href.contains("/serie/show/")) {
                TvShow(
                    id = normalizedId,
                    title = displayTitle,
                    overview = overview,
                    released = released,
                    poster = poster
                )
            } else {
                Movie(
                    id = normalizedId,
                    title = displayTitle,
                    overview = overview,
                    released = released,
                    rating = rating,
                    poster = poster
                )
            }
        }.distinctBy {
            when (it) {
                is Movie -> "movie:${it.id}"
                is TvShow -> "tv:${it.id}"
            }
        }
    }

    private fun parseRecommendation(anchor: Element): Movie? {
        val href = anchor.attr("href").takeIf { it.isNotBlank() } ?: return null
        val poster = normalizeUrl(anchor.selectFirst("img.related")?.attr("src"))
        val title = anchor.selectFirst("div.title_related")?.text()
            ?.substringAfter(" ")
            ?.trim()
            .orEmpty()
        val released = anchor.selectFirst("div.year_related")?.text()
            ?.let { Regex("""\b(19|20)\d{2}\b""").find(it)?.value }
        return Movie(
            id = normalizeId(href),
            title = title,
            released = released,
            poster = poster
        )
    }

    private fun parseSeasons(document: Document, showId: String, tmdbTvShow: TvShow?): List<Season> {
        return extractSeasonBlocks(document, showId).map { season ->
            season.copy(
                poster = tmdbTvShow?.seasons?.find { it.number == season.number }?.poster
            )
        }.sortedBy { it.number }
    }

    private fun extractSeasonBlocks(document: Document, showId: String): List<Season> {
        val seasons = mutableListOf<Season>()
        var currentSeasonNumber = 0

        document.select("#list-series > p, #list-series > ul.list-series").forEach { element ->
            if (element.tagName() == "p") {
                currentSeasonNumber = Regex("""(\d+)""").find(element.text())?.groupValues?.get(1)?.toIntOrNull() ?: 0
            } else if (element.tagName() == "ul") {
                val episodes = element.select("li").mapNotNull { episodeItem ->
                    val number = episodeItem.selectFirst("div")?.text()?.trim()?.toIntOrNull() ?: return@mapNotNull null
                    val link = episodeItem.selectFirst("a[href]")?.attr("href") ?: return@mapNotNull null
                    Episode(
                        id = normalizeId(link),
                        number = number,
                        title = episodeItem.selectFirst("a")?.text()?.trim()
                    )
                }.sortedBy { it.number }
                seasons += Season(
                    id = "$showId|$currentSeasonNumber",
                    number = currentSeasonNumber,
                    title = if (currentSeasonNumber > 0) "Sezon $currentSeasonNumber" else null,
                    episodes = episodes
                )
            }
        }

        return seasons.sortedBy { it.number }
    }

    private fun normalizeId(id: String): String {
        if (id.startsWith(baseUrl)) {
            return id.removePrefix(baseUrl).ifBlank { "/" }
        }
        return id
    }

    private fun toAbsoluteUrl(id: String): String {
        return if (id.startsWith("http://") || id.startsWith("https://")) {
            id
        } else {
            normalizeUrl(id) ?: "$baseUrl$id"
        }
    }

    private fun normalizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$baseUrl$url"
            else -> "$baseUrl/$url"
        }
    }

    private fun decodeActorName(id: String): String {
        val raw = id.substringAfter("aktor[").substringBefore("]")
        return URLDecoder.decode(raw, Charsets.UTF_8.name()).ifBlank { id }
    }

    private fun extractMaxPage(document: Document, pagePattern: Regex): Int {
        return document.select("#pager a")
            .mapNotNull { pagePattern.find(it.attr("href"))?.groupValues?.getOrNull(1)?.toIntOrNull() }
            .maxOrNull()
            ?: 1
    }

    private interface EkinoTVService {
        @GET(".")
        suspend fun getHome(): Document

        @GET
        suspend fun getDocument(@Url url: String): Document

        @FormUrlEncoded
        @POST("search/qf")
        suspend fun search(@Field("q") query: String): Document

        companion object {
            fun build(): EkinoTVService {
                val client = OkHttpClient.Builder()
                    .cookieJar(MyCookieJar())
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .build()

                return Retrofit.Builder()
                    .baseUrl("$baseUrl/")
                    .client(client)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                    .create(EkinoTVService::class.java)
            }
        }
    }
}
