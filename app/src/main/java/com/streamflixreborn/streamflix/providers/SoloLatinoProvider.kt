package com.streamflixreborn.streamflix.providers

import android.util.Base64
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.models.*
import com.streamflixreborn.streamflix.models.sololatino.Item
import com.streamflixreborn.streamflix.utils.DnsResolver
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.io.File
import java.util.concurrent.TimeUnit
// import javax.crypto.Cipher
// import javax.crypto.spec.IvParameterSpec
// import javax.crypto.spec.SecretKeySpec

object SoloLatinoProvider : Provider {

    override val name = "SoloLatino"
    override val baseUrl = "https://sololatino.net"
    override val language = "es"

    private val client = getOkHttpClient()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(JsoupConverterFactory.create())
        .client(client)
        .build()

    private val service = retrofit.create(SoloLatinoService::class.java)

    private fun getOkHttpClient(): OkHttpClient {
        val appCache = Cache(File("cacheDir", "okhttpcache"), 10 * 1024 * 1024)

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    .build()
                chain.proceed(request)
            }
            .cache(appCache)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)

        return clientBuilder.dns(DnsResolver.doh).build()
    }

    private interface SoloLatinoService {
        @GET
        suspend fun getPage(@Url url: String): Document

    }

    override val logo = "$baseUrl/images/logo.png"

    override suspend fun getHome(): List<Category> = coroutineScope {
        val categories = mutableListOf<Category>()

        try {
            val mainDoc = service.getPage(baseUrl)
            
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
        } catch (e: Exception) { /* Ignore */ }

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
            val document = service.getPage(url)
            parseMixed(document)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return try {
            val url = if (page > 1) "$baseUrl/peliculas/page/$page" else "$baseUrl/peliculas"
            val document = service.getPage(url)
            parseMovies(document)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return try {
            val url = if (page > 1) "$baseUrl/series/page/$page" else "$baseUrl/series"
            val document = service.getPage(url)
            parseTvShows(document)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            val url = if (page > 1) "$baseUrl/genero/$id/page/$page" else "$baseUrl/genero/$id"
            val document = service.getPage(url)
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
            val document = service.getPage(id)
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
            val document = service.getPage(id)
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
            val document = service.getPage(showId)
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
            val doc = service.getPage(id)
            val allServers = mutableListOf<Video.Server>()
            
            val serverBtns = doc.select("button.server-btn")
            for (btn in serverBtns) {
                val serverUrl = btn.attr("data-server-url")
                if (serverUrl.isNotEmpty()) {
                    val nested = processIframe(serverUrl, id)
                    allServers.addAll(nested)
                }
            }
            
            allServers.distinctBy { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun processIframe(iframeUrl: String, referer: String): List<Video.Server> {
        return try {
            val iframeDoc = service.getPage(iframeUrl)
            val iframeHtml = iframeDoc.html()
            val servers = mutableListOf<Video.Server>()

            // 1. DataLink case
            try {
                val dataLinkMatch = Regex("""dataLink = (\[.+?\]);""").find(iframeHtml)
                if (dataLinkMatch != null) {
                    val items = json.decodeFromString<List<Item>>(dataLinkMatch.groupValues[1])
                    for (item in items) {
                        val lang = when(item.video_language) {
                            "LAT" -> "[LAT]"
                            "ESP" -> "[CAST]"
                            "SUB" -> "[SUB]"
                            "JAP" -> "[JAP]"
                            else -> ""
                        }
                        for (embed in item.sortedEmbeds) {
                            if (embed.servername.equals("download", ignoreCase = true)) continue
                            val decryptedLink = decodeBase64Link(embed.link) ?: continue
                            servers.add(Video.Server(id = decryptedLink, name = "${embed.servername} $lang".trim()))
                        }
                    }
                }
            } catch (e: Exception) { /* JSON error - continue */ }

            // 2. DOM-base
            try {
                val domItems = iframeDoc.select(".ODDIV .OD_1 li[onclick]")
                for (dom in domItems) {
                    val onclick = dom.attr("onclick")
                    val m = Regex("""go_to_playerVast\(\s*'([^']+)'""").find(onclick)
                    val finalUrl = m?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    if (finalUrl.isBlank()) continue
                    val serverName = dom.selectFirst("span")?.text()?.trim().orEmpty()
                    if (serverName.equals("1fichier", ignoreCase = true) || serverName.equals("download", ignoreCase = true)) continue
                    if (servers.none { it.id == finalUrl }) {
                        servers.add(Video.Server(id = finalUrl, name = serverName))
                    }
                }
            } catch (e: Exception) { /* DOM error - continue */ }

            // 3. Direct Iframe
            iframeDoc.selectFirst("iframe")?.attr("src")?.takeIf { it.isNotEmpty() }?.let { src ->
                val name = src.substringAfter("//").substringBefore("/").replace("www.", "").substringBefore(".").replaceFirstChar { it.uppercase() }
                if (servers.none { it.id == src }) {
                    servers.add(Video.Server(id = src, name = name))
                }
            }

            servers
        } catch (e: Exception) {
            emptyList()
        }
    }

    // private fun decryptAES(encrypted: String): String? {
    //     return try {
    //         val key = "Ak7qrvvH4WKYxV2OgaeHAEg2a5eh16vE".toByteArray()
    //         val decoded = Base64.decode(encrypted, Base64.DEFAULT)
    //         val iv = decoded.copyOfRange(0, 16)
    //         val cipherText = decoded.copyOfRange(16, decoded.size)
    //         val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    //         cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    //         String(cipher.doFinal(cipherText))
    //     } catch (e: Exception) {
    //         null
    //     }
    // }

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
        return Extractor.extract(server.id, server)
    }

    override suspend fun getPeople(id: String, page: Int): People {
        if (page > 1) {
            return People(id = id, name = "")
        }
        return try {
            val document = service.getPage(id)
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