package com.streamflixreborn.streamflix.providers

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.models.*
import com.streamflixreborn.streamflix.utils.DnsResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.io.File
import java.util.concurrent.TimeUnit
import android.util.Log

object AnimefenixProvider : Provider {

    override val name = "Animefenix"
    override val baseUrl = "https://animefenix2.tv"
    override val language = "es"
    override val logo = "https://animefenix2.tv/themes/fenix-neo/images/AveFenix.png"

    private val client = getOkHttpClient()

    private val service = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(JsoupConverterFactory.create())
        .client(client)
        .build()
        .create(AnimefenixService::class.java)

    private fun getOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .cache(Cache(File("cacheDir", "okhttpcache"), 10 * 1024 * 1024))
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .dns(DnsResolver.doh)
            .build()
    }

    private interface AnimefenixService {
        @GET
        suspend fun getPage(@Url url: String): Document
    }

    private fun parseShows(elements: List<Element>): List<TvShow> {
        return elements.mapNotNull {
            val a = it.selectFirst("a") ?: it
            val titleElement = it.selectFirst("h3, p:not(.gray)")
            val imageElement = it.selectFirst("img")

            TvShow(
                id = a.attr("href"),
                title = titleElement?.text() ?: "",
                poster = imageElement?.let { img ->
                    img.attr("data-src").ifEmpty { img.attr("src") }
                }
            )
        }
    }

    private fun parseMovies(elements: List<Element>): List<Movie> {
        return elements.mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val posterElement = it.selectFirst(".main-img img")
            Movie(
                id = a.attr("href"),
                title = it.selectFirst("p:not(.gray)")?.text() ?: "",
                poster = posterElement?.attr("data-src")?.ifEmpty { posterElement.attr("src") }
            )
        }
    }

    override suspend fun getHome(): List<Category> {
        return try {
            coroutineScope {
                val premieres2025Deferred = async { service.getPage("$baseUrl/directorio/anime?estreno=2025") }
                val premieres2024Deferred = async { service.getPage("$baseUrl/directorio/anime?estreno=2024") }
                val premieres2023Deferred = async { service.getPage("$baseUrl/directorio/anime?estreno=2023") }

                val categories = mutableListOf<Category>()

                try {
                    val premieres2025Document = premieres2025Deferred.await()
                    val bannerShows = parseShows(premieres2025Document.select(".grid-animes li article")).map {
                        it.copy(banner = it.poster)
                    }
                    if (bannerShows.isNotEmpty()) {
                        categories.add(Category(Category.FEATURED, bannerShows.take(10)))
                    }
                } catch (e: Exception) { /* No-op */ }

                try {
                    val premieres2024Document = premieres2024Deferred.await()
                    val premieres2024Shows = parseShows(premieres2024Document.select(".grid-animes li article"))
                    if (premieres2024Shows.isNotEmpty()) {
                        categories.add(Category("Estrenos 2024", premieres2024Shows))
                    }
                } catch (e: Exception) { /* No-op */ }

                try {
                    val premieres2023Document = premieres2023Deferred.await()
                    val premieres2023Shows = parseShows(premieres2023Document.select(".grid-animes li article"))
                    if (premieres2023Shows.isNotEmpty()) {
                        categories.add(Category("Estrenos 2023", premieres2023Shows))
                    }
                } catch (e: Exception) { /* No-op */ }

                return@coroutineScope categories
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            return listOf(
                Genre("1", "Acción"), Genre("23", "Aventuras"), Genre("20", "Ciencia Ficción"),
                Genre("5", "Comedia"), Genre("8", "Deportes"), Genre("38", "Demonios"),
                Genre("6", "Drama"), Genre("11", "Ecchi"), Genre("2", "Escolares"),
                Genre("13", "Fantasía"), Genre("28", "Harem"), Genre("24", "Historico"),
                Genre("47", "Horror"), Genre("25", "Infantil"), Genre("51", "Isekai"),
                Genre("29", "Josei"), Genre("14", "Magia"), Genre("26", "Artes Marciales"),
                Genre("21", "Mecha"), Genre("22", "Militar"), Genre("17", "Misterio"),
                Genre("36", "Música"), Genre("30", "Parodia"), Genre("31", "Policía"),
                Genre("18", "Psicológico"), Genre("10", "Recuentos de la vida"), Genre("3", "Romance"),
                Genre("34", "Samurai"), Genre("7", "Seinen"), Genre("4", "Shoujo"),
                Genre("9", "Shounen"), Genre("12", "Sobrenatural"), Genre("15", "Superpoderes"),
                Genre("19", "Suspenso"), Genre("27", "Terror"), Genre("39", "Vampiros"),
                Genre("40", "Yaoi"), Genre("37", "Yuri")
            )
        }
        return try {
            val document = service.getPage("$baseUrl/directorio/anime?q=$query&p=$page")
            parseShows(document.select(".grid-animes li article")).distinctBy { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return try {
            val document = service.getPage("$baseUrl/directorio/anime?p=$page")
            parseShows(document.select(".grid-animes li article"))
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return try {
            val document = service.getPage("$baseUrl/directorio/anime?tipo=2&p=$page")
            parseMovies(document.select(".grid-animes li article"))
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            val document = service.getPage("$baseUrl/directorio/anime?genero=$id&p=$page")
            val shows = parseShows(document.select(".grid-animes li article"))
            val genreName = document.selectFirst("h1.text-4xl")?.ownText()?.trim() ?: "Género"
            Genre(id = id, name = genreName, shows = shows)
        } catch (e: Exception) {
            Genre(id = id, name = "Error", shows = emptyList())
        }
    }

    override suspend fun getMovie(id: String): Movie {
        return try {
            val document = service.getPage(id)
            val title = document.selectFirst("h1.text-4xl")?.ownText() ?: ""
            val poster = document.selectFirst("#anime_image")?.let {
                it.attr("data-src").ifEmpty { it.attr("src") }
            }
            val overview = document.selectFirst(".mb-6 p.text-gray-300")?.text()
            val genres = document.select("a.bg-gray-800").map {
                Genre(
                    id = it.attr("href").substringAfterLast("/"),
                    name = it.text()
                )
            }
            Movie(id = id, title = title, poster = poster, overview = overview, genres = genres)
        } catch (e: Exception) {
            Movie(id = id, title = "Error al cargar")
        }
    }

    override suspend fun getTvShow(id: String): TvShow {
        return try {
            val document = service.getPage(id)
            val title = document.selectFirst("h1.text-4xl")?.ownText() ?: ""
            val poster = document.selectFirst("#anime_image")?.let {
                it.attr("data-src").ifEmpty { it.attr("src") }
            }

            // 1. Mejora en la sinopsis para el nuevo tema Neo
            val overview = document.selectFirst("h2:contains(Sinopsis) + p")?.text()
                ?: document.selectFirst(".mb-6 p.text-gray-300")?.text()

            // 2. Mejora en los géneros
            val genres = document.select("a[href*=/directorio/anime?genero=]").map {
                Genre(id = it.attr("href").substringAfterLast("/"), name = it.text().trim())
            }

            // 3. Extracción de episodios AJAX usando la técnica de Corrutinas
            val episodes = mutableListOf<Episode>()
            val slug = id.substringAfterLast("/")

            // Buscamos los botones de las páginas (Ej: "1 - 50", "51 - 100")
            val episodeButtons = document.select(".episode-navigation button.episode-btn")
            val startValues = if (episodeButtons.isNotEmpty()) {
                episodeButtons.mapNotNull { btn ->
                    Regex("""loadEpisodes\((\d+)""").find(btn.attr("onclick"))?.groupValues?.get(1)
                }.distinct()
            } else {
                listOf("0") // Valor por defecto si solo hay una página
            }

            // Descargamos las páginas en paralelo
            coroutineScope {
                val deferredEpisodes = startValues.map { start ->
                    async {
                        try {
                            // Imita la llamada AJAX de la página web
                            val ajaxUrl = "$id?id=$slug&load=episodes&start=$start"
                            val epDoc = service.getPage(ajaxUrl)

                            epDoc.select(".episode-card").mapNotNull { epEl ->
                                val rawHref = epEl.attr("href")
                                if (rawHref.isNullOrBlank()) return@mapNotNull null
                                val epUrl = if (rawHref.startsWith("http")) rawHref else "$baseUrl$rawHref"

                                val epTitle = epEl.selectFirst(".ep-title")?.text()?.trim() ?: "Episodio"
                                val epImg = epEl.selectFirst("img")?.let { img ->
                                    img.attr("data-src").ifEmpty { img.attr("src") }
                                } ?: ""
                                val epNum = Regex("""\d+""").find(epTitle)?.value?.toIntOrNull() ?: 0

                                Episode(
                                    id = epUrl,
                                    number = epNum,
                                    title = epTitle,
                                    poster = epImg
                                )
                            }
                        } catch (e: Exception) {
                            emptyList<Episode>()
                        }
                    }
                }

                deferredEpisodes.map { it.await() }.forEach { episodes.addAll(it) }
            }

            // Aseguramos el orden correcto (menor a mayor)
            episodes.sortBy { it.number }

            TvShow(
                id = id,
                title = title,
                poster = poster,
                overview = overview,
                genres = genres,
                seasons = listOf(Season(id = id, number = 1, title = "Episodios", episodes = episodes))
            )
        } catch (e: Exception) {
            TvShow(id = id, title = "Error al cargar")
        }
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        return try {
            getTvShow(seasonId).seasons.firstOrNull()?.episodes ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return try {
            val url = if (videoType is Video.Type.Movie) {
                val moviePage = service.getPage(id)
                moviePage.selectFirst(".divide-y li > a")?.attr("href") ?: id
            } else {
                id
            }

            val document = service.getPage(url)
            val script = document.selectFirst("script:containsData(var tabsArray)") ?: return emptyList()

            val names = document.select(".episode-page__servers-list li a").map { a ->
                a.select("span").last()?.text()?.trim().orEmpty()
            }

            val urls = script.data()
                .substringAfter("<iframe").split("src='")
                .drop(1)
                .map { it.substringBefore("'").substringAfter("redirect.php?id=").trim() }

            val count = minOf(urls.size, names.size)
            val servers = (0 until count).mapNotNull { i ->
                val name = names[i].ifBlank { return@mapNotNull null }
                val urlValue = urls[i]
                Video.Server(id = urlValue, name = name)
            }

            servers
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.id, server)
    }

    override suspend fun getPeople(id: String, page: Int): People {
        throw Exception("Esta función no está disponible en AnimeFenix")
    }
}