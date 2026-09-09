package com.dskja.betterstreamflix.providers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import com.dskja.betterstreamflix.utils.UserPreferences

import android.content.Context
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.dskja.betterstreamflix.BetterStreamflixApp
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.extractors.Extractor
import com.dskja.betterstreamflix.models.*
import com.dskja.betterstreamflix.models.sololatino.Item
import com.dskja.betterstreamflix.utils.DnsResolver
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.WebViewResolver
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.io.File
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers


object SoloLatinoProvider : Provider, ProviderConfigUrl {

    override val name = "SoloLatino"
    override val defaultBaseUrl = "https://sololatino.net"
    override val baseUrl: String
        get() = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL).ifBlank { defaultBaseUrl }
    override val changeUrlMutex = Mutex()

    override suspend fun onChangeUrl(forceRefresh: Boolean): String = changeUrlMutex.withLock {
        client = getOkHttpClient()
        service = buildService(baseUrl)
        baseUrl
    }
    override val language = "es"

    private const val TAG = "SoloLatinoBypass"
    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private var webViewResolver: WebViewResolver? = null
    private val providerMutex = Mutex()

    private var client = getOkHttpClient()
    private var service = buildService(defaultBaseUrl)

    fun init(context: Context) {
        webViewResolver = WebViewResolver(context)
    }

    private fun getResolver(): WebViewResolver {
        return webViewResolver ?: WebViewResolver(BetterStreamflixApp.instance).also {
            webViewResolver = it
        }
    }

    private fun buildService(root: String): SoloLatinoService {
        val normalized = if (root.endsWith("/")) root else "$root/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .addConverterFactory(JsoupConverterFactory.create())
            .client(client)
            .build()
            .create(SoloLatinoService::class.java)
    }

    private fun getOkHttpClient(): OkHttpClient {
        val appCache = Cache(File("cacheDir", "okhttpcache"), 10 * 1024 * 1024)

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", BROWSER_UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "es-ES,es;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Referer", "$baseUrl/")
                    .header("Origin", baseUrl.trimEnd('/'))
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("Upgrade-Insecure-Requests", "1")
                    .build()
                chain.proceed(request)
            }
            .cookieJar(NetworkClient.cookieJar)
            .cache(appCache)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)

        return clientBuilder.dns(DnsResolver.doh).build()
    }

    private interface SoloLatinoService {
        @GET
        suspend fun getPage(@Url url: String): Document

    }

    private fun providerHost(): String = runCatching { baseUrl.toHttpUrl().host }.getOrDefault("sololatino.net")

    private fun isProviderUrl(url: String): Boolean {
        return runCatching {
            val host = url.toHttpUrl().host.lowercase()
            val base = providerHost().lowercase()
            host == base || host.endsWith(".$base") || host.contains("sololatino")
        }.getOrDefault(false)
    }

    private fun requiresClearance(html: String): Boolean {
        // Avoid matching every page that merely references /cdn-cgi/challenge-platform/
        return html.contains("Just a moment", ignoreCase = true) ||
            html.contains("cf-browser-verification", ignoreCase = true) ||
            html.contains("Checking your browser", ignoreCase = true) ||
            html.contains("cf-mitigated", ignoreCase = true)
    }

    private fun hasUsableContent(html: String): Boolean {
        return html.contains("server-btn") ||
            html.contains("hero__slide") ||
            html.contains("card__poster") ||
            html.contains("card__title") ||
            html.contains("data-player") ||
            html.contains("section-title") ||
            html.contains("dataLink") ||
            html.contains("go_to_player")
    }

    private suspend fun getDocument(url: String): Document {
        try {
            val document = service.getPage(url)
            if (isProviderUrl(url) && requiresClearance(document.outerHtml())) {
                throw Exception("SoloLatino Cloudflare challenge detected")
            }
            return document
        } catch (e: Exception) {
            // External embed hosts must not trigger SoloLatino WebView clearance.
            if (!isProviderUrl(url)) throw e

            val httpCode = (e as? retrofit2.HttpException)?.code()
            val challengeBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string().orEmpty()
            val needsWebView = requiresClearance(e.message.orEmpty()) ||
                requiresClearance(challengeBody) ||
                httpCode == 403 ||
                httpCode == 503 ||
                e.message?.contains("Cloudflare", ignoreCase = true) == true

            if (!needsWebView) throw e

            Log.d(TAG, "Using WebView bypass for $url")
            val result = providerMutex.withLock {
                getResolver().getResult(
                    url = url,
                    headers = mapOf(
                        "User-Agent" to BROWSER_UA,
                        "Accept-Language" to "es-ES,es;q=0.9,en;q=0.8",
                    ),
                    completion = { currentUrl, htmlText, cookies ->
                        val hasClearance = cookies.contains("cf_clearance=", ignoreCase = true)
                        (!requiresClearance(htmlText) && hasUsableContent(htmlText)) || hasClearance
                    },
                    shouldAllowNavigation = { targetUrl, _ ->
                        runCatching {
                            isProviderUrl(targetUrl) || targetUrl.contains("/cdn-cgi/", ignoreCase = true)
                        }.getOrDefault(false)
                    },
                )
            }
            CookieManager.getInstance().flush()

            // Prefer a fresh OkHttp fetch once cf_clearance is in CookieManager.
            runCatching {
                val retried = service.getPage(url)
                if (!requiresClearance(retried.outerHtml()) && hasUsableContent(retried.outerHtml())) {
                    return retried
                }
            }

            return Jsoup.parse(result.html, url).apply { setBaseUri(baseUrl) }
        }
    }

    override val logo = "$baseUrl/images/logo.png"

    override suspend fun getHome(): List<Category> = coroutineScope {
        val categories = mutableListOf<Category>()

        try {
            val mainDoc = getDocument(baseUrl)

            // 1. Featured
            val bannerShows = parseBannerShows(mainDoc).take(12)
            if (bannerShows.isNotEmpty()) {
                categories.add(Category(Category.FEATURED, bannerShows))
            }

            // 2. Sections from the home page
            val sections = mainDoc.select("section")
            for (section in sections) {
                val title = section.selectFirst(".section-title")?.text() ?: continue
                val shows = parseMixed(section)
                if (shows.isNotEmpty()) {
                    categories.add(Category(title, shows.take(12)))
                }
            }

            if (categories.isEmpty()) {
                throw Exception("SoloLatino home vacío (posible Cloudflare 403)")
            }
        } catch (e: Exception) {
            throw Exception("SoloLatino: ${e.message}")
        }

        categories
    }

    private fun parseMixed(element: Element): List<Show> {
        val cards = if (element is Document) element.select("div.card") else element.select("div.card")
        return cards.mapNotNull { card ->
            val linkElement = card.selectFirst("a") ?: card.parents().firstOrNull { it.tagName() == "a" }
            val href = linkElement?.attr("href") ?: return@mapNotNull null
            val absoluteUrl = if (href.startsWith("http")) href else "$baseUrl$href"
            
            val imgElement = card.selectFirst("img.card__poster")
            val poster = imgElement?.attr("src") ?: ""
            
            val titleElement = card.selectFirst(".card__title")
            val title = titleElement?.text() ?: ""
            
            val year = card.selectFirst(".card__year")?.text()
            val isMovie = card.selectFirst(".badge-movie") != null || absoluteUrl.contains("/pelicula/")

            if (isMovie) {
                Movie(
                    id = absoluteUrl,
                    title = title,
                    released = year,
                    poster = poster
                )
            } else {
                TvShow(
                    id = absoluteUrl,
                    title = title,
                    released = year,
                    poster = poster
                )
            }
        }
    }

    private fun parseBannerShows(document: Document): List<Show> {
        return document.select(".hero__slide").mapNotNull { slide ->
            val linkElement = slide.selectFirst("a.btn-accent") ?: return@mapNotNull null
            val href = linkElement.attr("href")
            val absoluteUrl = if (href.startsWith("http")) href else "$baseUrl$href"
            
            val posterElement = slide.selectFirst(".hero__bg")
            val style = posterElement?.attr("style") ?: ""
            val bannerUrl = if (style.contains("url('")) {
                style.substringAfter("url('").substringBefore("')")
            } else ""
            
            val title = slide.selectFirst(".hero__logo-img")?.attr("alt")?.takeIf { it.isNotBlank() }
                ?: slide.selectFirst(".hero__content p.font-display")?.text()?.trim() 
                ?: ""
            
            val year = slide.selectFirst("div.flex.items-center span")?.text()?.takeIf { it.matches(Regex("""\d{4}""")) }
            
            val overview = slide.selectFirst(".text-sm.leading-relaxed.line-clamp-4")?.text()?.trim()

            if (absoluteUrl.contains("/pelicula/")) {
                Movie(
                    id = absoluteUrl,
                    title = title,
                    banner = bannerUrl,
                    overview = overview,
                    released = year
                )
            } else {
                TvShow(
                    id = absoluteUrl,
                    title = title,
                    banner = bannerUrl,
                    overview = overview,
                    released = year
                )
            }
        }
    }

    private fun parseMovies(document: Document): List<Movie> {
        return parseMixed(document).filterIsInstance<Movie>()
    }

    private fun parseTvShows(document: Document): List<TvShow> {
        return parseMixed(document).filterIsInstance<TvShow>()
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            return listOf(
                Genre("accion", "Acción"),
                Genre("action-adventure", "Action & Adventure"),
                Genre("animacion", "Animación"),
                Genre("aventura", "Aventura"),
                Genre("belica", "Bélica"),
                Genre("ciencia-ficcion", "Ciencia Ficción"),
                Genre("comedia", "Comedia"),
                Genre("crimen", "Crimen"),
                Genre("disney", "Disney"),
                Genre("documental", "Documental"),
                Genre("drama", "Drama"),
                Genre("familia", "Familia"),
                Genre("fantasia", "Fantasía"),
                Genre("hbo", "HBO"),
                Genre("historia", "Historia"),
                Genre("kids", "Kids"),
                Genre("misterio", "Misterio"),
                Genre("musica", "Música"),
                Genre("romance", "Romance"),
                Genre("sci-fi-fantasy", "Sci-Fi & Fantasy"),
                Genre("soap", "Soap"),
                Genre("suspense", "Suspense"),
                Genre("talk", "Talk"),
                Genre("terror", "Terror"),
                Genre("war-politics", "War & Politics"),
                Genre("western", "Western"),
            )
        }

        return try {
            val url = if (page > 1) "$baseUrl/buscar?q=$query&page=$page" else "$baseUrl/buscar?q=$query"
            val document = getDocument(url)
            parseMixed(document)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return try {
            val url = if (page > 1) "$baseUrl/peliculas/page/$page" else "$baseUrl/peliculas"
            val document = getDocument(url)
            parseMovies(document)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return try {
            val url = if (page > 1) "$baseUrl/series/page/$page" else "$baseUrl/series"
            val document = getDocument(url)
            parseTvShows(document)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            val url = if (page > 1) "$baseUrl/genero/$id/page/$page" else "$baseUrl/genero/$id"
            val document = getDocument(url)
            val shows = parseMixed(document)
            Genre(
                id = id,
                name = id.replace("-", " ").replaceFirstChar { it.uppercase() },
                shows = shows
            )
        } catch (e: Exception) {
            Genre(id = id, name = id.replaceFirstChar { it.uppercase() }, shows = emptyList())
        }
    }

    override suspend fun getMovie(id: String): Movie {
        return try {
            val document = getDocument(id)
            val title = document.selectFirst("h1")?.text() 
                ?: document.select("nav[aria-label=breadcrumb] span:last-child").text()
            
            val posterElement = document.selectFirst("div.-mt-28 img, div.flex-shrink-0.mx-auto img, img.card__poster")
            val posterUrl = posterElement?.attr("src") ?: ""
            
            val overview = document.selectFirst(".description")?.text() 
                ?: document.selectFirst(".storyline")?.text()
                ?: document.selectFirst("p.leading-relaxed")?.text()
                ?: document.selectFirst("p.text-sm")?.text() ?: ""

            val banner = document.selectFirst(".hero__bg, .detail-hero__bg")?.attr("style")
                ?.substringAfter("url('")?.substringBefore("')")
            val rating = document.selectFirst(".rating-badge--tmdb")?.text()?.let { 
                Regex("""(\d+\.?\d*)""").find(it)?.value?.toDoubleOrNull()
            }

            val runtimeStr = document.select("div.flex.flex-wrap.items-center.gap-4.text-sm.mb-5 span")
                .map { it.text() }
                .firstOrNull { it.contains("h") || (it.contains("m") && it.any { c -> c.isDigit() }) }
            
            val runtime = runtimeStr?.let { str ->
                val hours = Regex("""(\d+)h""").find(str)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val minutes = Regex("""(\d+)m""").find(str)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (hours == 0 && minutes == 0) null else (hours * 60) + minutes
            }
            val released = document.selectFirst("span.date")?.text() ?: document.selectFirst(".card__year")?.text()
            val trailer = document.selectFirst("button[data-trailer]")?.attr("data-trailer")?.let { 
                "https://www.youtube.com/watch?v=$it" 
            }

            val genres = document.select("div.flex-1.min-w-0 a[href*=/genero/]").map {
                Genre(id = "", name = it.text())
            }

            val cast = document.select("div.cast-card").map {
                People(
                    id = it.selectFirst("a")?.attr("href") ?: "",
                    name = it.selectFirst("p.font-semibold")?.text() ?: "",
                    image = it.selectFirst("img")?.attr("src")
                )
            }

            val recommendations = document.select("div.card, article.card").mapNotNull { article ->
                val linkElement = article.selectFirst("a") ?: return@mapNotNull null
                val imgElement = article.selectFirst("img") ?: return@mapNotNull null
                val href = linkElement.attr("href")
                val absoluteUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                val recPoster = imgElement.attr("src") ?: imgElement.attr("data-srcset")

                Movie(
                    id = absoluteUrl,
                    title = imgElement.attr("alt"),
                    poster = if (recPoster.startsWith("http")) recPoster else "$baseUrl$recPoster"
                )
            }

            Movie(
                id = id,
                title = title,
                poster = if (posterUrl.startsWith("http")) posterUrl else "$baseUrl$posterUrl",
                banner = banner,
                genres = genres,
                overview = overview,
                rating = rating,
                runtime = runtime,
                released = released,
                trailer = trailer,
                cast = cast,
                recommendations = recommendations,
            )
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun getTvShow(id: String): TvShow {
        return try {
            val document = getDocument(id)
            val title = document.selectFirst("h1")?.text()
                ?: document.select("nav[aria-label=breadcrumb] span:last-child").text()
            
            val posterElement = document.selectFirst("div.-mt-28 img, div.flex-shrink-0.mx-auto img, img.card__poster")
            val posterUrl = posterElement?.attr("src") ?: ""

            val overview = document.selectFirst(".description")?.text() 
                ?: document.selectFirst(".storyline")?.text()
                ?: document.selectFirst("p.leading-relaxed")?.text()
                ?: document.selectFirst("p.text-sm")?.text() ?: ""

            val seasons = document.select("div[data-season-panel]").map { seasonElement ->
                val seasonNumber = seasonElement.attr("data-season-panel").toIntOrNull() ?: 0
                Season(id = "$id@$seasonNumber", number = seasonNumber, title = "Temporada $seasonNumber")
            }.filter { it.number != 0 }

            val banner = document.selectFirst(".hero__bg, .detail-hero__bg")?.attr("style")
                ?.substringAfter("url('")?.substringBefore("')")
                
            val rating = document.selectFirst(".rating-badge--tmdb")?.text()?.let { 
                Regex("""(\d+\.?\d*)""").find(it)?.value?.toDoubleOrNull()
            }
            val runtime: Int? = null
            
            val yearMatch = Regex("""\d{4}""").find(document.text())
            val released = yearMatch?.value
            
            val trailer = document.selectFirst("button[data-trailer]")?.attr("data-trailer")?.let { 
                "https://www.youtube.com/watch?v=$it" 
            }

            val genres = document.select("div.flex-1.min-w-0 a[href*=/genero/]").map {
                Genre(id = "", name = it.text())
            }

            val cast = document.select("div.cast-card, div.sbox.srepart div.person").map {
                People(
                    id = it.selectFirst("a")?.attr("href") ?: "",
                    name = it.selectFirst("p.font-semibold, .name a")?.text() ?: "",
                    image = it.selectFirst("img")?.attr("src")
                )
            }

            val recommendations = document.select("div.card, article.card").mapNotNull { article ->
                val linkElement = article.selectFirst("a") ?: return@mapNotNull null
                val imgElement = article.selectFirst("img") ?: return@mapNotNull null
                val href = linkElement.attr("href")
                val absoluteUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                val recPoster = imgElement.attr("src") ?: imgElement.attr("data-srcset")

                TvShow(
                    id = absoluteUrl,
                    title = imgElement.attr("alt"),
                    poster = if (recPoster.startsWith("http")) recPoster else "$baseUrl$recPoster"
                )
            }

            TvShow(
                id = id,
                title = title,
                poster = if (posterUrl.startsWith("http")) posterUrl else "$baseUrl$posterUrl",
                banner = banner,
                genres = genres,
                overview = overview,
                seasons = seasons.sortedBy { it.number },
                rating = rating,
                runtime = runtime,
                released = released,
                trailer = trailer,
                cast = cast,
                recommendations = recommendations,
            )
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        return try {
            val showId = seasonId.substringBefore("@")
            val seasonNumber = seasonId.substringAfter("@")
            val document = getDocument(showId)
            val seasonElement = document.select("div[data-season-panel=$seasonNumber]").firstOrNull() ?: return emptyList()
            seasonElement.select("a.ep-item").map { episodeElement ->
                val epNumText = episodeElement.selectFirst("p.ep-num")?.text() ?: "E0"
                val episodeNum = epNumText.filter { it.isDigit() }.toIntOrNull() ?: 0
                val episodeTitle = episodeElement.selectFirst("p.leading-tight")?.text()
                    ?: "Episodio $episodeNum"
                val episodeOverview = episodeElement.selectFirst("p.text-xs.line-clamp-2")?.text() 
                    ?: episodeElement.selectFirst("p[style*='color:#5050a0']")?.text()

                Episode(
                    id = episodeElement.attr("href"),
                    number = episodeNum,
                    title = episodeTitle.trim(),
                    overview = episodeOverview?.trim(),
                    poster = episodeElement.selectFirst("img.ep-thumb")?.attr("src")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return try {
            val doc = getDocument(id)
            val allServers = mutableListOf<Video.Server>()

            val serverBtns = doc.select(
                "button.server-btn, button[data-server-url], button[data-player-token], " +
                    "a.server-btn, [data-player-token], li[data-server-url]"
            )
            for (btn in serverBtns) {
                val serverUrl = btn.attr("data-server-url").ifBlank { btn.attr("data-url") }
                val playerId = btn.attr("data-player-id")
                val playerModel = btn.attr("data-player-model")
                val playerToken = btn.attr("data-player-token")

                if (playerToken.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val payload = JSONObject().put("t", playerToken)
                            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())

                            var requestBuilder = Request.Builder()
                                .url("$baseUrl/api/player-url")
                                .post(requestBody)
                                .header("Referer", id)
                                .header("Origin", baseUrl.trimEnd('/'))
                                .header("X-Requested-With", "XMLHttpRequest")
                                .header("Content-Type", "application/json")
                                .header("Accept", "application/json")
                                .header("User-Agent", BROWSER_UA)

                            fun addXsrfHeader(builder: Request.Builder) {
                                val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
                                val xsrfCookie = cookies.firstOrNull { it.name == "XSRF-TOKEN" }?.value
                                if (xsrfCookie != null) {
                                    val decoded = java.net.URLDecoder.decode(xsrfCookie, "UTF-8")
                                    builder.header("X-XSRF-TOKEN", decoded)
                                }
                            }

                            addXsrfHeader(requestBuilder)
                            var response = client.newCall(requestBuilder.build()).execute()

                            if (response.code == 419 || response.code == 403) {
                                response.close()
                                val csrfReq = Request.Builder()
                                    .url("$baseUrl/sanctum/csrf-cookie")
                                    .header("Referer", id)
                                    .header("User-Agent", BROWSER_UA)
                                    .build()
                                client.newCall(csrfReq).execute().close()

                                requestBuilder = Request.Builder()
                                    .url("$baseUrl/api/player-url")
                                    .post(requestBody)
                                    .header("Referer", id)
                                    .header("Origin", baseUrl.trimEnd('/'))
                                    .header("X-Requested-With", "XMLHttpRequest")
                                    .header("Content-Type", "application/json")
                                    .header("Accept", "application/json")
                                    .header("User-Agent", BROWSER_UA)
                                addXsrfHeader(requestBuilder)
                                response = client.newCall(requestBuilder.build()).execute()
                            }

                            val jsonText = response.body?.string() ?: ""
                            response.close()

                            val jsonObject = JSONObject(jsonText)
                            val resolvedUrl = jsonObject.optString("url")
                                .ifBlank { jsonObject.optString("embed") }
                                .ifBlank { jsonObject.optString("link") }
                            if (resolvedUrl.isNotEmpty()) {
                                allServers.addAll(processIframe(resolvedUrl, id))
                            }
                        } catch (_: Exception) {
                            // Ignore single network errors
                        }
                    }
                } else if (serverUrl.isNotEmpty()) {
                    allServers.addAll(processIframe(serverUrl, id))
                } else if (playerId.isNotEmpty() && playerModel.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val request = Request.Builder()
                                .url("$baseUrl/api/player-url/$playerModel/$playerId")
                                .header("Referer", id)
                                .header("Origin", baseUrl.trimEnd('/'))
                                .header("X-Requested-With", "XMLHttpRequest")
                                .header("User-Agent", BROWSER_UA)
                                .build()
                            val response = client.newCall(request).execute()
                            val jsonText = response.body?.string() ?: ""
                            response.close()
                            val jsonObject = JSONObject(jsonText)
                            val resolvedUrl = jsonObject.optString("url")
                            if (resolvedUrl.isNotEmpty()) {
                                allServers.addAll(processIframe(resolvedUrl, id))
                            }
                        } catch (_: Exception) {
                            // Ignore single network errors
                        }
                    }
                }
            }

            // Fallback: page-level iframes / player markup when server buttons are absent
            if (allServers.isEmpty()) {
                doc.select("iframe[src], iframe[data-src], #player iframe, .player iframe").forEach { iframe ->
                    val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }
                    if (src.isNotBlank() && !src.startsWith("about:")) {
                        allServers.addAll(processIframe(absUrl(src), id))
                    }
                }
            }

            allServers.distinctBy { it.id.ifBlank { it.src } }
        } catch (e: Exception) {
            Log.e(TAG, "getServers failed: ${e.message}")
            emptyList()
        }
    }

    private fun absUrl(path: String): String {
        return when {
            path.startsWith("http") -> path
            path.startsWith("//") -> "https:$path"
            else -> baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        }
    }

    private fun solvePoW(challenge: String, difficulty: Int, salt: String): ByteArray {
        val prefix = "0".repeat(difficulty)
        var nonce = 0
        val md = MessageDigest.getInstance("SHA-256")
        while (true) {
            val input = challenge + nonce
            val hashBytes = md.digest(input.toByteArray(Charsets.UTF_8))
            val hashStr = hashBytes.joinToString("") { "%02x".format(it) }
            if (hashStr.startsWith(prefix)) {
                val keyMaterial = challenge + nonce + salt
                return md.digest(keyMaterial.toByteArray(Charsets.UTF_8))
            }
            nonce++
        }
    }

    private fun decryptAES(encrypted: String, aesKey: ByteArray): String? {
        return try {
            val decoded = try {
                Base64.decode(encrypted, Base64.DEFAULT)
            } catch (_: Exception) {
                Base64.decode(encrypted, Base64.URL_SAFE or Base64.NO_WRAP)
            }
            val iv = decoded.copyOfRange(0, 16)
            val cipherText = decoded.copyOfRange(16, decoded.size)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchEmbedDocument(iframeUrl: String): Document {
        // Prefer direct OkHttp for embeds; only use CF WebView for SoloLatino-hosted pages.
        return if (isProviderUrl(iframeUrl)) {
            getDocument(iframeUrl)
        } else {
            try {
                service.getPage(iframeUrl)
            } catch (_: Exception) {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(iframeUrl)
                        .header("User-Agent", BROWSER_UA)
                        .header("Referer", baseUrl)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        Jsoup.parse(body, iframeUrl)
                    }
                }
            }
        }
    }

    private suspend fun processIframe(iframeUrl: String, referer: String): List<Video.Server> {
        return try {
            val resolvedEmbed = absUrl(iframeUrl)
            // Already a third-party stream host — return as a server directly.
            if (!isProviderUrl(resolvedEmbed) &&
                !resolvedEmbed.contains("/vidurl/", ignoreCase = true) &&
                !resolvedEmbed.contains("dataLink", ignoreCase = true) &&
                Regex("""\.(m3u8|mp4)(\?|$)|/(e|embed|v)/""", RegexOption.IGNORE_CASE).containsMatchIn(resolvedEmbed)
            ) {
                val hostName = resolvedEmbed.substringAfter("//").substringBefore("/")
                    .replace("www.", "").substringBefore(".")
                    .replaceFirstChar { it.uppercase() }
                return listOf(Video.Server(id = resolvedEmbed, name = hostName, src = resolvedEmbed))
            }

            val iframeDoc = fetchEmbedDocument(resolvedEmbed)
            val iframeHtml = iframeDoc.html()
            val servers = mutableListOf<Video.Server>()

            var aesKey: ByteArray? = null
            try {
                val challenge = Regex("""(?:const|let|var)\s+POW_CHALLENGE\s*=\s*['"]([^'"]+)['"]""")
                    .find(iframeHtml)?.groupValues?.get(1)
                val difficulty = Regex("""(?:const|let|var)\s+POW_DIFFICULTY\s*=\s*(\d+)""")
                    .find(iframeHtml)?.groupValues?.get(1)?.toIntOrNull()
                val salt = Regex("""(?:const|let|var)\s+POW_SALT\s*=\s*['"]([^'"]+)['"]""")
                    .find(iframeHtml)?.groupValues?.get(1)

                if (challenge != null && difficulty != null && salt != null) {
                    aesKey = solvePoW(challenge, difficulty, salt)
                }
            } catch (_: Exception) {
                // PoW solving error
            }

            try {
                val dataLinkMatch = Regex("""dataLink\s*=\s*(\[[\s\S]+?\]);""").find(iframeHtml)
                if (dataLinkMatch != null) {
                    val items = json.decodeFromString<List<Item>>(dataLinkMatch.groupValues[1])
                    for (item in items) {
                        val lang = when (item.video_language) {
                            "LAT" -> "[LAT]"
                            "ESP" -> "[CAST]"
                            "SUB" -> "[SUB]"
                            "JAP" -> "[JAP]"
                            else -> ""
                        }
                        for (embed in item.sortedEmbeds) {
                            if (embed.servername.equals("download", ignoreCase = true)) continue

                            val looksLikeJwt = embed.link.count { it == '.' } == 2 &&
                                embed.link.split(".").all { part -> part.isNotBlank() && !part.contains("/") }
                            val decryptedLink = if (looksLikeJwt) {
                                decodeBase64Link(embed.link)
                            } else if (aesKey != null) {
                                decryptAES(embed.link, aesKey)
                            } else {
                                decodeBase64Link(embed.link)
                            }

                            if (decryptedLink != null && decryptedLink.startsWith("http")) {
                                servers.add(
                                    Video.Server(
                                        id = decryptedLink,
                                        name = "${embed.servername} $lang".trim(),
                                        src = decryptedLink,
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) { /* JSON error - continue */ }

            try {
                val domItems = iframeDoc.select(".ODDIV .OD_1 li[onclick], li[onclick*=go_to_player]")
                for (dom in domItems) {
                    val onclick = dom.attr("onclick")
                    val m = Regex("""go_to_player(?:Vast)?\(\s*'([^']+)'""").find(onclick)
                    val finalUrl = m?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    if (finalUrl.isBlank()) continue
                    val resolvedUrl = absUrl(finalUrl)
                    val serverName = dom.selectFirst("span")?.text()?.trim().orEmpty()
                    if (serverName.equals("1fichier", ignoreCase = true) || serverName.equals("download", ignoreCase = true)) continue
                    if (resolvedUrl.endsWith(".xml", ignoreCase = true)) continue
                    if (servers.none { it.id == resolvedUrl }) {
                        servers.add(Video.Server(id = resolvedUrl, name = serverName.ifBlank { "Opción" }, src = resolvedUrl))
                    }
                }
            } catch (_: Exception) { /* DOM error - continue */ }

            iframeDoc.selectFirst("iframe[src], iframe[data-src]")?.let { iframe ->
                val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }.takeIf { it.isNotEmpty() }
                src?.let {
                    val resolved = absUrl(it)
                    val name = resolved.substringAfter("//").substringBefore("/").replace("www.", "")
                        .substringBefore(".").replaceFirstChar { c -> c.uppercase() }
                    if (servers.none { s -> s.id == resolved }) {
                        servers.add(Video.Server(id = resolved, name = name, src = resolved))
                    }
                }
            }

            // If nothing nested was found but the URL itself looks playable, keep it.
            if (servers.isEmpty() && resolvedEmbed.startsWith("http") && !isProviderUrl(resolvedEmbed)) {
                val name = resolvedEmbed.substringAfter("//").substringBefore("/").replace("www.", "")
                    .substringBefore(".").replaceFirstChar { it.uppercase() }
                servers.add(Video.Server(id = resolvedEmbed, name = name, src = resolvedEmbed))
            }

            servers
        } catch (e: Exception) {
            Log.w(TAG, "processIframe failed for $iframeUrl: ${e.message}")
            emptyList()
        }
    }

    private fun decodeBase64Link(encryptedLink: String): String? {
        return try {
            // Encrypted link has format: header.payload.signature
            val parts = encryptedLink.split(".")
            if (parts.size != 3) return null
            
            // Decode the payload (middle part) from base64
            var payloadB64 = parts[1]
            
            // Add padding if necessary
            val missingPadding = payloadB64.length % 4
            if (missingPadding != 0) {
                payloadB64 += "=".repeat(4 - missingPadding)
            }
            
            // Decode base64 payload
            val payloadJson = String(Base64.decode(payloadB64, Base64.DEFAULT))
            
            // Manual parsing for robustness
            val linkStart = payloadJson.indexOf("\"link\":\"")
            if (linkStart == -1) return null
            val valueStart = linkStart + 8
            val valueEnd = payloadJson.indexOf("\"", valueStart)
            if (valueEnd == -1) return null
            payloadJson.substring(valueStart, valueEnd)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.src.ifBlank { server.id }, server)
    }

    override suspend fun getPeople(id: String, page: Int): People {
        if (page > 1) {
            return People(id = id, name = "")
        }
        return try {
            val document = getDocument(id)
            val name = document.selectFirst(".data h1")?.text() ?: ""
            val poster = document.selectFirst(".poster img")?.attr("src")
            val filmography = parseMixed(document)
            People(
                id = id,
                name = name,
                image = poster?.let { if (it.startsWith("http")) it else "$baseUrl$it" },
                filmography = filmography
            )
        } catch (e: Exception) {
            throw e
        }
    }
}