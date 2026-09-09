package com.dskja.betterstreamflix.providers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import com.dskja.betterstreamflix.utils.UserPreferences

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
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
import com.dskja.betterstreamflix.utils.DnsResolver
import kotlinx.coroutines.coroutineScope
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object LaCartoonsProvider : Provider, ProviderConfigUrl {

    override val name = "La Cartoons"
    override val defaultBaseUrl = "https://www.lacartoons.com"
    override val baseUrl: String
        get() = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL).ifBlank { defaultBaseUrl }
    override val changeUrlMutex = Mutex()

    override suspend fun onChangeUrl(forceRefresh: Boolean): String = changeUrlMutex.withLock {
        baseUrl
    }
    override val language = "es"
    override val logo: String get() = "https://images2.imgbox.com/fc/26/S7f7dn42_o.png"

    private val client: OkHttpClient = getOkHttpClient()
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(JsoupConverterFactory.create())
        .client(client)
        .build()
    private val service = retrofit.create(Service::class.java)

    private fun getOkHttpClient(): OkHttpClient {
        val appCache = Cache(File("cacheDir", "okhttpcache"), 10 * 1024 * 1024)
        val builder = OkHttpClient.Builder()
            .cache(appCache)
            .readTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    )
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "es-MX,es;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Referer", "$baseUrl/")
                    .header("Origin", baseUrl.trimEnd('/'))
                    .build()
                chain.proceed(request)
            }
        return builder.dns(DnsResolver.doh).build()
    }

    private interface Service {
        @GET
        suspend fun getPage(@Url url: String): Document
    }

    override suspend fun getHome(): List<Category> {
        return try {
            val shows = getTvShows(page = 1)
            listOf(Category(name = "Series", list = shows))
        } catch (_: Exception) { emptyList() }
    }

    private fun parseHomeShows(doc: Document): List<TvShow> {
        val list = mutableListOf<TvShow>()
        val containers = doc.select("div.conjuntos-series")
        for (container in containers) {
            val links = container.select("a[href^=/serie/], a[href*=/serie/]")
            for (a in links) {
            val href = a.attr("href").ifBlank { continue }
            val card = a.selectFirst("div.serie, div[class*=serie]") ?: continue
            val img = card.selectFirst("img")?.attr("src").orEmpty()
            val title = card.selectFirst("p.nombre-serie")?.text().orElse("")
            val absolutePoster = if (img.startsWith("http")) img else "$baseUrl$img"
            val absoluteId = if (href.startsWith("http")) href else "$baseUrl$href"
            list.add(
                TvShow(
                    id = absoluteId,
                    title = title,
                    poster = absolutePoster,
                    banner = absolutePoster,
                )
            )
            }
        }
        return list
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.id }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            // Return categories from botontes-categorias as Genres
            return try {
                val doc = service.getPage(baseUrl)
                val genres = doc.select("ul.botontes-categorias li form").mapNotNull { form ->
                    val value = form.selectFirst("input[name=Categoria_id]")?.attr("value") ?: return@mapNotNull null
                    val name = form.selectFirst("button[type=submit]")?.text()
                        ?: form.selectFirst("input[type=submit]")?.attr("value")
                        ?: return@mapNotNull null
                    Genre(id = value, name = name)
                }
                genres
            } catch (_: Exception) { emptyList() }
        }

        // Title search: site returns all results; return them all on page 1, none afterwards
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$baseUrl/?Titulo=$encoded"
            val doc = service.getPage(url)
            val all = parseHomeShows(doc)
            if (page > 1) emptyList() else all
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun getMovies(page: Int): List<Movie> = emptyList()

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return try {
            val url = if (page <= 1) baseUrl else "$baseUrl/?page=$page"
            val doc = service.getPage(url)
            parseHomeShows(doc)
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun getMovie(id: String): Movie {
        throw Exception("La Cartoons no ofrece películas, solo series")
    }

    override suspend fun getTvShow(id: String): TvShow {
        val url = if (id.startsWith("http")) id else "$baseUrl$id"
        val doc = service.getPage(url)

        val posterUrl = doc.selectFirst("div.contenedor-informacion-serie img")?.attr("src").orEmpty()
        val title = doc.selectFirst("h2.subtitulo-serie-seccion")?.ownText()?.trim()
            ?: doc.selectFirst("p.nombre-serie")?.text()?.trim()
            ?: doc.selectFirst("h1,h2,h3")?.text()?.trim().orEmpty()
        val infoSection = doc.selectFirst("div.informacion-serie-seccion")
        val overview = infoSection?.select("p")?.firstOrNull { it.text().startsWith("Reseña") }
            ?.selectFirst("span")?.text()
        val ratingText = infoSection?.selectFirst("span.valoracion1")?.ownText()?.trim()
        val rating = ratingText?.toDoubleOrNull()

        val seasons = mutableListOf<Season>()
        val temporadaHeaders = doc.select("section.contenedor-episodio-temporada h4.accordion")
        var seasonCounter = 0
        for (h4 in temporadaHeaders) {
            val text = h4.text().trim()
            val seasonNumber = Regex("Temporada\\s+(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: (++seasonCounter)
            val seasonId = buildSeasonId(url, seasonNumber)
            seasons.add(Season(id = seasonId, number = seasonNumber, title = "Temporada $seasonNumber"))
        }
        if (seasons.isEmpty()) {
            // Fallback: at least one season
            val seasonId = buildSeasonId(url, 1)
            seasons.add(Season(id = seasonId, number = 1, title = "Temporada 1"))
        }

        return TvShow(
            id = url,
            title = title,
            poster = if (posterUrl.startsWith("http")) posterUrl else "$baseUrl$posterUrl",
            overview = overview,
            rating = rating,
            seasons = seasons
        )
    }

    private fun buildSeasonId(seriesUrl: String, seasonNumber: Int): String {
        return "$seriesUrl?t=$seasonNumber"
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val doc = service.getPage(seasonId)
        val seasonNumber = seasonId.substringAfterLast("?t=").toIntOrNull()
        val episodes = mutableListOf<Episode>()
        val panels = doc.select("section.contenedor-episodio-temporada div.episodio-panel")
        val episodeLinks = if (seasonNumber != null && panels.size >= seasonNumber) {
            panels.get(seasonNumber - 1).select("ul.listas-de-episodion li a")
        } else if (seasonNumber != null) {
            doc.select("ul.listas-de-episodion li a[href*=?t=$seasonNumber]")
        } else {
            doc.select("ul.listas-de-episodion li a")
        }
        for (a in episodeLinks) {
            val href = a.attr("href").ifBlank { continue }
            val text = a.text().trim()
            val number = extractEpisodeNumber(text)
            val absoluteId = if (href.startsWith("http")) href else "$baseUrl$href"
            episodes.add(
                Episode(
                    id = absoluteId,
                    number = number,
                    title = text
                )
            )
        }
        return episodes
    }

    private fun extractEpisodeNumber(text: String): Int {
        // Expecting "Capitulo X- ..."
        val cap = text.substringAfter("Capitulo ", "").substringBefore("-").trim()
        return cap.toIntOrNull() ?: 0
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            // Resolve human-readable category name from homepage forms
            val home = service.getPage(baseUrl)
            val name = home.select("ul.botontes-categorias li form").firstOrNull { form ->
                form.selectFirst("input[name=Categoria_id]")?.attr("value") == id
            }?.let { form ->
                form.selectFirst("button[type=submit]")?.text()
                    ?: form.selectFirst("input[type=submit]")?.attr("value")
            } ?: id

            val url = if (page <= 1) "$baseUrl/?Categoria_id=$id" else "$baseUrl/?Categoria_id=$id&page=$page"
            val doc = service.getPage(url)
            val shows = parseHomeShows(doc)
            Genre(id = id, name = name, shows = shows)
        } catch (_: Exception) { Genre(id = id, name = id, shows = emptyList()) }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        throw Exception("Esta función no está disponible en La Cartoons")
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val url = if (id.startsWith("http")) id else "$baseUrl$id"
        val doc = service.getPage(url)
        val servers = mutableListOf<Video.Server>()

        fun addServer(raw: String?, label: String?) {
            val finalUrl = raw?.trim().orEmpty()
            if (finalUrl.isBlank()) return
            if (finalUrl.startsWith("javascript:", ignoreCase = true)) return
            val absolute = when {
                finalUrl.startsWith("//") -> "https:$finalUrl"
                finalUrl.startsWith("http") -> finalUrl
                else -> "$baseUrl/${finalUrl.trimStart('/')}"
            }
            val serverName = label?.takeIf { it.isNotBlank() } ?: runCatching {
                absolute.toHttpUrl().host
                    .replaceFirst("www.", "")
                    .substringBefore(".")
                    .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
            }.getOrDefault("Server")
            servers += Video.Server(id = absolute, name = serverName, src = absolute)
        }

        doc.select(
            "section.contenedor-video-recomendaciones iframe, " +
                "div.serie-video-informacion iframe, " +
                ".contenedor-video iframe, " +
                "iframe[src], iframe[data-src], .embed iframe, #player iframe, .player iframe"
        ).forEach {
            addServer(it.attr("src").ifBlank { it.attr("data-src") }, it.attr("title").takeIf { t -> t.isNotBlank() })
        }
        doc.select("[data-src*=http], li[data-url], a[data-player]").forEach {
            addServer(
                it.attr("data-src").ifBlank { it.attr("data-url") }.ifBlank { it.attr("data-player") },
                it.text()
            )
        }

        // Inline ok.ru / mail.ru / embed URLs in scripts
        if (servers.isEmpty()) {
            Regex(
                """(?:src|data-src)\s*=\s*["'](https?://(?:ok\.ru|www\.ok\.ru|videoapi\.my\.mail\.ru)[^"']+)["']""",
                RegexOption.IGNORE_CASE,
            ).findAll(doc.html()).forEachIndexed { idx, match ->
                addServer(match.groupValues[1], "Embed ${idx + 1}")
            }
        }

        return servers.distinctBy { it.src.ifBlank { it.id } }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.src.ifBlank { server.id }, server)
    }

    private fun String?.orElse(fallback: String): String = this ?: fallback
}


