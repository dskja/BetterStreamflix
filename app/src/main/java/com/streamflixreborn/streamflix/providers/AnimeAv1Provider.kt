package com.streamflixreborn.streamflix.providers

import android.util.Log
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.models.*
import com.streamflixreborn.streamflix.utils.DnsResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import okhttp3.ResponseBody
import okhttp3.Request
import retrofit2.Response
import retrofit2.http.Headers
import java.io.File
import java.util.concurrent.TimeUnit

object AnimeAv1Provider : Provider {

    override val name = "AnimeAV1"
    override val baseUrl = "https://animeav1.com"
    override val language = "es"
    override val logo = "https://animeav1.com/favicon.png"

    private val client = getOkHttpClient()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(JsoupConverterFactory.create())
        .client(client)
        .build()

    private val service = retrofit.create(AnimeAv1Service::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun getOkHttpClient(): OkHttpClient {
        val appCache = Cache(File("cacheDir", "okhttpcache"), 10 * 1024 * 1024)

        val clientBuilder = OkHttpClient.Builder()
            .cache(appCache)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)

        return clientBuilder.dns(DnsResolver.doh).build()
    }

    private fun getEpisodePoster(animeId: String, episodeNum: String): String {
        return "https://cdn.animeav1.com/screenshots/$animeId/$episodeNum.jpg"
    }

    private interface AnimeAv1Service {
        @GET
        suspend fun getPage(@Url url: String): Document

        @Headers(
            "User-Agent: Mozilla/5.0",
            "Referer: https://animeav1.com/"
        )
        @GET
        suspend fun getRaw(@Url url: String): Response<ResponseBody>

        @GET("catalogo")
        suspend fun search(@Query("search") query: String, @Query("page") page: Int): Document

        @GET("catalogo")
        suspend fun getTvShows(@Query("order") order: String = "score", @Query("page") page: Int): Document

        @GET("catalogo")
        suspend fun getMovies(@Query("category") type: String = "pelicula", @Query("page") page: Int): Document

        @GET("catalogo")
        suspend fun getGenre(@Query("genre") genre: String, @Query("page") page: Int): Document

        @GET("media/{id}")
        suspend fun getShowDetails(@Path("id") id: String): Document
    }

    override suspend fun getHome(): List<Category> {
        return try {
            coroutineScope {
                val homeDeferred = async { service.getPage(baseUrl) }
                val addedDeferred = async { service.getPage("$baseUrl/catalogo?order=latest_added&page=1") }
                val airingDeferred = async { service.getPage("$baseUrl/catalogo?status=emision") }

                val categories = mutableListOf<Category>()

                try {
                    val addedDocument = addedDeferred.await()
                    val bannerShows = addedDocument.select("article").mapNotNull { element ->
                        val url = element.selectFirst("a[href]")?.attr("href") ?: return@mapNotNull null
                        val posterUrl = element.selectFirst("img")?.attr("src")
                        val finalPoster = if (posterUrl?.startsWith("http") == true) posterUrl else posterUrl?.let { "$baseUrl$it" }

                        TvShow(
                            id = url.substringAfterLast("/"),
                            title = element.selectFirst("h3")?.text() ?: "",
                            banner = finalPoster
                        )
                    }
                    if (bannerShows.isNotEmpty()) {
                        categories.add(Category(Category.FEATURED, bannerShows))
                    }
                } catch (e: Exception) { /* No-op */ }

                try {
                    val homeDocument = homeDeferred.await()
                    val sectionEpisodes = homeDocument.select("section").firstOrNull { it.selectFirst("h2")?.text()?.contains("Episodios", ignoreCase = true) == true }
                    val latestEpisodes = sectionEpisodes?.select("article")
                        ?.mapNotNull { element ->
                            val linkElement = element.selectFirst("a[href]") ?: return@mapNotNull null
                            val fullUrl = linkElement.attr("href")

                            if (!fullUrl.contains("/media/")) return@mapNotNull null
                            val showUrl = fullUrl.substringBeforeLast("/")
                            val imageUrl = element.selectFirst("img")?.attr("src")
                            TvShow(
                                id = showUrl.substringAfterLast("/"),
                                title = element.selectFirst("h3, header div")?.text()?.trim().orEmpty(),
                                poster = imageUrl?.replace("thumbnails", "covers")
                            )
                        }
                        ?.distinctBy { it.id }
                    if (latestEpisodes?.isNotEmpty() == true ) {
                        categories.add(Category("Últimos Episodios", latestEpisodes))
                    }
                } catch (e: Exception) { /* No-op */ }

                try {
                    val airingDocument = airingDeferred.await()
                    val airingShows = airingDocument.select("article").mapNotNull { element ->
                        val url = element.selectFirst("a[href]")?.attr("href") ?: return@mapNotNull null
                        val posterUrl = element.selectFirst("img")?.attr("src")
                        val finalPoster = if (posterUrl?.startsWith("http") == true) posterUrl else posterUrl?.let { "$baseUrl$it" }

                        TvShow(
                            id = url.substringAfterLast("/"),
                            title = element.selectFirst("h3")?.text() ?: "",
                            poster = finalPoster
                        )
                    }
                    if (airingShows.isNotEmpty()) {
                        categories.add(Category("Animes en Emisión", airingShows))
                    }
                } catch (e: Exception) { /* No-op */ }

                return@coroutineScope categories
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            return listOf(
                Genre("accion", "Acción"),
                Genre("antropomorfico", "Antropomórfico"),
                Genre("artes-marciales", "Artes Marciales"),
                Genre("aventuras", "Aventuras"),
                Genre("carreras", "Carreras"),
                Genre("ciencia-ficcion", "Ciencia Ficción"),
                Genre("comedia", "Comedia"),
                Genre("deportes", "Deportes"),
                Genre("detectives", "Detectives"),
                Genre("drama", "Drama"),
                Genre("ecchi", "Ecchi"),
                Genre("elenco-adulto", "Elenco Adulto"),
                Genre("escolares", "Escolares"),
                Genre("espacial", "Espacial"),
                Genre("fantasia", "Fantasía"),
                Genre("gore", "Gore"),
                Genre("gourmet", "Gourmet"),
                Genre("harem", "Harem"),
                Genre("historico", "Histórico"),
                Genre("idols-hombre", "Idols (Hombre)"),
                Genre("idols-mujer", "Idols (Mujer)"),
                Genre("infantil", "Infantil"),
                Genre("isekai", "Isekai"),
                Genre("josei", "Josei"),
                Genre("juegos-estrategia", "Juegos Estrategia"),
                Genre("mahou-shoujo", "Mahou Shoujo"),
                Genre("mecha", "Mecha"),
                Genre("militar", "Militar"),
                Genre("misterio", "Misterio"),
                Genre("mitologia", "Mitología"),
                Genre("musica", "Música"),
                Genre("parodia", "Parodia"),
                Genre("psicologico", "Psicológico"),
                Genre("recuentos-de-la-vida", "Recuentos de la vida"),
                Genre("romance", "Romance"),
                Genre("samurai", "Samurai"),
                Genre("seinen", "Seinen"),
                Genre("shoujo", "Shoujo"),
                Genre("shoujo-ai", "Shoujo Ai"),
                Genre("shounen", "Shounen"),
                Genre("shounen-ai", "Shounen Ai"),
                Genre("sobrenatural", "Sobrenatural"),
                Genre("superpoderes", "Superpoderes"),
                Genre("suspenso", "Suspenso"),
                Genre("terror", "Terror"),
                Genre("vampiros", "Vampiros")
            )
        }

        return try {
            if (page > 1) return emptyList()

            val document = service.search(query, page)

            document.select("article").mapNotNull { element ->
                val url = element.selectFirst("a[href]")?.attr("href") ?: return@mapNotNull null
                val id = url.substringAfterLast("/")
                val title = element.selectFirst("h3")?.text() ?: ""
                val posterUrl = element.selectFirst("img")?.attr("src")
                val type = element.selectFirst("div.bg-line")?.text()?.trim()

                val finalPoster = if (posterUrl?.startsWith("http") == true) {
                    posterUrl
                } else {
                    posterUrl?.let { "$baseUrl$it" }
                }

                if (type == "Película") {
                    Movie(id = id, title = title, poster = finalPoster)
                } else {
                    TvShow(id = id, title = title, poster = finalPoster)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return try {
            val document = service.getTvShows(page = page)
            document.select("article").mapNotNull { element ->
                val url = element.selectFirst("a[href]")?.attr("href") ?: return@mapNotNull null
                val posterUrl = element.selectFirst("img")?.attr("src")
                val finalPoster = if (posterUrl?.startsWith("http") == true) posterUrl else posterUrl?.let { "$baseUrl$it" }

                TvShow(
                    id = url.substringAfterLast("/"),
                    title = element.selectFirst("h3")?.text() ?: "",
                    poster = finalPoster
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return try {
            val document = service.getMovies(page = page)
            document.select("article").mapNotNull { element ->
                val url = element.selectFirst("a[href]")?.attr("href") ?: return@mapNotNull null
                val posterUrl = element.selectFirst("img")?.attr("src")
                val finalPoster = if (posterUrl?.startsWith("http") == true) posterUrl else posterUrl?.let { "$baseUrl$it" }

                Movie(
                    id = url.substringAfterLast("/"),
                    title = element.selectFirst("h3")?.text() ?: "",
                    poster = finalPoster
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTvShow(id: String): TvShow {
        return try {
            val document = AnimeAv1Provider.service.getShowDetails(id)

            val title = document.selectFirst("h1")?.text() ?: ""
            val overview = document.selectFirst("div.entry p")?.text()
            val poster = document.selectFirst("img[src*=/covers/]")?.attr("src")
            val rating = document.selectFirst("div.text-2xl.font-bold")?.text()?.toDoubleOrNull()
            val genres = document.select("a[href*=/catalogo?genre=]").map {
                Genre(id = it.attr("href").substringAfterLast("/"), name = it.text())
            }

            val episodes = document
                .select("section:has(h2:contains(Episodios)) article")
                .mapIndexedNotNull { index, element ->

                    val url = element.selectFirst("a[href*=/media/]")?.attr("href")
                        ?: return@mapIndexedNotNull null

                    val number = element
                        .selectFirst("div.bg-line span")
                        ?.text()
                        ?.toIntOrNull()
                        ?: (index + 1)

                    val poster = element.selectFirst("img")?.attr("src")

                    Episode(
                        id = url.substringAfter("/media/"),
                        number = number,
                        title = "Episodio $number",
                        poster = poster
                    )
                }


            val seasons = listOf(
                Season(
                    id = id,
                    number = 1,
                    title = "Episodios",
                    episodes = episodes,
                    poster = if (poster?.startsWith("http") == true) poster else poster?.let { "${AnimeAv1Provider.baseUrl}$it" }
                )
            )

            TvShow(
                id = id,
                title = title,
                overview = overview,
                poster = if (poster?.startsWith("http") == true) poster else poster?.let { "${AnimeAv1Provider.baseUrl}$it" },
                rating = rating,
                genres = genres,
                seasons = seasons
            )
        } catch (e: Exception) {
            TvShow(id = id, title = "Error al cargar")
        }
    }

    override suspend fun getMovie(id: String): Movie {
        val show = getTvShow(id)
        return Movie(
            id = show.id,
            title = show.title,
            overview = show.overview,
            poster = show.poster,
            rating = show.rating,
            genres = show.genres,
            cast = emptyList(),
            recommendations = emptyList()
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val show = getTvShow(seasonId)
        return show.seasons.firstOrNull()?.episodes ?: emptyList()
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val genreName = id.replace("-", " ").replaceFirstChar { it.uppercase() }
        return try {
            val document = service.getGenre(genre = id, page = page)

            val shows = document.select("article").mapNotNull { element ->
                val url = element.selectFirst("a[href]")?.attr("href") ?: return@mapNotNull null
                val showId = url.substringAfterLast("/")
                val title = element.selectFirst("h3")?.text() ?: ""
                val posterUrl = element.selectFirst("img")?.attr("src")
                val type = element.selectFirst("div.bg-line")?.text()

                val finalPoster = if (posterUrl?.startsWith("http") == true) posterUrl else posterUrl?.let { "$baseUrl$it" }

                if (type == "Película") {
                    Movie(id = showId, title = title, poster = finalPoster)
                } else {
                    TvShow(id = showId, title = title, poster = finalPoster)
                }
            }

            Genre(id = id, name = genreName, shows = shows)
        } catch (e: Exception) {
            Genre(id = id, name = genreName, shows = emptyList())
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        throw Exception("Not implemented for this provider")
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return try {
            val url = "$baseUrl/media/$id"
            Log.d("AnimeAV1", "Id: $id")
            Log.d("AnimeAV1", "URL: $url")
            val jsonUrl = "$url/__data.json"
            Log.d("AnimeAV1", "jsonURL: $jsonUrl")

            val response = service.getRaw(jsonUrl)
            val jsonText = response.body()?.string() ?: return emptyList()

            Log.d("AnimeAV1", "Tiene embeds: ${jsonText.contains("embeds")}")
            Log.d("AnimeAV1", "JSON preview: $jsonText")

            val servers = mutableListOf<Video.Server>()

            val root = org.json.JSONObject(jsonText)
            val nodes = root.getJSONArray("nodes")

            // Nodo donde vive episode
            val episodeNode = nodes.getJSONObject(3)
            val dataArray = episodeNode.getJSONArray("data")

            val episodeObj = dataArray.getJSONObject(0)
            val embedsIndex = episodeObj.getInt("embeds")

            val embeds = dataArray.getJSONObject(embedsIndex)

            val subIndex = embeds.optInt("SUB", -1)
            val dubIndex = embeds.optInt("DUB", -1)

            fun resolveList(index: Int): org.json.JSONArray? {
                return if (index >= 0) dataArray.optJSONArray(index) else null
            }

            fun resolveServer(objIndex: Int): Pair<String, String>? {
                return try {
                    val obj = dataArray.getJSONObject(objIndex)
                    val serverName = dataArray.getString(obj.getInt("server"))
                    val url = dataArray.getString(obj.getInt("url"))
                    serverName to url
                } catch (e: Exception) {
                    null
                }
            }

            fun extractFromIndex(index: Int, type: String) {
                val list = resolveList(index) ?: return

                for (i in 0 until list.length()) {
                    val objIndex = list.optInt(i, -1)
                    if (objIndex == -1) continue

                    val result = resolveServer(objIndex) ?: continue
                    val (name, videoUrl) = result

                    // Omitir servidores que no funcionan correctamente (Mega y MP4Upload)
                    if (name.contains("Mega", ignoreCase = true) || name.contains("MP4Upload", ignoreCase = true)) {
                        continue
                    }

                    if (videoUrl.startsWith("http")) {
                        servers.add(
                            Video.Server(
                                id = videoUrl,
                                name = "$name ($type)"
                            )
                        )
                    }
                }
            }

            extractFromIndex(subIndex, "SUB")
            extractFromIndex(dubIndex, "DUB")

            Log.d("AnimeAV1", "Servers: $servers")

            servers.distinctBy { it.id }

        } catch (e: Exception) {
            Log.e("AnimeAV1", "ERROR", e)
            emptyList()
        }
    }



    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.id, server)
    }
}

private fun String.body() {
    TODO("Not yet implemented")
}
