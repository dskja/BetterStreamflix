package com.streamflixreborn.streamflix.providers

import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import com.streamflixreborn.streamflix.utils.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

object DoramasflixProvider : Provider {

    override val name = "Doramasflix"
    override val baseUrl = "https://doramasflix.in"
    override val language = "es"
    override val logo: String =
        "https://assets.seriesapi.co/brands/doramasflix/websites/6a651fa138cbd16df74343be/logo/logo-1785013866419.png"

    private const val ACTION_GET_MOVIES = "c0ca8d9c46e61791ede8543b5de57b3fce2522bae2"
    private const val ACTION_GET_PAGINATION_DORAMAS = "c05872141d5f29f0089a4c629adc4889fdcf5f84e3"
    private const val ACTION_GET_EPISODE_LINKS = "4042b6ff7262141961145bdab7008e4c92323e054d"
    private const val ACTION_GET_MOVIE_LINKS = "40a81e120660afa2566d1235e6a4c05b1114d87a48"
    private const val ACTION_GET_EPISODES_PAGINATION = "40c389f001a72f0eb6ae05c14b824cb0d8d17926c5"

    private val client = NetworkClient.default

    private val serviceHtml = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(JsoupConverterFactory.create())
        .client(client)
        .build()
        .create(DoramasflixService::class.java)

    private val languages = arrayOf(
        Pair("36", "[ENG]"),
        Pair("37", "[CAST]"),
        Pair("38", "[LAT]"),
        Pair("192", "[SUB]"),
        Pair("1327", "[POR]"),
        Pair("13109", "[COR]"),
        Pair("13110", "[JAP]"),
        Pair("13111", "[MAN]"),
        Pair("13112", "[TAI]"),
        Pair("13113", "[FIL]"),
        Pair("13114", "[IND]"),
        Pair("343422", "[VIET]"),
    )

    private fun String.getLang(): String {
        return languages.firstOrNull { it.first == this }?.second ?: ""
    }

    private interface DoramasflixService {
        @GET
        suspend fun getPage(@Url url: String): Document
    }

    private fun getPosterUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return if (path.startsWith("http")) path else "https://image.tmdb.org/t/p/w500$path"
    }

    private fun absoluteUrl(pathOrUrl: String): String {
        return if (pathOrUrl.startsWith("http")) pathOrUrl else "$baseUrl/${pathOrUrl.removePrefix("/")}"
    }

    private fun pagePath(id: String): String {
        return id.removePrefix(baseUrl).removePrefix("/")
    }

    /**
     * Call a Next.js server action on doramasflix.in.
     * The site moved off Pages Router (__NEXT_DATA__) / public GraphQL (Cloudflare 403)
     * to App Router server actions for catalogs and stream links.
     */
    private suspend fun callServerAction(
        path: String,
        actionId: String,
        payload: JsonObject,
    ): JsonElement? = withContext(Dispatchers.IO) {
        val url = absoluteUrl(path)
        val body = JsonArray().apply { add(payload) }.toString()
            .toRequestBody("text/plain;charset=UTF-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Accept", "text/x-component")
            .header("Next-Action", actionId)
            .header("Origin", baseUrl)
            .header("Referer", url)
            .header("Content-Type", "text/plain;charset=UTF-8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val text = response.body?.string().orEmpty()
            parseServerActionPayload(text)
        }
    }

    private fun parseServerActionPayload(text: String): JsonElement? {
        // Next flight response: "0:{...}\n1:<json>"
        val line = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("1:") }
            ?: return null
        return try {
            JsonParser.parseString(line.removePrefix("1:"))
        } catch (_: Exception) {
            null
        }
    }

    private fun Document.htmlSource(): String = this.outerHtml()

    private fun extractJsonLd(document: Document, type: String): JsonObject? {
        for (script in document.select("script[type=application/ld+json]")) {
            try {
                val element = JsonParser.parseString(script.data())
                if (element.isJsonObject) {
                    val obj = element.asJsonObject
                    if (obj.get("@type")?.asString == type) return obj
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun extractRegexGroup(source: String, regex: Regex): String? {
        return regex.find(source)?.groupValues?.getOrNull(1)
    }

    /** Match keys inside Next.js RSC flight payloads (quotes are often escaped). */
    private fun extractRscString(source: String, key: String): String? {
        val pattern = Regex("""\\?"$key\\?"\s*:\s*\\?"((?:\\\\.|[^"\\])*)\\?""" + "\"")
        return extractRegexGroup(source, pattern)?.replace("\\\"", "\"")?.replace("\\\\", "\\")
    }

    private fun extractRscObjectId(source: String, objectKey: String): String? {
        val pattern = Regex(
            """\\?"$objectKey\\?"\s*:\s*\\?\{\s*\\?"_id\\?"\s*:\s*\\?"([a-f0-9]{24})\\?""" + "\""
        )
        return extractRegexGroup(source, pattern)
    }

    private fun parseShowCards(document: Document, hrefContains: String): List<Show> {
        val seen = linkedSetOf<String>()
        val shows = mutableListOf<Show>()
        for (anchor in document.select("article a[href*=/$hrefContains/], a[href*=/$hrefContains/]")) {
            val href = anchor.attr("href").substringBefore("?").trim()
            if (!href.contains("/$hrefContains/") || href.count { it == '/' } < 2) continue
            val path = href.removePrefix(baseUrl).removePrefix("/")
            if (!seen.add(path)) continue
            val img = anchor.selectFirst("img")
            val title = img?.attr("alt")?.ifBlank { null }
                ?: anchor.attr("aria-label").removePrefix("Ver ").ifBlank { null }
                ?: path.substringAfterLast('/')
            val poster = img?.attr("src")?.ifBlank { null } ?: img?.attr("data-src")
            val show = if (hrefContains == "peliculas-online") {
                Movie(id = path, title = title, poster = poster)
            } else {
                TvShow(id = path, title = title, poster = poster)
            }
            shows.add(show)
        }
        return shows
    }

    private fun movieFromActionItem(item: JsonObject): Movie {
        val slug = item.get("slug")?.asString.orEmpty()
        val name = item.get("name")?.asString.orEmpty()
        val nameEs = item.get("name_es")?.asString
        return Movie(
            id = "peliculas-online/$slug",
            title = listOfNotNull(name, nameEs?.takeIf { it.isNotBlank() && it != name })
                .joinToString(" (").let { if (it.contains("(")) "$it)" else it },
            poster = getPosterUrl(item.get("poster_path")?.asString ?: item.get("poster")?.asString),
        )
    }

    private fun doramaFromActionItem(item: JsonObject): TvShow {
        val slug = item.get("slug")?.asString.orEmpty()
        val name = item.get("name")?.asString.orEmpty()
        val nameEs = item.get("name_es")?.asString
        return TvShow(
            id = "doramas-online/$slug",
            title = listOfNotNull(name, nameEs?.takeIf { it.isNotBlank() && it != name })
                .joinToString(" (").let { if (it.contains("(")) "$it)" else it },
            poster = getPosterUrl(item.get("poster_path")?.asString ?: item.get("poster")?.asString),
        )
    }

    private fun unwrapEmbedShortener(link: String): String {
        if (!link.contains("embedshortener.co/e/")) return link
        return try {
            val token = link.substringAfter("/e/").substringBefore("?").substringBefore("#")
            val payload = token.split(".").getOrNull(1) ?: return link
            val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
            val json = String(Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
            val innerB64 = JsonParser.parseString(json).asJsonObject.get("link")?.asString ?: return link
            val innerPadded = innerB64 + "=".repeat((4 - innerB64.length % 4) % 4)
            String(Base64.decode(innerPadded, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            link
        }
    }

    private fun serverNameFromUrl(url: String): String {
        return try {
            URL(url).host.split(".").first { it != "www" && it.isNotBlank() }
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        } catch (_: Exception) {
            "Server"
        }
    }

    override suspend fun getHome(): List<Category> {
        return try {
            coroutineScope {
                val homeDeferred = async { serviceHtml.getPage(baseUrl) }
                val popularDoramasDeferred = async { getTvShows(1) }
                val popularMoviesDeferred = async { getMovies(1) }

                val homeDocument = homeDeferred.await()
                val bannerShows = parseShowCards(homeDocument, "doramas-online").take(12).ifEmpty {
                    parseShowCards(homeDocument, "peliculas-online").take(12)
                }

                listOf(
                    Category(name = Category.FEATURED, list = bannerShows),
                    Category(name = "Doramas Populares", list = popularDoramasDeferred.await()),
                    Category(name = "Películas Populares", list = popularMoviesDeferred.await()),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            return listOf(
                Genre("doramas", "Doramas"),
                Genre("peliculas", "Películas"),
                Genre("variedades", "Variedades"),
            )
        }

        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val document = serviceHtml.getPage("$baseUrl/buscar?q=$encoded&page=$page")
            val results = mutableListOf<AppAdapter.Item>()
            results += parseShowCards(document, "doramas-online")
            results += parseShowCards(document, "peliculas-online")
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return try {
            val payload = JsonObject().apply {
                addProperty("page", page)
                addProperty("limit", 20)
                addProperty("sort", "POPULARITY_DESC")
                addProperty("brandHost", "doramasflix.in")
            }
            val element = callServerAction("peliculas-online", ACTION_GET_MOVIES, payload)
            when {
                element == null || !element.isJsonArray -> {
                    if (page == 1) {
                        parseShowCards(serviceHtml.getPage("$baseUrl/peliculas-online"), "peliculas-online")
                            .filterIsInstance<Movie>()
                    } else emptyList()
                }
                else -> element.asJsonArray.mapNotNull {
                    runCatching { movieFromActionItem(it.asJsonObject) }.getOrNull()
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return try {
            val payload = JsonObject().apply {
                addProperty("page", page)
                addProperty("limit", 20)
                addProperty("sort", "POPULARITY_DESC")
                add("filter", JsonObject().apply { addProperty("isTVShow", false) })
                addProperty("brandHost", "doramasflix.in")
            }
            val element = callServerAction("doramas-online", ACTION_GET_PAGINATION_DORAMAS, payload)
            val items = when {
                element == null -> null
                element.isJsonObject -> element.asJsonObject.getAsJsonArray("items")
                element.isJsonArray -> element.asJsonArray
                else -> null
            }
            when {
                items == null -> {
                    if (page == 1) {
                        parseShowCards(serviceHtml.getPage("$baseUrl/doramas-online"), "doramas-online")
                            .filterIsInstance<TvShow>()
                    } else emptyList()
                }
                else -> items.mapNotNull {
                    runCatching { doramaFromActionItem(it.asJsonObject) }.getOrNull()
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovie(id: String): Movie {
        return try {
            val path = pagePath(id)
            val document = serviceHtml.getPage(absoluteUrl(path))
            val source = document.htmlSource()

            val ld = extractJsonLd(document, "Movie")
            val movieId = extractRscObjectId(source, "movie")
            val name = ld?.get("name")?.asString
            val nameEs = ld?.get("alternateName")?.asString
            val overview = ld?.get("description")?.asString
            val poster = ld?.get("image")?.asString
                ?: extractRegexGroup(
                    source,
                    Regex("""\\?"poster_path\\?"\s*:\s*\\?"(/[^"\\]+)\\?""" + "\""),
                )?.let { getPosterUrl(it) }

            if (name.isNullOrBlank() && movieId == null && ld == null) {
                throw Exception("No se pudo encontrar el script de datos.")
            }

            val titleName = name.orEmpty()
            Movie(
                id = path,
                title = listOfNotNull(
                    titleName.takeIf { it.isNotBlank() },
                    nameEs?.takeIf { it.isNotBlank() && it != titleName },
                ).joinToString(" (").let { if (it.contains("(")) "$it)" else it }
                    .ifBlank { path.substringAfterLast('/') },
                overview = overview,
                poster = poster,
            )
        } catch (e: Exception) {
            throw Exception("No se pudieron cargar los detalles de la película: ${e.message}")
        }
    }

    override suspend fun getTvShow(id: String): TvShow {
        return try {
            val path = pagePath(id)
            val document = serviceHtml.getPage(absoluteUrl(path))
            val source = document.htmlSource()
            val ld = extractJsonLd(document, "TVSeries")

            val serieId = extractRscString(source, "serie_id")
                ?.takeIf { it.matches(Regex("[a-f0-9]{24}")) }
                ?: extractRegexGroup(
                    source,
                    Regex("""\\?"serie_id\\?"\s*:\s*\\?"([a-f0-9]{24})\\?""" + "\""),
                )

            val name = ld?.get("name")?.asString
                ?: extractRegexGroup(
                    source,
                    Regex("""<meta property="og:title" content="([^"]+)">"""),
                )
                    ?.substringBefore(" ⚜️")
                    ?.substringBefore(" |")
                    .orEmpty()
            val nameEs = ld?.get("alternateName")?.asString
            val overview = ld?.get("description")?.asString
            val poster = ld?.get("image")?.asString
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")

            val seasons = Regex(
                """\\?"seasons\\?"\s*:\s*\[((?:\\?\{[^]]*?\},?\s*)+)\]"""
            ).find(source)?.groupValues?.getOrNull(1)?.let { seasonsRaw ->
                Regex("""\\?"season_number\\?"\s*:\s*(\d+)""")
                    .findAll(seasonsRaw)
                    .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                    .distinct()
                    .sorted()
                    .map { number ->
                        Season(
                            id = "${serieId ?: path}/$number",
                            number = number,
                            title = "Temporada $number",
                        )
                    }
                    .toList()
            }.orEmpty().ifEmpty {
                listOf(
                    Season(
                        id = "${serieId ?: path}/1",
                        number = 1,
                        title = "Temporada 1",
                    )
                )
            }

            if (name.isBlank() && serieId == null && ld == null) {
                throw Exception("No se pudo encontrar el script de datos.")
            }

            TvShow(
                id = serieId ?: path,
                title = listOfNotNull(
                    name,
                    nameEs?.takeIf { it.isNotBlank() && it != name },
                ).joinToString(" (").let { if (it.contains("(")) "$it)" else it },
                overview = overview,
                poster = poster,
                seasons = seasons,
            )
        } catch (e: Exception) {
            throw Exception("No se pudieron cargar los detalles del dorama: ${e.message}")
        }
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val serieId = seasonId.substringBefore("/")
        val seasonNumber = seasonId.substringAfter("/", "1").toIntOrNull() ?: 1

        // If season was keyed by path instead of mongo id, resolve via show page.
        val resolvedSerieId = if (serieId.matches(Regex("[a-f0-9]{24}"))) {
            serieId
        } else {
            val document = serviceHtml.getPage(absoluteUrl(serieId))
            extractRegexGroup(
                document.htmlSource(),
                Regex("""\\?"serie_id\\?"\s*:\s*\\?"([a-f0-9]{24})\\?""" + "\""),
            ) ?: return emptyList()
        }

        val refererPath = if (serieId.contains("/")) serieId else "doramas-online"
        return try {
            val payload = JsonObject().apply {
                addProperty("serie_id", resolvedSerieId)
                addProperty("season_number", seasonNumber)
                addProperty("page", 1)
                addProperty("limit", 100)
                addProperty("sort", "NUMBER_ASC")
                addProperty("brandHost", "doramasflix.in")
            }
            val element = callServerAction(refererPath, ACTION_GET_EPISODES_PAGINATION, payload)
            val items = when {
                element == null -> null
                element.isJsonObject -> element.asJsonObject.getAsJsonArray("items")
                element.isJsonArray -> element.asJsonArray
                else -> null
            } ?: return emptyList()

            items.mapNotNull { itemElement ->
                val item = itemElement.asJsonObject
                val slug = item.get("slug")?.asString ?: return@mapNotNull null
                val number = item.get("episode_number")?.asInt ?: 0
                val title = item.get("name_es")?.asString ?: item.get("name")?.asString
                Episode(
                    id = slug,
                    number = number,
                    title = "Episodio $number${title?.let { ": $it" } ?: ""}",
                    poster = getPosterUrl(item.get("still_path")?.asString),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return try {
            when (videoType) {
                is Video.Type.Movie -> {
                    val path = pagePath(id)
                    val document = serviceHtml.getPage(absoluteUrl(path))
                    val movieId = extractRscObjectId(document.htmlSource(), "movie")
                        ?: return emptyList()

                    val payload = JsonObject().apply { addProperty("movie_id", movieId) }
                    val element = callServerAction(path, ACTION_GET_MOVIE_LINKS, payload)
                    parseLinksOnline(element)
                }

                is Video.Type.Episode -> {
                    val slug = pagePath(id).removePrefix("episodios/")
                    val path = "episodios/$slug"
                    val document = serviceHtml.getPage(absoluteUrl(path))
                    val episodeId = extractRscObjectId(document.htmlSource(), "episode")
                        ?: return emptyList()

                    val payload = JsonObject().apply { addProperty("episode_id", episodeId) }
                    val element = callServerAction(path, ACTION_GET_EPISODE_LINKS, payload)
                    parseLinksOnline(element)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseLinksOnline(element: JsonElement?): List<Video.Server> {
        if (element == null || !element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull { itemElement ->
            val item = itemElement.asJsonObject
            val rawLink = item.get("link")?.asString ?: return@mapNotNull null
            val finalUrl = unwrapEmbedShortener(rawLink)
            val lang = item.get("lang")?.asString?.getLang().orEmpty()
            val name = serverNameFromUrl(finalUrl)
            Video.Server(
                id = finalUrl,
                name = "$name $lang".trim(),
            )
        }.distinctBy { it.id }
    }

    override suspend fun getVideo(server: Video.Server): Video = Extractor.extract(server.id, server)

    override suspend fun getGenre(id: String, page: Int): Genre {
        val list: List<Show> = when (id) {
            "peliculas" -> getMovies(page)
            "variedades" -> {
                try {
                    val payload = JsonObject().apply {
                        addProperty("page", page)
                        addProperty("limit", 32)
                        addProperty("sort", "CREATEDAT_DESC")
                        add("filter", JsonObject().apply { addProperty("isTVShow", true) })
                        addProperty("brandHost", "doramasflix.in")
                    }
                    val element = callServerAction("variedades-online", ACTION_GET_PAGINATION_DORAMAS, payload)
                    val items = when {
                        element == null -> null
                        element.isJsonObject -> element.asJsonObject.getAsJsonArray("items")
                        element.isJsonArray -> element.asJsonArray
                        else -> null
                    }
                    items?.mapNotNull {
                        runCatching { doramaFromActionItem(it.asJsonObject) }.getOrNull()
                    } ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            }
            else -> getTvShows(page)
        }
        return Genre(id = id, name = id.replaceFirstChar { it.uppercase() }, shows = list)
    }

    override suspend fun getPeople(id: String, page: Int): People = throw Exception("Not yet implemented")

    private fun String.unescapeJson(): String {
        return this
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}
