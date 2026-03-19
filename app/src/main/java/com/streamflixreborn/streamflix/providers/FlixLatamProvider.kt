package com.streamflixreborn.streamflix.providers

import android.util.Log
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.models.*
import com.streamflixreborn.streamflix.models.flixlatam.DataLinkItem
import com.streamflixreborn.streamflix.models.flixlatam.PlayerResponse
// import com.streamflixreborn.streamflix.utils.CryptoAES
import android.util.Base64
import com.streamflixreborn.streamflix.utils.DnsResolver
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.Locale

object FlixLatamProvider : Provider {

    override val name = "FlixLatam"
    override val baseUrl = "https://flixlatam.com"
    override val language = "es"
    override val logo = "https://images2.imgbox.com/94/59/1ClPdx5Z_o.jpg"

    private val service = FlixLatamService.build(baseUrl)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getHome(): List<Category> = coroutineScope {
        try {
            val document = service.getPage(baseUrl, baseUrl)
            val categories = mutableListOf<Category>()

            val sections = document.select(".items")
            sections.forEach { section ->
                val title = section.selectFirst("header h2")?.text() ?: return@forEach
                val shows = parseShows(section.select("article"))
                if (shows.isNotEmpty()) {
                    categories.add(Category(title, shows))
                }
            }

            categories
        } catch (e: Exception) {
            Log.e("FlixLatamProvider", "Error en getHome: ${e.message}")
            emptyList()
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            return listOf(
                Genre(id = "generos/accion", name = "Acción"), Genre(id = "generos/animacion", name = "Animación"),
                Genre(id = "generos/aventura", name = "Aventura"), Genre(id = "generos/ciencia-ficcion", name = "Ciencia Ficción"),
                Genre(id = "generos/comedia", name = "Comedia"), Genre(id = "generos/crimen", name = "Crimen"),
                Genre(id = "generos/documental", name = "Documental"), Genre(id = "generos/drama", name = "Drama"),
                Genre(id = "generos/familia", name = "Familia"), Genre(id = "generos/fantasia", name = "Fantasía"),
                Genre(id = "generos/historia", name = "Historia"), Genre(id = "generos/kids", name = "Kids"),
                Genre(id = "generos/misterio", name = "Misterio"), Genre(id = "generos/musica", name = "Música"),
                Genre(id = "generos/romance", name = "Romance"), Genre(id = "generos/terror", name = "Terror"),
                Genre(id = "generos/western", name = "Western")
            )
        }
        if (page > 1) return emptyList()
        return try {
            val url = "$baseUrl/search?s=$query"
            val document = service.getPage(url, baseUrl)
            parseShows(document.select("article.item, div.result-item article, .items article"))
        } catch (e: Exception) {
            Log.e("FlixLatamProvider", "Error en search: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return try {
            val url = if (page == 1) "$baseUrl/peliculas/" else "$baseUrl/peliculas/?page=$page"
            val document = service.getPage(url, baseUrl)
            parseShows(document.select("div.items article")).filterIsInstance<Movie>()
        } catch (e: Exception) {
            Log.e("FlixLatamProvider", "Error en getMovies: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return try {
            val url = if (page == 1) "$baseUrl/series/" else "$baseUrl/series/?page=$page"
            val document = service.getPage(url, baseUrl)
            parseShows(document.select("div.items article")).filterIsInstance<TvShow>()
        } catch (e: Exception) {
            Log.e("FlixLatamProvider", "Error en getTvShows: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            val url = if (page == 1) "$baseUrl/$id/" else "$baseUrl/$id/?page=$page"
            val document = service.getPage(url, baseUrl)
            val shows = parseShows(document.select("div.items article, .items article"))
            val genreName = document.selectFirst("header h1")?.text()?.substringAfter("Genero:")?.trim()?.replaceFirstChar { it.uppercase() } ?: ""
            Genre(id = id, name = genreName, shows = shows)
        } catch (e: Exception) {
            Genre(id = id, name = id.replaceFirstChar { it.uppercase() })
        }
    }

    override suspend fun getMovie(id: String): Movie {
        return try {
            val url = "$baseUrl/$id/"
            val document = service.getPage(url, baseUrl)
            val details = parseShowDetails(document)
            Movie(
                id = id,
                title = document.selectFirst(".sheader .data h1")?.text() ?: "",
                poster = document.selectFirst(".sheader .poster img")?.attr("src"),
                banner = document.selectFirst("style:containsData(background-image)")?.data()?.getBackgroundImage(),
                overview = details.overview,
                rating = details.rating,
                released = details.released,
                genres = details.genres,
                cast = details.cast,
                recommendations = parseShows(document.select("#single_relacionados article"))
            )
        } catch (e: Exception) {
            Movie(id = id, title = "Error al cargar")
        }
    }

    override suspend fun getTvShow(id: String): TvShow {
        val cleanId = if (id.contains("/temporada/")) id.substringBefore("/temporada/") else id
        return try {
            val url = "$baseUrl/$cleanId/"
            val document = service.getPage(url, baseUrl)
            
            val details = parseShowDetails(document)

            val seasons = document.select("#seasons .se-c").mapNotNull { seasonElement ->
                val seasonNumberText = seasonElement.selectFirst(".se-q span.se-t")?.text()?.trim() ?: return@mapNotNull null
                val seasonNumber = seasonNumberText.replace("[^0-9]".toRegex(), "").toIntOrNull() ?: return@mapNotNull null
                Season(id = "$id|$seasonNumber", number = seasonNumber, title = "Temporada $seasonNumber")
            }

            TvShow(
                id = id,
                title = document.selectFirst(".sheader .data h1")?.text() ?: "",
                poster = document.selectFirst(".sheader .poster img")?.attr("src"),
                banner = document.selectFirst("style:containsData(background-image)")?.data()?.getBackgroundImage(),
                overview = details.overview,
                rating = details.rating,
                released = details.released,
                genres = details.genres,
                cast = details.cast,
                recommendations = parseShows(document.select("#single_relacionados article")),
                seasons = seasons
            )
        } catch (e: Exception) {
            TvShow(id = id, title = "Error al cargar")
        }
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        return try {
            val (rawShowId, seasonNumberStr) = seasonId.split('|')
            val showId = if (rawShowId.contains("/temporada/")) rawShowId.substringBefore("/temporada/") else rawShowId
            val url = "$baseUrl/$showId/"
            val document = service.getPage(url, baseUrl)

            val seasonElement = document.select("#seasons .se-c").find {
                val text = it.selectFirst(".se-q span.se-t")?.text()?.trim() ?: ""
                text == seasonNumberStr || text.replace("[^0-9]".toRegex(), "") == seasonNumberStr
            } ?: return emptyList()

            seasonElement.select(".se-a ul.episodios li").mapNotNull { episodeElement ->
                val a = episodeElement.selectFirst(".episodiotitle a") ?: return@mapNotNull null
                val href = a.attr("href")
                val posterUrl = episodeElement.selectFirst(".imagen img")?.attr("src")
                val title = a.text()
                val numberStr = episodeElement.selectFirst(".numerando")?.text()?.trim()?.split("-")?.getOrNull(1)?.trim()

                Episode(
                    id = href.getId(),
                    title = title,
                    number = numberStr?.toIntOrNull() ?: 0,
                    poster = posterUrl
                )
            }
        } catch (e: Exception) {
            Log.e("FlixLatamProvider", "Error en getEpisodesBySeason: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val servers = mutableListOf<Video.Server>()
        try {
            val url = "$baseUrl/$id/"
            val page = service.getPage(url, baseUrl)

            page.select("div.pframe iframe").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotEmpty()) {
                    servers.addAll(processIframe(src))
                }
            }
        } catch (e: Exception) {
            Log.e("FlixLatamProvider", "Error en getServers: ${e.message}", e)
        }
        return servers.distinctBy { it.id }
    }

    private suspend fun processIframe(embedUrl: String): List<Video.Server> {
        return try {
            val embedHeaders = mapOf("Referer" to baseUrl)
            val embedDocument = service.getEmbedPage(embedUrl, embedHeaders)
            
            // 1. DataLink case
            val scriptData = embedDocument.selectFirst("script:containsData(dataLink)")?.data() ?: ""
            val dataLinkJsonString = Regex("""dataLink\s*=\s*(\[.+?\]);""").find(scriptData)?.groupValues?.get(1)

            if (dataLinkJsonString != null) {
                return json.decodeFromString<List<DataLinkItem>>(dataLinkJsonString).flatMap { item ->
                    item.sortedEmbeds.mapNotNull { embed ->
                        if (embed.servername.equals("download", ignoreCase = true)) return@mapNotNull null
                        decodeBase64Link(embed.link)?.let { decryptedLink ->
                            Video.Server(
                                id = decryptedLink,
                                name = "${embed.servername.replaceFirstChar { it.titlecase(Locale.ROOT) }} [${item.video_language}]"
                            )
                        }
                    }
                }
            }
            
            // 2. go_to_playerVast Case
            val domItems = embedDocument.select(".ODDIV .OD_1 li[onclick]")
            val domServers = domItems.mapNotNull { dom ->
                val onclick = dom.attr("onclick")
                val m = Regex("""go_to_playerVast\(\s*'([^']+)'""").find(onclick)
                val finalUrl = m?.groupValues?.getOrNull(1)?.trim() ?: return@mapNotNull null
                val serverName = dom.selectFirst("span")?.text()?.trim() ?: "Opción"
                if (serverName.contains("download", ignoreCase = true)) return@mapNotNull null
                Video.Server(id = finalUrl, name = serverName)
            }
            
            if (domServers.isNotEmpty()) return domServers

            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getVideo(server: Video.Server): Video = Extractor.extract(server.id)

    override suspend fun getPeople(id: String, page: Int): People {
        throw Exception("Esta función no está disponible en FlixLatam")
    }

    private fun String.getId(): String = this.substringAfter(baseUrl).trim('/')
    private fun String.getBackgroundImage(): String? = this.substringAfter("url(").substringBefore(")")

    private data class ShowDetails(
        val overview: String?, val rating: Double?, val released: String?,
        val genres: List<Genre>, val cast: List<People>
    )

    private fun parseShowDetails(document: Document): ShowDetails {
        val overview = document.selectFirst(".wp-content p, .sbox .wp-content p")?.text()
        val ratingText = document.selectFirst(".rating-value, .srating [itemprop=ratingValue]")?.text() 
            ?: document.selectFirst(".rating-value, .srating .rating-value")?.text()
        val rating = ratingText?.substringBefore("/")?.replace("[^0-9.]".toRegex(), "")?.toDoubleOrNull()
        val released = document.selectFirst(".sheader .extra span.date, .extra span.date")?.text()

        val genres = document.select(".sgeneros a").map {
            Genre(id = it.attr("href").getId(), name = it.text())
        }
        val cast = document.select("#cast .persons .person").map {
            People(
                id = it.selectFirst("a")?.attr("href")?.getId() ?: "",
                name = it.selectFirst(".name a")?.text() ?: "",
                image = it.selectFirst(".img img")?.attr("src")
            )
        }
        return ShowDetails(overview, rating, released, genres, cast)
    }

    private fun parseShows(elements: List<Element>): List<Show> {
        return elements.mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val href = a.attr("href")
            val title = it.selectFirst("h3")?.text() ?: it.selectFirst(".title")?.text() ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.let { img -> img.attr("data-src").ifEmpty { img.attr("src") } }
            val id = href.getId()

            when {
                href.contains("/pelicula/") -> Movie(id = id, title = title, poster = poster)
                href.contains("/serie/") || href.contains("/series/") || href.contains("/anime/") -> TvShow(id = id, title = title, poster = poster)
                else -> null
            }
        }
    }

    private interface FlixLatamService {
        companion object {
            fun build(baseUrl: String): FlixLatamService {
                val okHttpClient = OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                            .build()
                        chain.proceed(request)
                    }
                    .cache(Cache(File("cacheDir", "okhttpcache"), 10 * 1024 * 1024))
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .dns(DnsResolver.doh)
                    .build()

                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(okHttpClient)
                    .build()
                    .create(FlixLatamService::class.java)
            }
        }

        @GET
        suspend fun getPage(@Url url: String, @Header("Referer") referer: String): Document


        @GET
        suspend fun getEmbedPage(@Url url: String, @HeaderMap headers: Map<String, String>): Document
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
            val payloadJson = String(android.util.Base64.decode(payloadB64, android.util.Base64.DEFAULT))
            
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
}