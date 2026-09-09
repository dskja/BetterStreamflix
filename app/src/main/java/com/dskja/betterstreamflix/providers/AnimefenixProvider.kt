package com.dskja.betterstreamflix.providers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import com.dskja.betterstreamflix.utils.UserPreferences

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.extractors.Extractor
import com.dskja.betterstreamflix.models.*
import com.dskja.betterstreamflix.utils.DnsResolver
import com.dskja.betterstreamflix.utils.NetworkClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.util.concurrent.TimeUnit
import android.util.Log

object AnimefenixProvider : Provider, ProviderConfigUrl {

    override val name = "Animefenix"
    // animefenix2.tv is dead from many networks; animefenix.live is the current working mirror.
    override val defaultBaseUrl = "https://animefenix.live"
    override val baseUrl: String
        get() = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL).ifBlank { defaultBaseUrl }
    override val changeUrlMutex = Mutex()

    override suspend fun onChangeUrl(forceRefresh: Boolean): String = changeUrlMutex.withLock {
        baseUrl
    }
    override val language = "es"
    override val logo = "$defaultBaseUrl/images/animefenix-logo.png"

    private const val TAG = "AnimefenixProvider"

    private val service by lazy {
        Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .addConverterFactory(JsoupConverterFactory.create())
            .client(buildClient())
            .build()
            .create(AnimefenixService::class.java)
    }

    private fun buildClient(): OkHttpClient {
        return NetworkClient.default.newBuilder()
            .readTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .dns(DnsResolver.doh)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    )
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "es-ES,es;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Referer", "${baseUrl.trimEnd('/')}/")
                    .header("Origin", baseUrl.trimEnd('/'))
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private interface AnimefenixService {
        @GET
        suspend fun getPage(
            @Url url: String,
            @Header("Referer") referer: String = "",
        ): Document
    }

    private fun absoluteUrl(href: String): String {
        return when {
            href.startsWith("http") -> href
            href.startsWith("/") -> "${baseUrl.trimEnd('/')}$href"
            else -> "${baseUrl.trimEnd('/')}/$href"
        }
    }

    private fun parseAnimeCards(elements: List<Element>): List<TvShow> {
        return elements.mapNotNull { el ->
            val a = when {
                el.tagName().equals("a", ignoreCase = true) &&
                    el.attr("href").contains("/anime/") -> el
                else -> el.selectFirst("a[href*=/anime/]")
                    ?: el.selectFirst("a[href]")
                    ?: return@mapNotNull null
            }
            val href = a.attr("href").ifBlank { return@mapNotNull null }
            val title = el.selectFirst("h3, .title, .anime-title, .media-body h3, .description h3, p:not(.gray)")
                ?.text()
                ?.trim()
                ?.ifBlank { null }
                ?: a.attr("title").ifBlank { a.text() }.trim()
            if (title.isBlank()) return@mapNotNull null
            val image = el.selectFirst("img")
            TvShow(
                id = absoluteUrl(href),
                title = title,
                poster = image?.attr("data-src")?.ifBlank { null }
                    ?: image?.attr("src")?.ifBlank { null }
            )
        }.distinctBy { it.id }
    }

    private fun parseHomeEpisodes(document: Document): List<TvShow> {
        val links = document.select(
            "article.episode a[href*=/ver/], .list-episodes a[href*=/ver/], a[href*=/ver/]"
        )
        return links.mapNotNull { a ->
            val href = a.attr("href").ifBlank { return@mapNotNull null }
            val title = a.selectFirst(".title, .anime-title, h3, h4")?.text()?.trim()
                ?: a.attr("title").ifBlank { a.text() }.trim()
            if (title.isBlank()) return@mapNotNull null
            val image = a.selectFirst("img")
            TvShow(
                id = absoluteUrl(href),
                title = title,
                poster = image?.attr("data-src")?.ifBlank { null }
                    ?: image?.attr("src")?.ifBlank { null }
            )
        }.distinctBy { it.id }
    }

    override suspend fun getHome(): List<Category> {
        return try {
            coroutineScope {
                val homeDeferred = async { service.getPage(baseUrl) }
                val directoryDeferred = async { service.getPage("$baseUrl/directorio?p=1") }

                val categories = mutableListOf<Category>()
                var sawCloudflare = false
                var sawTinyPage = false

                runCatching {
                    val home = homeDeferred.await()
                    if (looksLikeCloudflare(home)) {
                        sawCloudflare = true
                        return@runCatching
                    }
                    if (home.html().length < 4000) {
                        sawTinyPage = true
                        return@runCatching
                    }
                    val latest = parseHomeEpisodes(home).take(24)
                    if (latest.isNotEmpty()) {
                        categories.add(Category("Últimos episodios", latest))
                    }
                    val featured = parseAnimeCards(
                        home.select("article.anime, .anime, .animes .anime, .media.anime, li.anime")
                    ).take(20)
                    if (featured.isNotEmpty()) {
                        categories.add(Category(Category.FEATURED, featured.map { it.copy(banner = it.poster) }))
                    }
                }.onFailure { Log.w(TAG, "Home parse failed: ${it.message}") }

                // Directory is often Cloudflare-blocked from some IPs; home alone is enough.
                runCatching {
                    val directory = directoryDeferred.await()
                    if (looksLikeCloudflare(directory)) {
                        sawCloudflare = true
                        return@runCatching
                    }
                    if (directory.html().length < 4000) {
                        sawTinyPage = true
                        return@runCatching
                    }
                    val shows = parseAnimeCards(
                        directory.select(
                            "article.anime, .anime, .animes .anime, .media.anime, li.anime, " +
                                "article, .group, .card, a[href*=/anime/]"
                        )
                    )
                    if (shows.isNotEmpty()) {
                        categories.add(Category("Directorio", shows))
                    }
                }.onFailure { Log.w(TAG, "Directory parse failed: ${it.message}") }

                // If home worked, never fail the whole provider just because /directorio is CF.
                if (categories.isEmpty() && sawCloudflare) {
                    throw Exception(
                        "Animefenix bloqueado por Cloudflare en $baseUrl. " +
                            "Abre el sitio en el dispositivo o cambia la URL del proveedor."
                    )
                }
                if (categories.isEmpty() && sawTinyPage) {
                    throw Exception(
                        "Animefenix devolvió una página incompleta en $baseUrl (posible antibot). Intenta de nuevo."
                    )
                }
                if (categories.isEmpty()) {
                    throw Exception("Animefenix home vacío en $baseUrl (dominio o selectores desactualizados)")
                }
                categories
            }
        } catch (e: Exception) {
            // If parallel directory call threw CF but home already filled categories, surface home.
            if (e.message?.contains("Animefenix", ignoreCase = true) == true) throw e
            Log.e(TAG, "getHome failed: ${e.message}", e)
            throw e
        }
    }

    private fun looksLikeCloudflare(document: Document): Boolean {
        val html = document.html()
        return html.contains("Just a moment", ignoreCase = true) ||
            html.contains("cf-browser-verification", ignoreCase = true) ||
            html.contains("Checking your browser", ignoreCase = true) ||
            html.contains("challenge-platform", ignoreCase = true)
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            return listOf(
                Genre("accion", "Acción"), Genre("aventura", "Aventura"), Genre("comedia", "Comedia"),
                Genre("drama", "Drama"), Genre("fantasia", "Fantasía"), Genre("romance", "Romance"),
                Genre("shounen", "Shounen"), Genre("seinen", "Seinen"), Genre("sobrenatural", "Sobrenatural")
            )
        }
        return try {
            val document = service.getPage("$baseUrl/directorio?q=${java.net.URLEncoder.encode(query, "UTF-8")}&p=$page")
            parseAnimeCards(document.select(".anime, .animes .anime, .media.anime, li.anime"))
        } catch (e: Exception) {
            Log.w(TAG, "search failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return try {
            val document = service.getPage("$baseUrl/directorio?p=$page")
            parseAnimeCards(document.select(".anime, .animes .anime, .media.anime, li.anime"))
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return try {
            val document = service.getPage("$baseUrl/directorio?tipo=2&p=$page")
            parseAnimeCards(document.select(".anime, .animes .anime, .media.anime, li.anime")).map {
                Movie(id = it.id, title = it.title, poster = it.poster)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            val document = service.getPage("$baseUrl/directorio?genero=$id&p=$page")
            val shows = parseAnimeCards(document.select(".anime, .animes .anime, .media.anime, li.anime"))
            Genre(id = id, name = id.replaceFirstChar { it.uppercase() }, shows = shows)
        } catch (e: Exception) {
            Genre(id = id, name = "Error", shows = emptyList())
        }
    }

    override suspend fun getMovie(id: String): Movie {
        val show = getTvShow(id)
        return Movie(
            id = show.id,
            title = show.title,
            overview = show.overview,
            poster = show.poster,
            banner = show.banner,
            genres = show.genres,
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        return try {
            val url = absoluteUrl(id)
            val document = service.getPage(url)
            val title = document.selectFirst("h1.anime-title, h1.text-4xl, h1")?.ownText()?.trim()
                ?: document.selectFirst("h1")?.text()?.trim()
                ?: ""
            val poster = document.selectFirst("#anime_image, .thumb img, .anime-single img")?.let {
                it.attr("data-src").ifEmpty { it.attr("src") }
            }
            val overview = document.selectFirst("h2:contains(Sinopsis) + p, .mb-6 p.text-gray-300, .sinopsis p, .description")
                ?.text()
            val genres = document.select("a[href*=genero], a[href*=/directorio]").mapNotNull {
                val name = it.text().trim()
                if (name.isBlank() || name.equals("Animes", true)) return@mapNotNull null
                Genre(id = it.attr("href").substringAfter("genero=").substringAfterLast("/"), name = name)
            }.distinctBy { it.name }

            val episodes = mutableListOf<Episode>()
            val slug = url.trimEnd('/').substringAfterLast("/")

            // Current mirror embeds episode numbers in page JS: var episodes = [8,7,6,...]
            val scriptEpisodes = document.select("script").mapNotNull { script ->
                Regex("""var\s+episodes\s*=\s*\[([^\]]+)\]""")
                    .find(script.data())
                    ?.groupValues
                    ?.getOrNull(1)
            }.firstOrNull()
            if (!scriptEpisodes.isNullOrBlank()) {
                scriptEpisodes.split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .sorted()
                    .forEach { number ->
                        episodes += Episode(
                            id = absoluteUrl("/ver/$slug-$number"),
                            number = number,
                            title = "Episodio $number",
                        )
                    }
            }

            // Legacy Neo theme AJAX episode cards
            if (episodes.isEmpty()) {
                val episodeButtons = document.select(".episode-navigation button.episode-btn")
                val startValues = if (episodeButtons.isNotEmpty()) {
                    episodeButtons.mapNotNull { btn ->
                        Regex("""loadEpisodes\((\d+)""").find(btn.attr("onclick"))?.groupValues?.get(1)
                    }.distinct()
                } else {
                    listOf("0")
                }

                coroutineScope {
                    val deferredEpisodes = startValues.map { start ->
                        async {
                            try {
                                val ajaxUrl = "$url?id=$slug&load=episodes&start=$start"
                                val epDoc = service.getPage(ajaxUrl)
                                epDoc.select(".episode-card, .episodes-list a[href*=/ver/], a[href*=/ver/]").mapNotNull { epEl ->
                                    val rawHref = epEl.attr("href")
                                    if (rawHref.isBlank()) return@mapNotNull null
                                    val epUrl = absoluteUrl(rawHref)
                                    val epTitle = epEl.selectFirst(".ep-title, .d-title, b")?.text()?.trim()
                                        ?: epEl.text().trim().ifBlank { "Episodio" }
                                    val epNum = Regex("""\d+""").find(epTitle)?.value?.toIntOrNull()
                                        ?: Regex("""-(\d+)$""").find(epUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()
                                        ?: 0
                                    Episode(id = epUrl, number = epNum, title = epTitle)
                                }
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }
                    deferredEpisodes.map { it.await() }.forEach { episodes.addAll(it) }
                }
            }

            episodes.sortBy { it.number }

            TvShow(
                id = url,
                title = title,
                poster = poster,
                overview = overview,
                genres = genres,
                seasons = listOf(Season(id = url, number = 1, title = "Episodios", episodes = episodes))
            )
        } catch (e: Exception) {
            Log.e(TAG, "getTvShow failed: ${e.message}", e)
            TvShow(id = id, title = "Error al cargar")
        }
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        return try {
            getTvShow(seasonId).seasons.firstOrNull()?.episodes ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return try {
            val url = if (videoType is Video.Type.Movie) {
                val moviePage = service.getPage(absoluteUrl(id))
                moviePage.selectFirst(".divide-y li > a, .episodes-list a[href*=/ver/], a[href*=/ver/]")
                    ?.attr("href")
                    ?.let(::absoluteUrl)
                    ?: absoluteUrl(id)
            } else {
                absoluteUrl(id)
            }

            val document = service.getPage(url)
            val servers = mutableListOf<Video.Server>()

            // Current animefenix.live embeds: var videos = [["Mega","https://...",0,0], ...]
            parseVideosArray(document).forEach { servers += it }

            // Legacy tabsArray iframe embeds
            if (servers.isEmpty()) {
                document.selectFirst("script:containsData(var tabsArray)")?.let { script ->
                    val names = document.select(".episode-page__servers-list li a, .servers li a, .nav-tabs a").map { a ->
                        a.select("span").last()?.text()?.trim().orEmpty().ifBlank { a.text().trim() }
                    }
                    val urls = script.data()
                        .substringAfter("<iframe").split("src='")
                        .drop(1)
                        .map { it.substringBefore("'").substringAfter("redirect.php?id=").trim() }
                    val count = minOf(urls.size, names.size.coerceAtLeast(urls.size))
                    for (i in 0 until count) {
                        val src = urls.getOrNull(i)?.takeIf { it.isNotBlank() } ?: continue
                        val name = names.getOrNull(i)?.ifBlank { null } ?: "Server ${i + 1}"
                        servers += Video.Server(id = src, name = name, src = src)
                    }
                }
            }

            // Direct iframes / data-src embeds on current mirror
            if (servers.isEmpty()) {
                document.select("iframe[src], iframe[data-src], [data-url]").forEachIndexed { index, el ->
                    val src = el.attr("src").ifBlank { el.attr("data-src") }.ifBlank { el.attr("data-url") }
                    if (src.isBlank() || src.startsWith("about:")) return@forEachIndexed
                    val absolute = when {
                        src.startsWith("//") -> "https:$src"
                        src.startsWith("http") -> src
                        src.contains("redirect.php?id=") -> absoluteUrl(src.substringAfter("redirect.php?id=").let { "/redirect.php?id=$it" })
                        else -> absoluteUrl(src)
                    }
                    servers += Video.Server(
                        id = absolute,
                        name = "Server ${index + 1}",
                        src = absolute,
                    )
                }
            }

            // redirect.php links in page scripts
            if (servers.isEmpty()) {
                Regex("""redirect\.php\?id=([^"'\s]+)""")
                    .findAll(document.html())
                    .map { it.groupValues[1] }
                    .distinct()
                    .forEachIndexed { index, encoded ->
                        val src = if (encoded.startsWith("http")) encoded else absoluteUrl("/redirect.php?id=$encoded")
                        servers += Video.Server(id = src, name = "Server ${index + 1}", src = src)
                    }
            }

            servers.distinctBy { it.src.ifBlank { it.id } }
        } catch (e: Exception) {
            Log.w(TAG, "getServers failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.src.ifBlank { server.id }, server)
    }

    private fun parseVideosArray(document: Document): List<Video.Server> {
        val scriptSource = document.select("script").asSequence()
            .map { it.data().ifBlank { it.html() } }
            .firstOrNull { it.contains("var videos") }
            ?: return emptyList()
        val arrayLiteral = extractJsArray(scriptSource, "var videos") ?: return emptyList()
        val videos = runCatching { JSONArray(arrayLiteral) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until videos.length()) {
                val entry = videos.optJSONArray(i) ?: continue
                val label = entry.optString(0).trim().ifBlank { "Server ${i + 1}" }
                val url = entry.optString(1).trim()
                    .replace("\\/", "/")
                if (url.isBlank() || !url.startsWith("http")) continue
                add(Video.Server(id = url, name = label, src = url))
            }
        }
    }

    private fun extractJsArray(source: String, marker: String): String? {
        val markerIndex = source.indexOf(marker).takeIf { it >= 0 } ?: return null
        val start = source.indexOf('[', markerIndex).takeIf { it >= 0 } ?: return null
        var depth = 0
        var quoted = false
        var escaped = false
        for (i in start until source.length) {
            val c = source[i]
            when {
                escaped -> escaped = false
                c == '\\' && quoted -> escaped = true
                c == '"' || c == '\'' -> quoted = !quoted
                !quoted && c == '[' -> depth++
                !quoted && c == ']' -> {
                    depth--
                    if (depth == 0) return source.substring(start, i + 1)
                }
            }
        }
        return null
    }

    override suspend fun getPeople(id: String, page: Int): People {
        throw Exception("Esta función no está disponible en AnimeFenix")
    }
}
