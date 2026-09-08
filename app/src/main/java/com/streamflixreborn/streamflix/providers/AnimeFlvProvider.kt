package com.streamflixreborn.streamflix.providers

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

object AnimeFlvProvider : Provider {

    override val name = "AnimeFLV"
    override val baseUrl = "https://vww.animeflv.one"
    override val language = "es"
    override val logo = "$baseUrl/cdn/img/favicon.ico"

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .dns(DnsResolver.doh)
        .build()

    private val service = Retrofit.Builder()
        .baseUrl("$baseUrl/")
        .addConverterFactory(JsoupConverterFactory.create())
        .client(client)
        .build()
        .create(AnimeFlvService::class.java)

    private interface AnimeFlvService {
        @GET
        suspend fun getPage(@Url url: String): Document

        @GET("animes")
        suspend fun search(
            @Query("buscar") query: String,
            @Query("pag") page: Int,
        ): Document

        @GET("animes")
        suspend fun getDirectory(
            @Query("pag") page: Int,
            @Query("orden") order: String? = null,
            @Query("estado") status: String? = null,
            @Query("tipo") type: String? = null,
            @Query("genero") genre: String? = null,
        ): Document
    }

    private val genres = listOf(
        Genre("accion", "Acción"),
        Genre("artes-marciales", "Artes Marciales"),
        Genre("aventuras", "Aventuras"),
        Genre("carreras", "Carreras"),
        Genre("ciencia-ficcion", "Ciencia Ficción"),
        Genre("comedia", "Comedia"),
        Genre("demencia", "Demencia"),
        Genre("demonios", "Demonios"),
        Genre("deportes", "Deportes"),
        Genre("drama", "Drama"),
        Genre("ecchi", "Ecchi"),
        Genre("escolares", "Escolares"),
        Genre("espacial", "Espacial"),
        Genre("fantasia", "Fantasía"),
        Genre("harem", "Harem"),
        Genre("historico", "Histórico"),
        Genre("infantil", "Infantil"),
        Genre("josei", "Josei"),
        Genre("juegos", "Juegos"),
        Genre("magia", "Magia"),
        Genre("mecha", "Mecha"),
        Genre("militar", "Militar"),
        Genre("misterio", "Misterio"),
        Genre("musica", "Música"),
        Genre("parodia", "Parodia"),
        Genre("policia", "Policía"),
        Genre("psicologico", "Psicológico"),
        Genre("recuentos-de-la-vida", "Recuentos de la vida"),
        Genre("romance", "Romance"),
        Genre("samurai", "Samurai"),
        Genre("seinen", "Seinen"),
        Genre("shoujo", "Shojo"),
        Genre("shounen", "Shounen"),
        Genre("sobrenatural", "Sobrenatural"),
        Genre("superpoderes", "Superpoderes"),
        Genre("suspenso", "Suspenso"),
        Genre("terror", "Terror"),
        Genre("vampiros", "Vampiros"),
        Genre("yaoi", "Yaoi"),
        Genre("yuri", "Yuri"),
    )

    override suspend fun getHome(): List<Category> {
        return try {
            coroutineScope {
                val homeDeferred = async { service.getPage(baseUrl) }
                val addedDeferred = async {
                    service.getDirectory(page = 1, order = "agregado")
                }
                val airingDeferred = async {
                    service.getDirectory(page = 1, status = "en-emision")
                }

                val categories = mutableListOf<Category>()

                runCatching {
                    val featured = parseDirectoryShows(addedDeferred.await())
                        .filterIsInstance<TvShow>()
                        .take(12)
                    if (featured.isNotEmpty()) {
                        categories.add(Category(Category.FEATURED, featured))
                    }
                }

                runCatching {
                    val latest = homeDeferred.await()
                        .select("div.ul.hm article.li")
                        .mapNotNull { element ->
                            val link = element.selectFirst("a[href*=/ver/]") ?: return@mapNotNull null
                            val href = absoluteUrl(link.attr("href"))
                            val slug = slugFromVerUrl(href) ?: return@mapNotNull null
                            val title = element.selectFirst("figure.i img")?.attr("alt")
                                ?.substringBefore(" episodio")
                                ?.substringBefore(" Episodio")
                                ?.ifBlank { null }
                                ?: link.attr("title")
                                    .removePrefix("Ver ")
                                    .substringBefore(" episodio")
                                    .substringBefore(" Episodio")
                                    .trim()
                            val poster = absoluteUrl(
                                element.selectFirst("img[data-src]")?.attr("data-src")
                                    ?: element.selectFirst("img")?.attr("src")
                            )?.replace("/portada/", "/anime/")

                            TvShow(
                                id = slug,
                                title = title.ifBlank { slug },
                                poster = poster,
                            )
                        }
                        .distinctBy { it.id }
                    if (latest.isNotEmpty()) {
                        categories.add(Category("Últimos Episodios", latest))
                    }
                }

                runCatching {
                    val airing = parseDirectoryShows(airingDeferred.await())
                        .filterIsInstance<TvShow>()
                    if (airing.isNotEmpty()) {
                        categories.add(Category("Animes en Emisión", airing))
                    }
                }

                categories
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) return genres
        if (page > 1) return emptyList()

        return try {
            parseDirectoryShows(service.search(query, page))
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return try {
            parseDirectoryShows(service.getDirectory(page = page, order = "agregado"))
                .filterIsInstance<TvShow>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return try {
            parseDirectoryShows(service.getDirectory(page = page, type = "pelicula"))
                .filterIsInstance<Movie>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getTvShow(id: String): TvShow {
        return try {
            val slug = id.substringAfterLast("/")
            val document = service.getPage("$baseUrl/anime/$slug")
            val title = document.selectFirst("div.info-t h2 i")?.text()
                ?: document.selectFirst("div.info-t h1")?.text()
                    ?.removePrefix("Ver ")
                    ?.removeSuffix(" Online")
                    ?.substringBefore(" Sub ")
                    ?.trim()
                ?: slug
            val overview = document.selectFirst("div.tx p")?.text()
            val poster = absoluteUrl(
                document.selectFirst("*[data-src*=cdn/img/anime/]")?.attr("data-src")
                    ?: document.selectFirst("div.info-l img")?.attr("src")
            )
            val genres = document.select("ul.gn a[href*=genero=]").map {
                Genre(
                    id = it.attr("href").substringAfter("genero=").substringBefore("&"),
                    name = it.text(),
                )
            }
            val animeAi = document.selectFirst("*[data-ai]")?.attr("data-ai").orEmpty()
            val episodes = parseEpisodes(document, slug, animeAi)

            TvShow(
                id = slug,
                title = title,
                overview = overview,
                poster = poster,
                genres = genres,
                seasons = listOf(
                    Season(
                        id = slug,
                        number = 1,
                        title = "Episodios",
                        episodes = episodes,
                    )
                ),
            )
        } catch (_: Exception) {
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
            genres = show.genres,
            cast = emptyList(),
            recommendations = emptyList(),
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        return getTvShow(seasonId).seasons.firstOrNull()?.episodes.orEmpty()
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val genreName = genres.firstOrNull { it.id == id }?.name
            ?: id.replace("-", " ").replaceFirstChar { it.uppercase() }
        return try {
            val shows = parseDirectoryShows(
                service.getDirectory(page = page, genre = id)
            )
            Genre(id = id, name = genreName, shows = shows)
        } catch (_: Exception) {
            Genre(id = id, name = genreName, shows = emptyList())
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        throw Exception("Not implemented for this provider")
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return try {
            val episodePath = when (videoType) {
                is Video.Type.Movie -> {
                    val show = getTvShow(id)
                    show.seasons.firstOrNull()?.episodes?.firstOrNull()?.id
                        ?: return emptyList()
                }
                is Video.Type.Episode -> id.removePrefix("/")
            }

            val document = service.getPage("$baseUrl/$episodePath")
            val encrypt = document.selectFirst(".opt[data-encrypt]")?.attr("data-encrypt")
                ?: return emptyList()
            val html = postFlvOptions(encrypt)
            if (html.isBlank()) return emptyList()

            org.jsoup.Jsoup.parse(html).select("li[encrypt]").mapNotNull { element ->
                val embedUrl = hexToAscii(element.attr("encrypt")).ifBlank { return@mapNotNull null }
                val name = element.attr("title")
                    .ifBlank { element.selectFirst("span")?.text() }
                    ?.removePrefix("Opción ")
                    ?.trim()
                    ?: "Servidor"
                Video.Server(
                    id = embedUrl,
                    name = name,
                    src = embedUrl,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.src.ifBlank { server.id }, server)
    }

    private fun parseDirectoryShows(document: Document): List<AppAdapter.Item> {
        return document.select("div.ul.x6 article.li, div.ul article.li").mapNotNull { element ->
            val link = element.selectFirst("a[href*=/anime/]") ?: return@mapNotNull null
            val href = absoluteUrl(link.attr("href")) ?: return@mapNotNull null
            val slug = href.substringAfterLast("/").substringBefore("?")
            if (slug.isBlank()) return@mapNotNull null

            val title = element.selectFirst("div.h a, .h a, a h3")?.text()?.trim()
                ?: link.attr("title")
                    .removePrefix("Ver Anime ")
                    .removePrefix("Ver ")
                    .removeSuffix(" Online Gratis")
                    .removeSuffix(" Online")
                    .trim()
            val poster = absoluteUrl(
                element.selectFirst("img[data-src]")?.attr("data-src")
                    ?: element.selectFirst("img")?.attr("src")
            )
            val type = element.selectFirst(".c-p, .c-o, .c-a, .c-e")?.text().orEmpty()

            if (type.contains("Película", ignoreCase = true)) {
                Movie(id = slug, title = title.ifBlank { slug }, poster = poster)
            } else {
                TvShow(id = slug, title = title.ifBlank { slug }, poster = poster)
            }
        }.distinctBy {
            when (it) {
                is Movie -> it.id
                is TvShow -> it.id
                else -> it.hashCode().toString()
            }
        }
    }

    private fun parseEpisodes(document: Document, slug: String, animeAi: String): List<Episode> {
        val script = document.select("script")
            .firstOrNull { it.data().contains("var eps =") }
            ?.data()
            .orEmpty()
        val episodeRegex = Regex("""\["(\d+)","(\d+)","([^"]*)"]""")
        val rawEpisodes = episodeRegex.findAll(script).mapNotNull { match ->
            val number = match.groupValues[1]
            val hasThumb = match.groupValues[2] == "1"
            val code = match.groupValues[3]
            val episodeId = buildString {
                append("ver/")
                append(slug)
                append('-')
                append(number)
                if (code.isNotBlank()) {
                    append('-')
                    append(code)
                }
            }
            val poster = if (hasThumb && animeAi.isNotBlank()) {
                "$baseUrl/cdn/img/episodios/$animeAi-$number.webp"
            } else {
                null
            }
            Episode(
                id = episodeId,
                number = number.toIntOrNull() ?: 0,
                title = "Episodio $number",
                poster = poster,
            )
        }.toList()

        // Site lists newest first; present episodes oldest → newest.
        return rawEpisodes.asReversed()
    }

    private fun postFlvOptions(encrypt: String): String {
        val body = FormBody.Builder()
            .add("acc", "opt")
            .add("i", encrypt)
            .build()
        val request = Request.Builder()
            .url("$baseUrl/flv")
            .post(body)
            .header("User-Agent", "Mozilla/5.0")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "$baseUrl/")
            .header("Origin", baseUrl)
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use ""
            response.body?.string().orEmpty()
        }
    }

    private fun absoluteUrl(url: String?): String? {
        if (url.isNullOrBlank() || url.startsWith("data:")) return null
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("./") -> "$baseUrl/${url.removePrefix("./")}"
            url.startsWith("/") -> "$baseUrl$url"
            else -> "$baseUrl/$url"
        }
    }

    private fun slugFromVerUrl(url: String): String? {
        val path = url.substringAfter("/ver/").substringBefore('?')
        if (path.isBlank()) return null
        val parts = path.split('-')
        if (parts.size < 2) return path
        // Drop trailing episode number and optional code.
        val last = parts.last()
        return if (last.all { it.isDigit() } || parts.dropLast(1).lastOrNull()?.all { it.isDigit() } == true) {
            val withoutCode = if (!last.all { it.isDigit() } && parts.size > 2) parts.dropLast(1) else parts
            withoutCode.dropLast(1).joinToString("-").ifBlank { path }
        } else {
            path
        }
    }

    private fun hexToAscii(hex: String): String {
        if (hex.isBlank() || hex.length % 2 != 0) return ""
        return runCatching {
            hex.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        }.getOrDefault("")
    }
}
