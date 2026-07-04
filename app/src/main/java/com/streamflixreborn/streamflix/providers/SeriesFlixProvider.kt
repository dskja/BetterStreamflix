package com.streamflixreborn.streamflix.providers

import android.util.Base64
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url
import java.net.URLEncoder
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.TimeUnit

object SeriesFlixProvider : Provider {

    override val name = "SeriesFlix"
    override val baseUrl = "https://seriesflixhd.lol"
    override val logo = "https://s.seriesflixhd.lol/series/imgs/favicon-192.png"
    override val language = "es"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private interface SeriesFlixService {
        @Headers("User-Agent: $USER_AGENT")
        @GET
        suspend fun getPage(@Url url: String): Document
    }

    private val service: SeriesFlixService by lazy {
        val client = OkHttpClient.Builder()
            .dns(DnsResolver.doh)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", baseUrl)
                        .build()
                )
            }
            .build()

        Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .addConverterFactory(JsoupConverterFactory.create())
            .client(client)
            .build()
            .create(SeriesFlixService::class.java)
    }

    override suspend fun getHome(): List<Category> {
        val document = service.getPage(baseUrl)
        val categories = mutableListOf<Category>()

        document.select("section").forEach { section ->
            val title = section.selectFirst(".Top .Title, .Top h2, .Top h3")?.text()?.trim().orEmpty()
            if (title.isBlank()) return@forEach

            val items = when {
                title.contains("Top 10", ignoreCase = true) ->
                    section.select("ul.hometop10 li.mvnew").mapNotNull(::parseTopItem)
                title.contains("Ultimos Episodios", ignoreCase = true) ||
                    title.contains("Últimos Episodios", ignoreCase = true) ->
                    section.select("article.TPost.B").mapNotNull(::parseEpisodeCard)
                else ->
                    section.select("article.TPost.B").mapNotNull(::parseShowCard)
            }

            if (items.isNotEmpty()) {
                categories.add(Category(name = title, list = items))
            }
        }

        return categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            return getSearchGenres()
        }

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = if (page <= 1) {
            "$baseUrl/?s=$encodedQuery"
        } else {
            "$baseUrl/page/$page/?s=$encodedQuery"
        }

        val document = service.getPage(url)
        return document.select("article.TPost.B").mapNotNull { element ->
            parseShowCard(element) ?: parseEpisodeCard(element)
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> = emptyList()

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val document = service.getPage("$baseUrl/peliculas-online/series-online/page/$page")
        return document.select("article.TPost.B").mapNotNull(::parseShowCard)
    }

    override suspend fun getMovie(id: String): Movie {
        throw UnsupportedOperationException("SeriesFlix is a series-only provider")
    }

    override suspend fun getTvShow(id: String): TvShow {
        val document = service.getPage(id)
        val container = document.selectFirst("article.TPost.A") ?: document

        val title = container.selectFirst("h1.Title")?.text()
            ?.removePrefix("Serie ")
            ?.trim()
            .orEmpty()
        val info = container.selectFirst(".Info")
        val overview = container.selectFirst(".Description > p:not([class])")?.text()?.trim()
        val banner = normalizeImageUrl(container.selectFirst(".Image img")?.attr("src"))
        val genres = container.select(".Description .Genre a[href]").map { genreAnchor ->
            Genre(
                id = genreAnchor.attr("href").trim(),
                name = genreAnchor.text().trim().trimEnd(',')
            )
        }
        val directors = container.select(".Description .Director a").map { director ->
            People(
                id = director.text().trim(),
                name = director.text().trim()
            )
        }
        val cast = container.select(".Description .Cast a").map { actor ->
            People(
                id = actor.attr("href").trim(),
                name = actor.text().trim().trimEnd(',')
            )
        }
        val seasons = document.select("a[href*='/temporada/']").mapNotNull { seasonAnchor ->
            val href = seasonAnchor.attr("href").trim()
            val number = Regex("""Temporada\s+(\d+)""", RegexOption.IGNORE_CASE)
                .find(seasonAnchor.text())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: Regex("""-(\d+)/?$""").find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: return@mapNotNull null

            Season(
                id = href,
                number = number,
                title = "Temporada $number",
                poster = banner
            )
        }.distinctBy { it.id }.sortedBy { it.number }

        return TvShow(
            id = id,
            title = title,
            overview = overview,
            released = info?.selectFirst(".Date")?.text()?.trim(),
            runtime = parseRuntimeMinutes(info?.selectFirst(".Time")?.text()),
            poster = banner,
            banner = banner,
            genres = genres,
            directors = directors,
            cast = cast,
            seasons = seasons
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val document = service.getPage(seasonId)

        return document.select("section.SeasonBx .TPTblCn tr").mapNotNull { row ->
            val href = row.selectFirst(".MvTbTtl a[href]")?.attr("href")?.trim() ?: return@mapNotNull null
            val number = row.selectFirst(".Num")?.text()?.trim()?.toIntOrNull() ?: return@mapNotNull null
            Episode(
                id = href,
                number = number,
                title = row.selectFirst(".MvTbTtl a[href]")?.text()?.trim(),
                poster = normalizeImageUrl(row.selectFirst(".MvTbImg img")?.attr("data-src"))
                    ?: normalizeImageUrl(row.selectFirst(".MvTbImg img")?.attr("src"))
            )
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val pageUrl = when {
            id.startsWith("http") && page <= 1 -> id
            id.startsWith("http") -> "${id.trimEnd('/')}/page/$page/"
            page <= 1 -> "$baseUrl/genero/${id.trim('/')}/"
            else -> "$baseUrl/genero/${id.trim('/')}/page/$page/"
        }

        val document = service.getPage(pageUrl)
        val shows = document.select("article.TPost.B").mapNotNull(::parseShowCard)
        val name = document.selectFirst(".Top .Title, h1.Title, h1")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: id.substringAfterLast('/').trim('/').replace('-', ' ')
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

        return Genre(id = id, name = name, shows = shows)
    }

    override suspend fun getPeople(id: String, page: Int): People {
        val url = if (page <= 1) {
            id
        } else {
            "${id.trimEnd('/')}/page/$page/"
        }
        val document = service.getPage(url)
        val name = URLDecoder.decode(id.substringAfterLast("/"), "UTF-8").replace('+', ' ')

        return People(
            id = id,
            name = name,
            filmography = document.select("article.TPost.B").mapNotNull(::parseShowCard)
        )
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val document = service.getPage(id)
        val servers = mutableListOf<Video.Server>()

        document.select(".optns-bx .drpdn").forEach { group ->
            val languageLabel = normalizeLanguageLabel(
                group.selectFirst("button.bstd")?.text().orEmpty()
            )

            group.select(".Button.sgty[data-url]").forEachIndexed { index, serverButton ->
                val decodedUrl = decodeBase64Url(serverButton.attr("data-url")) ?: return@forEachIndexed
                val playableUrl = unwrapPlayableUrl(decodedUrl)
                val hostLabel = extractHostLabel(playableUrl)

                servers.add(
                    Video.Server(
                        id = playableUrl,
                        name = buildServerLabel(languageLabel, hostLabel, index + 1),
                        src = playableUrl
                    )
                )
            }
        }

        return servers.distinctBy { it.id }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.src, server)
    }

    private suspend fun getSearchGenres(): List<Genre> {
        val document = service.getPage(baseUrl)
        val menuGenres = document.select("li.menu-item-has-children")
            .firstOrNull { it.ownText().contains("GÉNEROS", ignoreCase = true) || it.text().contains("GÉNEROS", ignoreCase = true) }
            ?.select("a[href*='/genero/']")
            .orEmpty()

        return menuGenres.mapNotNull { anchor ->
            val href = anchor.attr("href").trim()
            val name = anchor.text().trim()
            if (href.isBlank() || name.isBlank()) return@mapNotNull null
            Genre(id = href, name = name)
        }.distinctBy { it.id }
    }

    private fun parseTopItem(element: Element): TvShow? {
        val anchor = element.selectFirst("a[href*='/serie/']") ?: return null
        val title = element.selectFirst("h2.Title")?.text()?.trim() ?: return null
        return TvShow(
            id = anchor.attr("href").trim(),
            title = title,
            poster = normalizeImageUrl(element.selectFirst("img")?.attr("data-src"))
                ?: normalizeImageUrl(element.selectFirst("img")?.attr("src"))
        )
    }

    private fun parseShowCard(element: Element): TvShow? {
        val anchor = element.selectFirst("a[href*='/serie/']") ?: return null
        val title = element.selectFirst("h2.Title")?.text()?.trim() ?: return null
        val overview = element.selectFirst(".Description")?.text()?.trim()
        return TvShow(
            id = anchor.attr("href").trim(),
            title = title,
            overview = overview,
            released = element.selectFirst(".Year, .Date")?.text()?.trim()
                ?: element.selectFirst("span.Date")?.text()?.trim(),
            runtime = parseRuntimeMinutes(element.selectFirst(".Time")?.text()),
            poster = normalizeImageUrl(element.selectFirst("img")?.attr("data-src"))
                ?: normalizeImageUrl(element.selectFirst("img")?.attr("src"))
        )
    }

    private fun parseEpisodeCard(element: Element): Episode? {
        val anchor = element.selectFirst("a[href*='/episodio/']") ?: return null
        val title = element.selectFirst("h2.Title")?.text()?.trim() ?: return null
        val subtitle = element.selectFirst("h2.Title")?.attr("data-subtitle").orEmpty()
        val number = Regex("""(\d+)x(\d+)""").find(subtitle)?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
        return Episode(
            id = anchor.attr("href").trim(),
            number = number,
            title = title,
            poster = normalizeImageUrl(element.selectFirst("img")?.attr("data-src"))
                ?: normalizeImageUrl(element.selectFirst("img")?.attr("src"))
        )
    }

    private fun decodeBase64Url(value: String): String? {
        return runCatching {
            String(Base64.decode(value.trim(), Base64.DEFAULT)).trim()
        }.getOrNull()?.takeIf { it.startsWith("http") }
    }

    private fun unwrapPlayableUrl(value: String): String {
        val httpUrl = value.toHttpUrlOrNull() ?: return value
        httpUrl.queryParameter("url")?.let { encodedInner ->
            return URLDecoder.decode(encodedInner, "UTF-8")
        }
        return value
    }

    private fun extractHostLabel(value: String): String {
        return runCatching {
            val host = value.toHttpUrlOrNull()?.host.orEmpty()
                .removePrefix("www.")
                .substringBefore(".")
            when {
                host.contains("voe", ignoreCase = true) -> "Voe"
                host.contains("nupload", ignoreCase = true) -> "Nupload"
                host.contains("waaw", ignoreCase = true) -> "Waaw"
                host.contains("upstream", ignoreCase = true) -> "Upstream"
                host.contains("streamtape", ignoreCase = true) -> "Streamtape"
                host.isBlank() -> "Server"
                else -> host.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
        }.getOrDefault("Server")
    }

    private fun normalizeLanguageLabel(raw: String): String {
        val upper = raw.uppercase(Locale.ROOT)
        return when {
            "LATINO" in upper -> "Latino"
            "CASTELLANO" in upper || "ESPANOL" in upper || "ESPAÑOL" in upper -> "Castellano"
            "SUBTITULADO" in upper || "SUBTITLED" in upper -> "Subtitulado"
            raw.isBlank() -> "Server"
            else -> raw.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private fun buildServerLabel(languageLabel: String, hostLabel: String, optionNumber: Int): String {
        return listOf(languageLabel, "$hostLabel $optionNumber")
            .filter { it.isNotBlank() }
            .joinToString(" - ")
    }

    private fun normalizeImageUrl(url: String?): String? {
        val clean = url?.trim().orEmpty()
        if (clean.isBlank()) return null
        return when {
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("/") -> "$baseUrl$clean"
            else -> clean
        }
    }

    private fun parseRuntimeMinutes(value: String?): Int? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null

        val hourMatch = Regex("""(\d+)\s*h""", RegexOption.IGNORE_CASE).find(raw)
        val minuteMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(raw)
        val hours = hourMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val minutes = minuteMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: raw.filter { it.isDigit() }.toIntOrNull()

        return when {
            hours > 0 -> hours * 60 + (minuteMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0)
            minutes != null -> minutes
            else -> null
        }
    }
}
