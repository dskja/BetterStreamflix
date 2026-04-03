package com.streamflixreborn.streamflix.providers

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
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.text.Normalizer
import java.util.concurrent.TimeUnit

object FilmyOnlineCcProvider : Provider {

    override val name = "FilmyOnline"
    override val baseUrl = "https://filmyonline.cc"
    override val logo = "$baseUrl/favicon/icon-144x144.png?v=1703232212"
    override val language = "pl"

    private val service = FilmyOnlineCcService.build()

    override suspend fun getHome(): List<Category> {
        val root = getBootstrapRoot(service.getDocument(baseUrl))
        val channels = sequenceOf(
            root.optJSONObject("loaders")
                ?.optJSONObject("homePage")
                ?.optJSONArray("channels")
                ?.toJsonObjectList(),
            root.optJSONObject("loaders")
                ?.optJSONObject("channelPage")
                ?.optJSONObject("channels")
                ?.optJSONArray("data")
                ?.toJsonObjectList(),
            collectChannelObjects(root)
        ).firstOrNull { !it.isNullOrEmpty() }.orEmpty()

        return channels.mapNotNull { channel ->
            val items = channel.optJSONObject("content")
                ?.optJSONArray("data")
                ?.toTitleItems()
                .orEmpty()
                .take(20)

            if (items.isEmpty()) return@mapNotNull null

            Category(
                name = channel.optString("name").ifBlank { "FilmyOnline" },
                list = items
            )
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            return listOf(
                Genre(id = "/movies", name = "Filmy"),
                Genre(id = "/series", name = "Seriale")
            )
        }

        val root = getBootstrapRoot(service.search(query))
        val loaders = root.optJSONObject("loaders") ?: return emptyList()

        val directResults = sequenceOf(
            loaders.optJSONObject("searchPage")?.optJSONArray("results"),
            loaders.optJSONObject("searchPage")?.optJSONObject("pagination")?.optJSONArray("data"),
            loaders.optJSONObject("searchPage")?.optJSONObject("titles")?.optJSONArray("data"),
            loaders.optJSONObject("channelPage")?.optJSONObject("channel")?.optJSONObject("content")?.optJSONArray("data")
        ).firstOrNull { it != null }?.toTitleItems().orEmpty()

        if (directResults.isNotEmpty()) return directResults.distinctBy(::itemKey)

        return collectTitleObjects(root)
            .filter { title ->
                title.optString("name").contains(query, ignoreCase = true)
            }
            .mapNotNull(::toItem)
            .distinctBy(::itemKey)
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return getChannelItems("$baseUrl/movies?page=$page").filterIsInstance<Movie>()
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return getChannelItems("$baseUrl/series?page=$page").filterIsInstance<TvShow>()
    }

    override suspend fun getMovie(id: String): Movie {
        val parsed = parseEncodedId(id)
        val watchId = parsed.primaryVideoId
            ?: throw Exception("FilmyOnline movie is missing a playable source")
        val root = getBootstrapRoot(service.getDocument("$baseUrl/watch/$watchId"))
        val watchPage = root.optJSONObject("loaders")?.optJSONObject("watchPage")
            ?: throw Exception("Unable to load FilmyOnline movie")
        val title = watchPage.optJSONObject("title")
            ?: watchPage.optJSONObject("video")?.optJSONObject("title")
            ?: throw Exception("Missing title data")

        return (toItem(title) as? Movie)?.copy(
            id = buildEncodedId(
                type = "movie",
                titleId = parsed.titleId,
                primaryVideoId = parsed.primaryVideoId,
                titleSlug = parsed.titleSlug ?: slugifyTitle(title.optString("name"))
            ),
            runtime = watchPage.optJSONObject("video")?.optInt("runtime").takeIf { it != null && it > 0 }
                ?: (toItem(title) as? Movie)?.runtime,
            poster = title.optString("poster").ifBlank { null },
            banner = title.optString("backdrop").ifBlank { null },
            genres = title.optJSONArray("genres")?.toGenres().orEmpty(),
            recommendations = watchPage.optJSONArray("related_videos").toRecommendationItems(),
        ) ?: throw Exception("Unable to build FilmyOnline movie")
    }

    override suspend fun getTvShow(id: String): TvShow {
        val parsed = parseEncodedId(id)
        val titleSlug = resolveTitleSlug(parsed)
            ?: throw Exception("Unable to resolve FilmyOnline show slug")
        val root = getBootstrapRoot(service.getDocument(buildTitleUrl(parsed.titleId, titleSlug)))
        val titlePage = root.optJSONObject("loaders")?.optJSONObject("titlePage")
            ?: throw Exception("Unable to load FilmyOnline show")
        val title = titlePage.optJSONObject("title")
            ?: throw Exception("Missing title data")

        val seasons = titlePage.optJSONObject("seasons")
            ?.optJSONArray("data")
            ?.toSeasonObjects()
            .orEmpty()
            .sortedBy { it.optInt("number") }
            .let { seasonObjects ->
                if (seasonObjects.isEmpty()) {
                    emptyList()
                } else {
                    coroutineScope {
                        seasonObjects.map { seasonObject ->
                            async {
                                val seasonNumber = seasonObject.optInt("number").takeIf { it > 0 } ?: return@async null
                                Season(
                                    id = buildSeasonId(parsed.titleId, titleSlug, seasonNumber),
                                    number = seasonNumber,
                                    title = "Sezon $seasonNumber",
                                    poster = seasonObject.optString("poster").ifBlank { title.optString("poster").ifBlank { null } },
                                    episodes = fetchSeasonEpisodes(parsed.titleId, titleSlug, seasonNumber, title)
                                )
                            }
                        }.awaitAll().filterNotNull().sortedBy { it.number }
                    }
                }
            }

        return (toItem(title) as? TvShow)?.copy(
            id = buildEncodedId(
                type = "tv",
                titleId = parsed.titleId,
                primaryVideoId = parsed.primaryVideoId,
                titleSlug = titleSlug
            ),
            poster = title.optString("poster").ifBlank { null },
            banner = title.optString("backdrop").ifBlank { null },
            genres = title.optJSONArray("genres")?.toGenres().orEmpty(),
            seasons = seasons,
            recommendations = titlePage.optJSONArray("related_videos").toRecommendationItems(),
        ) ?: throw Exception("Unable to build FilmyOnline show")
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val parts = seasonId.split("|")
        if (parts.size < 3) return emptyList()
        val titleId = parts[0].toIntOrNull() ?: return emptyList()
        val titleSlug = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return emptyList()
        val seasonNumber = parts.getOrNull(2)?.toIntOrNull() ?: return emptyList()
        val titleRoot = getBootstrapRoot(service.getDocument(buildTitleUrl(titleId, titleSlug)))
        val title = titleRoot.optJSONObject("loaders")
            ?.optJSONObject("titlePage")
            ?.optJSONObject("title")
            ?: return emptyList()

        return fetchSeasonEpisodes(titleId, titleSlug, seasonNumber, title)
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val normalized = when (id) {
            "/series" -> "Seriale"
            else -> "Filmy"
        }
        val url = if (id.startsWith("/")) "$baseUrl$id?page=$page" else "$baseUrl/movies?page=$page"
        return Genre(
            id = id,
            name = normalized,
            shows = getChannelItems(url).filterIsInstance<Show>()
        )
    }

    override suspend fun getPeople(id: String, page: Int): People {
        return People(
            id = id,
            name = id,
            filmography = emptyList()
        )
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val watchId = when (videoType) {
            is Video.Type.Movie -> parseEncodedId(id).primaryVideoId
            is Video.Type.Episode -> id.toIntOrNull()
        } ?: return emptyList()

        val root = getBootstrapRoot(service.getDocument("$baseUrl/watch/$watchId"))
        val watchPage = root.optJSONObject("loaders")?.optJSONObject("watchPage") ?: return emptyList()

        val collected = linkedMapOf<String, Video.Server>()
        fun addServer(video: JSONObject?) {
            if (video == null) return
            val source = video.optString("src").ifBlank { return }
            val serverName = buildString {
                append(video.optString("quality").ifBlank { "default" }.uppercase())
                val lang = video.optString("language").ifBlank { null }
                if (lang != null) append(" [$lang]")
            }
            collected.putIfAbsent(
                source,
                Video.Server(
                    id = source,
                    name = serverName
                )
            )
        }

        addServer(watchPage.optJSONObject("video"))
        watchPage.optJSONArray("alternative_videos")?.let { videos ->
            for (index in 0 until videos.length()) addServer(videos.optJSONObject(index))
        }

        return collected.values.toList()
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.id, server)
    }

    private suspend fun getChannelItems(url: String): List<AppAdapter.Item> {
        val root = getBootstrapRoot(service.getDocument(url))
        return root.optJSONObject("loaders")
            ?.optJSONObject("channelPage")
            ?.optJSONObject("channel")
            ?.optJSONObject("content")
            ?.optJSONArray("data")
            ?.toTitleItems()
            .orEmpty()
    }

    private fun getBootstrapRoot(document: Document): JSONObject {
        val html = document.outerHtml()
        val marker = "window.bootstrapData ="
        val markerIndex = html.indexOf(marker)
        if (markerIndex == -1) {
            throw Exception("Unable to find FilmyOnline bootstrap data")
        }

        val startIndex = html.indexOf('{', markerIndex + marker.length)
        if (startIndex == -1) {
            throw Exception("Unable to find FilmyOnline bootstrap JSON start")
        }

        var depth = 0
        var inString = false
        var escaped = false

        for (index in startIndex until html.length) {
            val char = html[index]

            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    inString = false
                }
                continue
            }

            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return JSONObject(html.substring(startIndex, index + 1))
                    }
                }
            }
        }

        throw Exception("Unable to extract FilmyOnline bootstrap JSON")
    }

    private fun JSONArray.toTitleItems(): List<AppAdapter.Item> {
        return (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let(::toItem)
        }.distinctBy(::itemKey)
    }

    private fun toItem(title: JSONObject): AppAdapter.Item? {
        if (title.optString("model_type") != "title") return null

        val titleId = title.optInt("id").takeIf { it > 0 } ?: return null
        val primaryVideo = title.optJSONObject("primary_video")
        val encodedId = buildEncodedId(
            type = if (title.optBoolean("is_series")) "tv" else "movie",
            titleId = titleId,
            primaryVideoId = primaryVideo?.optInt("id")?.takeIf { it > 0 },
            titleSlug = slugifyTitle(title.optString("name"))
        )

        val commonTitle = title.optString("name")
        val commonOverview = title.optString("description").ifBlank { null }
        val commonReleased = title.optString("release_date").ifBlank { title.optString("year").ifBlank { null } }
        val commonPoster = title.optString("poster").ifBlank { null }
        val commonBanner = title.optString("backdrop").ifBlank { null }
        val commonRating = title.optDouble("rating").takeIf { it > 0.0 }
        val commonRuntime = title.optInt("runtime").takeIf { it > 0 }

        return if (title.optBoolean("is_series")) {
            TvShow(
                id = encodedId,
                title = commonTitle,
                overview = commonOverview,
                released = commonReleased,
                runtime = commonRuntime,
                rating = commonRating,
                poster = commonPoster,
                banner = commonBanner
            )
        } else {
            Movie(
                id = encodedId,
                title = commonTitle,
                overview = commonOverview,
                released = commonReleased,
                runtime = commonRuntime,
                rating = commonRating,
                poster = commonPoster,
                banner = commonBanner
            )
        }
    }

    private fun JSONArray.toGenres(): List<Genre> {
        return (0 until length()).mapNotNull { index ->
            val genre = optJSONObject(index) ?: return@mapNotNull null
            Genre(
                id = genre.optString("name").ifBlank { return@mapNotNull null },
                name = genre.optString("display_name").ifBlank { genre.optString("name") }
            )
        }
    }

    private fun JSONArray?.toRecommendationItems(): List<Show> {
        if (this == null) return emptyList()
        val mapped = mutableListOf<Show>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val title = item.optJSONObject("title") ?: continue
            val recommendation = toItem(title)
            when (recommendation) {
                is Movie -> mapped += recommendation
                is TvShow -> mapped += recommendation
            }
        }
        return mapped.distinctBy(::itemKey)
    }

    private fun collectTitleObjects(root: JSONObject): List<JSONObject> {
        val results = mutableListOf<JSONObject>()

        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    if (value.optString("model_type") == "title") {
                        results += value
                    }
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        walk(value.opt(keys.next()))
                    }
                }

                is JSONArray -> {
                    for (index in 0 until value.length()) {
                        walk(value.opt(index))
                    }
                }
            }
        }

        walk(root)
        return results
    }

    private fun collectChannelObjects(root: JSONObject): List<JSONObject> {
        val results = mutableListOf<JSONObject>()

        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val type = value.optString("type")
                    val modelType = value.optString("model_type")
                    val hasTitleData = value.optJSONObject("content")
                        ?.optJSONArray("data")
                        ?.let { data ->
                            (0 until data.length()).any { index ->
                                data.optJSONObject(index)?.optString("model_type") == "title"
                            }
                        } == true

                    if ((type == "channel" || modelType == "channel") && hasTitleData) {
                        results += value
                    }

                    val keys = value.keys()
                    while (keys.hasNext()) {
                        walk(value.opt(keys.next()))
                    }
                }

                is JSONArray -> {
                    for (index in 0 until value.length()) {
                        walk(value.opt(index))
                    }
                }
            }
        }

        walk(root)
        return results.distinctBy { it.optInt("id").toString() + ":" + it.optString("name") }
    }

    private fun JSONArray.toJsonObjectList(): List<JSONObject> {
        return (0 until length()).mapNotNull { index -> optJSONObject(index) }
    }

    private fun JSONArray.toSeasonObjects(): List<JSONObject> {
        return (0 until length()).mapNotNull { index -> optJSONObject(index) }
    }

    private fun JSONArray.toEpisodeItems(fallbackPoster: String?): List<Episode> {
        return (0 until length()).mapNotNull { index ->
            val episode = optJSONObject(index) ?: return@mapNotNull null
            val primaryVideoId = episode.optJSONObject("primary_video")
                ?.optInt("id")
                ?.takeIf { it > 0 }
                ?: return@mapNotNull null
            val episodeNumber = episode.optInt("episode_number").takeIf { it > 0 } ?: return@mapNotNull null

            Episode(
                id = primaryVideoId.toString(),
                number = episodeNumber,
                title = episode.optString("name").ifBlank { "Odcinek $episodeNumber" },
                released = episode.optString("release_date").ifBlank { null },
                poster = episode.optString("poster").ifBlank { fallbackPoster },
                overview = episode.optString("description").ifBlank { null }
            )
        }
    }

    private fun itemKey(item: AppAdapter.Item): String {
        return when (item) {
            is Movie -> "movie:${item.id}"
            is TvShow -> "tv:${item.id}"
            is Genre -> "genre:${item.id}"
            else -> item.toString()
        }
    }

    private fun buildEncodedId(type: String, titleId: Int, primaryVideoId: Int?, titleSlug: String?): String {
        return listOf(
            type,
            titleId.toString(),
            primaryVideoId?.toString().orEmpty(),
            titleSlug.orEmpty()
        ).joinToString("|")
    }

    private suspend fun fetchSeasonEpisodes(
        titleId: Int,
        titleSlug: String,
        seasonNumber: Int,
        title: JSONObject
    ): List<Episode> {
        val root = getBootstrapRoot(service.getDocument(buildSeasonUrl(titleId, titleSlug, seasonNumber)))
        val seasonPage = root.optJSONObject("loaders")?.optJSONObject("seasonPage") ?: return emptyList()
        val seasonPoster = seasonPage.optJSONObject("season")?.optString("poster")
        val fallbackPoster = seasonPoster?.ifBlank { null }
            ?: title.optString("poster").ifBlank { null }

        return seasonPage.optJSONObject("episodes")
            ?.optJSONArray("data")
            ?.toEpisodeItems(fallbackPoster)
            .orEmpty()
            .sortedBy { it.number }
    }

    private fun buildSeasonId(titleId: Int, titleSlug: String, seasonNumber: Int): String {
        return listOf(titleId.toString(), titleSlug, seasonNumber.toString()).joinToString("|")
    }

    private fun buildTitleUrl(titleId: Int, titleSlug: String): String {
        return "$baseUrl/titles/$titleId/$titleSlug"
    }

    private fun buildSeasonUrl(titleId: Int, titleSlug: String, seasonNumber: Int): String {
        return "${buildTitleUrl(titleId, titleSlug)}/season/$seasonNumber"
    }

    private suspend fun resolveTitleSlug(parsed: ParsedId): String? {
        parsed.titleSlug?.let { return it }

        val watchId = parsed.primaryVideoId ?: return null
        val root = getBootstrapRoot(service.getDocument("$baseUrl/watch/$watchId"))
        val watchTitle = root.optJSONObject("loaders")
            ?.optJSONObject("watchPage")
            ?.optJSONObject("title")
            ?.optString("name")
            ?.ifBlank { null }
            ?: root.optJSONObject("loaders")
                ?.optJSONObject("watchPage")
                ?.optJSONObject("video")
                ?.optJSONObject("title")
                ?.optString("name")
                ?.ifBlank { null }

        return slugifyTitle(watchTitle)
    }

    private fun slugifyTitle(value: String?): String? {
        if (value.isNullOrBlank()) return null

        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

        return normalized.ifBlank { null }
    }

    private data class ParsedId(
        val type: String,
        val titleId: Int,
        val primaryVideoId: Int?,
        val titleSlug: String?
    )

    private fun parseEncodedId(id: String): ParsedId {
        val parts = id.split("|")
        return ParsedId(
            type = parts.getOrNull(0).orEmpty(),
            titleId = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            primaryVideoId = parts.getOrNull(2)?.toIntOrNull(),
            titleSlug = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
        )
    }

    private interface FilmyOnlineCcService {
        @GET
        suspend fun getDocument(@Url url: String): Document

        @GET("search")
        suspend fun search(@Query("q") query: String): Document

        companion object {
            fun build(): FilmyOnlineCcService {
                val client = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .build()

                return Retrofit.Builder()
                    .baseUrl("$baseUrl/")
                    .client(client)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                    .create(FilmyOnlineCcService::class.java)
            }
        }
    }
}
