package com.streamflixreborn.streamflix.providers

import android.util.Base64
import android.util.Log
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
import com.streamflixreborn.streamflix.utils.ArtworkRequestHeaders
import com.streamflixreborn.streamflix.utils.DnsResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object MkissaProvider : Provider {

    private const val TAG = "MkissaProvider"
    private const val API_URL = "https://api.allanime.day/"
    private const val CLOCK_URL = "https://allanime.day"
    private const val SEARCH_HASH = "a24c500a1b765c68ae1d8dd85174931f661c71369c89b92b88b75a725afc471c"
    private const val POPULAR_DAILY_HASH = "a0aca6827cc9a3ad7bc711da4d200a04adea8f1a7545dc418d5e92e74c3aad15"
    private const val POPULAR_HASH = "ac2c75884a11fca5707ce4ad10f2e3e2aae31e42af5e4d9c511a4a5e708e4c6d"
    private const val DETAIL_HASH = "043448386c7a686bc2aabfbb6b80f6074e795d350df48015023b079527b0848a"
    private const val SOURCE_HASH = "d405d0edd690624b66baba3068e0edc3ac90f1597d898a1ec8db4e5c43c00fec"
    private const val GENRE_HASH = "ff61a63ff776f334f80c1e6ad1aa49ef71eab831e235e5d6ec679eae5b83450f"
    private const val IMAGE_URL = "https://aln.youtube-anime.com"
    private const val HOME_ROW_LIMIT = 20
    private const val HOME_TAG_LIMIT = 20
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val translationTypes = listOf("sub", "dub", "raw")
    private val browseTranslationTypes = listOf("sub", "dub")

    private val SHOW_FIELDS = """
        _id
        type
        englishName
        name
        nativeName
        nameOnlyString
        altNames
        slugTime
        description
        availableEpisodes
        episodeCount
        lastEpisodeInfo
        episodeDuration
        airedStart
        score
        thumbnail
        banner
        genres
        isAdult
    """.trimIndent()

    private val SEARCH_QUERY = """
        query(
          ${'$'}search: SearchInput
          ${'$'}limit: Int
          ${'$'}page: Int
          ${'$'}translationType: VaildTranslationTypeEnumType
          ${'$'}countryOrigin: VaildCountryOriginEnumType
          ${'$'}allowAdult: Boolean
        ) {
          shows(
            search: ${'$'}search
            limit: ${'$'}limit
            page: ${'$'}page
            translationType: ${'$'}translationType
            countryOrigin: ${'$'}countryOrigin
            allowAdult: ${'$'}allowAdult
          ) {
            pageInfo { total }
            edges { $SHOW_FIELDS }
          }
        }
    """.trimIndent()

    private val POPULAR_DAILY_QUERY = """
        query(
          ${'$'}type: VaildPopularTypeEnumType!
          ${'$'}size: Int!
          ${'$'}dateRange: Int
          ${'$'}page: Int
          ${'$'}allowAdult: Boolean
          ${'$'}allowUnknown: Boolean
        ) {
          queryPopular(
            type: ${'$'}type
            size: ${'$'}size
            dateRange: ${'$'}dateRange
            page: ${'$'}page
            allowAdult: ${'$'}allowAdult
            allowUnknown: ${'$'}allowUnknown
          ) {
            total
            recommendations {
              anyCard {
                $SHOW_FIELDS
                lastEpisodeDate
                lastChapterDate
                availableChapters
              }
            }
          }
        }
    """.trimIndent()

    private val TAG_QUERY = """
        query(${ '$' }search: ListForTagInput!) {
          queryListForTag(search: ${ '$' }search) {
            pageInfo { total }
            edges { $SHOW_FIELDS }
          }
        }
    """.trimIndent()

    private val TAGS_QUERY = """
        query(
          ${ '$' }page: Int
          ${ '$' }offset: Int
          ${ '$' }limit: Int
          ${ '$' }search: TagSearchInput
        ) {
          queryTags(
            page: ${ '$' }page
            offset: ${ '$' }offset
            limit: ${ '$' }limit
            search: ${ '$' }search
          ) {
            pageInfo { total }
            edges {
              _id
              name
              slug
              tagType
            }
          }
        }
    """.trimIndent()

    private val DETAIL_QUERY = """
        query(${ '$' }_id: String!) {
          show(_id: ${ '$' }_id) {
            $SHOW_FIELDS
            status
            averageScore
            rating
            airedEnd
            studios
            countryOfOrigin
            availableEpisodesDetail
            isAdult
            tags
          }
        }
    """.trimIndent()

    private val RANDOM_QUERY = """
        query(
          ${ '$' }format: String!
          ${ '$' }allowAdult: Boolean
        ) {
          queryRandomRecommendation(
            format: ${ '$' }format
            allowAdult: ${ '$' }allowAdult
          ) {
            $SHOW_FIELDS
          }
        }
    """.trimIndent()

    private val SOURCE_QUERY = """
        query(
          ${ '$' }showId: String!
          ${ '$' }translationType: VaildTranslationTypeEnumType!
          ${ '$' }episodeString: String!
        ) {
          episode(
            showId: ${ '$' }showId
            translationType: ${ '$' }translationType
            episodeString: ${ '$' }episodeString
          ) {
            episodeString
            uploadDate
            sourceUrls
            thumbnail
            notes
            show { $SHOW_FIELDS }
          }
        }
    """.trimIndent()

    override val name = "MKissa"
    override val baseUrl = "https://mkissa.to/anime"
    override val language = "en"
    override val logo = "https://mkissa.to/favicon-32x32.png"

    private val service = Retrofit.Builder()
        .baseUrl(API_URL)
        .addConverterFactory(ScalarsConverterFactory.create())
        .client(
            OkHttpClient.Builder()
                .cache(Cache(File("cacheDir", "mkissa_okhttpcache"), 10 * 1024 * 1024))
                .readTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .dns(DnsResolver.doh)
                .build()
        )
        .build()
        .create(MkissaService::class.java)

    private val sourceResolverClient = OkHttpClient.Builder()
        .dns(DnsResolver.doh)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private interface MkissaService {
        @Headers(
            "Accept: application/json",
            "Origin: https://mkissa.to",
            "Referer: https://mkissa.to/",
            "User-Agent: Mozilla/5.0"
        )
        @GET("api")
        suspend fun api(
            @Query("variables") variables: String,
            @Query("extensions") extensions: String
        ): String

        @Headers(
            "Accept: application/json",
            "Content-Type: application/json",
            "Origin: https://mkissa.to",
            "Referer: https://mkissa.to/",
            "User-Agent: Mozilla/5.0"
        )
        @POST("api")
        suspend fun apiPost(@Body body: okhttp3.RequestBody): String
    }

    override suspend fun getHome(): List<Category> = coroutineScope {
        fun category(name: String, block: suspend () -> List<AppAdapter.Item>) = async {
            Category(
                name = name,
                list = try {
                    block()
                } catch (_: Exception) {
                    emptyList()
                }
            )
        }

        val dynamicTags = try {
            homeTags()
        } catch (_: Exception) {
            fallbackHomeTags
        }

        val newSeries = category("New Series") {
            val now = java.util.Calendar.getInstance()
            searchShows(
                search = mapOf(
                    "season" to currentAnimeSeason(now.get(java.util.Calendar.MONTH) + 1),
                    "year" to now.get(java.util.Calendar.YEAR)
                ),
                limit = HOME_ROW_LIMIT,
                page = 1,
                countryOrigin = "JP"
            )
        }

        val categories = buildList {
            add(category("Latest Updates (Sub/Dub)") {
                searchShows(mapOf("sortBy" to "Recent"), limit = HOME_ROW_LIMIT, page = 1)
            })
            add(newSeries)
            add(category("Random") { randomShows(limit = HOME_ROW_LIMIT) })
            addAll(
                dynamicTags.map { tag ->
                    category(tag.name) {
                        tagShows(
                            slug = tag.slug,
                            name = tag.name,
                            tagType = tag.tagType,
                            limit = HOME_ROW_LIMIT,
                            page = 1
                        )
                    }
                }
            )
            add(category("Trending Activity") { popularByDateRange(dateRange = 1, page = 1, size = HOME_ROW_LIMIT) })
        }

        categories
            .map { it.await() }
            .filter { it.list.isNotEmpty() }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) return genres
        return searchItems(mapOf("query" to query), limit = 26, page = page)
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return searchMovies(page = page)
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return searchShows(mapOf("sortBy" to "Popular", "types" to listOf("TV")), limit = 26, page = page)
    }

    override suspend fun getMovie(id: String): Movie {
        return showDetails(id.removePrefix("movie:")).toMovie()
    }

    override suspend fun getTvShow(id: String): TvShow {
        return showDetails(id.removePrefix("movie:"))
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val parts = seasonId.split("|")
        val showId = parts.firstOrNull().orEmpty()
        val translation = parts.getOrNull(1) ?: "sub"
        val show = showDetails(showId)
        val count = show.seasons.firstOrNull { it.id == seasonId }?.episodes?.size ?: 0
        return buildEpisodes(showId, count, translation)
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val name = id.replace('_', ' ')
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        val shows = tagShows(slug = id, name = name, limit = 26, page = page)
        return Genre(id = id, name = name, shows = shows)
    }

    override suspend fun getPeople(id: String, page: Int): People {
        throw Exception("People pages are not available in MKissa")
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val parts = id.split("|")
        val showId = parts.firstOrNull()?.removePrefix("movie:").orEmpty()
        val episode = parts.getOrNull(1) ?: "1"
        val requestedTranslation = parts.getOrNull(2)

        val detail = showJson(showId)
        val available = detail.optJSONObject("availableEpisodes")
        return translationTypes
            .filter { translation ->
                requestedTranslation == null || requestedTranslation == translation
            }
            .filter { translation ->
                (available?.optInt(translation, 0) ?: if (translation == "sub") 1 else 0) > 0
            }
            .flatMap { translation ->
                val sources = getSourceEntries(showId = showId, episode = episode, translation = translation)
                sources.map { sourceObject ->
                    val sourceName = sourceObject.stringOrNull("sourceName") ?: "MKissa"
                    val sourceUrl = sourceObject.sourceUrl()
                    Video.Server(
                        id = sourceUrl,
                        name = "$sourceName ${translation.uppercase()}".trim(),
                        src = sourceUrl
                    )
                }
            }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val source = resolveSourceUrl(server.src.ifBlank { server.id })
            ?: throw Exception("Selected MKissa source could not be resolved")

        if (source.contains(".m3u8", ignoreCase = true) || source.contains(".mp4", ignoreCase = true)) {
            return Video(
                source = source,
                headers = directPlaybackHeaders()
            )
        }

        return Extractor.extract(source, server)
    }

    private suspend fun getSourceEntries(showId: String, episode: String, translation: String): List<JSONObject> {
        val response = api(
            variables = JSONObject()
                .put("showId", showId)
                .put("translationType", translation)
                .put("episodeString", episode),
            hash = SOURCE_HASH,
            fallbackQuery = SOURCE_QUERY
        )
        var data = response.optJSONObject("data") ?: JSONObject()
        if (data.has("tobeparsed")) {
            data = decryptTobeParsed(data.optString("tobeparsed"))
        }

        return sequenceOf(
            data.optJSONArray("sourceUrls"),
            data.optJSONObject("episode")?.optJSONArray("sourceUrls")
        )
            .filterNotNull()
            .flatMap { it.asSequence() }
            .mapNotNull { it as? JSONObject }
            .filter { it.sourceUrl().isNotBlank() }
//            .filterNot { it.isKnownDeadEmbedSource() }
            .toList()
    }

    private suspend fun popularShows(page: Int, size: Int): List<TvShow> {
        val variables = JSONObject()
            .put(
                "search",
                JSONObject()
                    .put("page", page)
                    .put("size", size)
                    .put("sortBy", "Popular")
                    .put("allowAdult", false)
                    .put("allowUnknown", false)
            )
        return parseShows(api(variables, POPULAR_HASH, SEARCH_QUERY))
    }

    private suspend fun popularByDateRange(dateRange: Int, page: Int, size: Int): List<TvShow> {
        val variables = JSONObject()
            .put("type", "anime")
            .put("size", size)
            .put("dateRange", dateRange)
            .put("page", page)
            .put("allowAdult", false)
            .put("allowUnknown", false)
        return parsePopular(api(variables, POPULAR_DAILY_HASH, POPULAR_DAILY_QUERY))
    }

    private suspend fun tagShows(
        slug: String,
        name: String,
        tagType: String? = null,
        limit: Int = HOME_ROW_LIMIT,
        page: Int = 1
    ): List<TvShow> {
        val search = JSONObject()
            .put("slug", slug)
            .put("format", "anime")
            .put("page", page)
            .put("limit", limit)
            .put("name", name)
            .put("allowAdult", false)
            .put("allowUnknown", false)
        if (!tagType.isNullOrBlank()) search.put("tagType", tagType.normalizedTagType())
        val variables = JSONObject().put("search", search)
        return parseShows(api(variables, GENRE_HASH, TAG_QUERY))
    }

    private suspend fun homeTags(): List<HomeTag> {
        val variables = JSONObject()
            .put("page", 1)
            .put("limit", HOME_TAG_LIMIT)
            .put(
                "search",
                JSONObject()
                    .put("format", "anime")
                    .put("sortBy", "Recommendation")
                    .put("allowAdult", false)
                    .put("allowUnknown", false)
            )
        val response = postQuery(TAGS_QUERY, variables)
        val edges = response.optJSONObject("data")
            ?.optJSONObject("queryTags")
            ?.optJSONArray("edges")
            ?: JSONArray()
        return edges.asSequence()
            .mapNotNull { it as? JSONObject }
            .mapNotNull { tag ->
                val slug = tag.stringOrNull("slug") ?: tag.stringOrNull("name")?.slugify() ?: return@mapNotNull null
                val name = tag.stringOrNull("name") ?: return@mapNotNull null
                if (slug == "movie-anime") return@mapNotNull null
                HomeTag(
                    slug = slug,
                    name = name,
                    tagType = tag.stringOrNull("tagType")?.normalizedTagType()
                )
            }
            .distinctBy { it.slug }
            .toList()
    }

    private suspend fun randomShows(limit: Int): List<TvShow> {
        val response = postQuery(
            RANDOM_QUERY,
            JSONObject()
                .put("format", "anime")
                .put("allowAdult", false)
        )
        val items = response.optJSONObject("data")
            ?.optJSONArray("queryRandomRecommendation")
            ?: JSONArray()
        return items.asSequence()
            .mapNotNull { it as? JSONObject }
            .mapNotNull { it.toTvShow(detailed = false) }
            .take(limit)
            .toList()
    }

    private suspend fun searchShows(
        search: Map<String, Any?>,
        limit: Int,
        page: Int,
        countryOrigin: String? = null,
        hash: String = SEARCH_HASH
    ): List<TvShow> {
        val shows = buildList {
            for (translation in browseTranslationTypes) {
                val variables = JSONObject()
                    .put("search", JSONObject(search))
                    .put("limit", limit)
                    .put("page", page)
                    .put("translationType", translation)
                    .put("allowAdult", false)
                if (countryOrigin != null) variables.put("countryOrigin", countryOrigin)
                addAll(parseShows(api(variables, hash, SEARCH_QUERY)))
            }
        }
        return shows
            .distinctBy { it.id }
            .take(limit)
    }

    private suspend fun searchItems(
        search: Map<String, Any?>,
        limit: Int,
        page: Int
    ): List<AppAdapter.Item> {
        val items = buildList {
            for (translation in browseTranslationTypes) {
                val variables = JSONObject()
                    .put("search", JSONObject(search))
                    .put("limit", limit)
                    .put("page", page)
                    .put("translationType", translation)
                    .put("allowAdult", false)
                addAll(parseSearchItems(api(variables, SEARCH_HASH, SEARCH_QUERY)))
            }
        }
        return items
            .distinctBy { item ->
                when (item) {
                    is Movie -> item.id
                    is TvShow -> item.id
                    else -> item.itemType
                }
            }
            .take(limit)
    }

    private suspend fun searchMovies(page: Int, limit: Int = 26): List<Movie> {
        val movies = buildList {
            for (translation in browseTranslationTypes) {
                val variables = JSONObject()
                    .put("search", JSONObject(mapOf("sortBy" to "Popular", "types" to listOf("Movie"))))
                    .put("limit", limit)
                    .put("page", page)
                    .put("translationType", translation)
                    .put("allowAdult", false)
                addAll(
                    showEdges(api(variables, SEARCH_HASH, SEARCH_QUERY))
                        .asSequence()
                        .mapNotNull { it as? JSONObject }
                        .mapNotNull { it.toMovieOrNull(forceMovie = true) }
                        .toList()
                )
            }
        }
        return movies
            .distinctBy { it.id }
            .take(limit)
    }

    private suspend fun showDetails(id: String): TvShow {
        val show = showJson(id)
        return show.toTvShow(detailed = true) ?: throw Exception("MKissa show is missing required metadata")
    }

    private suspend fun showJson(id: String): JSONObject {
        val show = api(JSONObject().put("_id", id), DETAIL_HASH, DETAIL_QUERY)
            .optJSONObject("data")
            ?.optJSONObject("show")
            ?: throw Exception("MKissa show not found")
        if (show.isAdultContent()) throw Exception("MKissa show not found")
        return show
    }

    private suspend fun api(variables: JSONObject, hash: String, fallbackQuery: String? = null): JSONObject {
        val extensions = JSONObject()
            .put("persistedQuery", JSONObject().put("version", 1).put("sha256Hash", hash))
        val response = try {
            JSONObject(service.api(variables.toString(), extensions.toString()))
        } catch (error: HttpException) {
            if (fallbackQuery == null) throw error
            null
        }
        if (response != null && !response.shouldRetryWithQueryBody()) return response
        if (fallbackQuery == null) return response ?: JSONObject()
        val body = JSONObject()
            .put("query", fallbackQuery)
            .put("variables", variables)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        return JSONObject(service.apiPost(body))
    }

    private suspend fun postQuery(query: String, variables: JSONObject): JSONObject {
        val body = JSONObject()
            .put("query", query)
            .put("variables", variables)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        return JSONObject(service.apiPost(body))
    }

    private fun parseShows(response: JSONObject): List<TvShow> {
        return showEdges(response)
            .mapNotNull { it as? JSONObject }
            .filterNot { it.isAdultContent() }
            .mapNotNull { it.toTvShow(detailed = false) }
            .toList()
    }

    private fun parseSearchItems(response: JSONObject): List<AppAdapter.Item> {
        val items = showEdges(response)
            .mapNotNull { it as? JSONObject }
            .filterNot { it.isAdultContent() }
            .mapNotNull { show ->
                val title = show.displayTitleOrNull() ?: "?"
                val type = show.stringOrNull("type") ?: "?"
                val genres = (0 until (show.optJSONArray("genres")?.length() ?: 0))
                    .map { show.optJSONArray("genres")!!.optString(it) }
                    .joinToString(",")
                val isAdult = show.opt("isAdult")
                Log.d(TAG, "RAW show: _id=${show.stringOrNull("_id")}, title=$title, type=$type, genres=[$genres], isAdult=$isAdult")
                if (show.stringOrNull("type").equals("Movie", ignoreCase = true)) {
                    show.toMovieOrNull(forceMovie = true)
                } else {
                    show.toTvShow(detailed = false)
                }
            }
            .toList()
        Log.d(TAG, "parseSearchItems: ${items.size} items parsed from search")
        items.forEach { item ->
            when (item) {
                is Movie -> Log.d(TAG, "  Movie: ${item.title} (genres: ${item.genres.map { it.name }})")
                is TvShow -> Log.d(TAG, "  TvShow: ${item.title} (genres: ${item.genres.map { it.name }})")
                else -> Log.d(TAG, "  Other: ${item.itemType}")
            }
        }
        return items
    }

    private fun showEdges(response: JSONObject): Sequence<Any?> {
        val edges = response.optJSONObject("data")
            ?.optJSONObject("shows")
            ?.optJSONArray("edges")
            ?: response.optJSONObject("data")
                ?.optJSONObject("queryListForTag")
                ?.optJSONArray("edges")
            ?: JSONArray()
        return edges.asSequence()
    }

    private fun JSONObject.shouldRetryWithQueryBody(): Boolean {
        val errors = optJSONArray("errors") ?: return false
        return errors.asSequence()
            .mapNotNull { it as? JSONObject }
            .any { error ->
                error.optString("message").contains("PersistedQueryNotFound", ignoreCase = true) ||
                    error.optString("message").contains("PersistedQueryNotSupported", ignoreCase = true) ||
                    error.optJSONObject("extensions")
                        ?.optString("code")
                        ?.contains("PERSISTED_QUERY", ignoreCase = true) == true
            }
    }

    private fun parsePopular(response: JSONObject): List<TvShow> {
        val recommendations = response.optJSONObject("data")
            ?.optJSONObject("queryPopular")
            ?.optJSONArray("recommendations")
            ?: JSONArray()
        return recommendations.asSequence()
            .mapNotNull { (it as? JSONObject)?.optJSONObject("anyCard") }
            .mapNotNull { it.toTvShow(detailed = false) }
            .toList()
    }

    private fun JSONObject.toTvShow(detailed: Boolean): TvShow? {
        if (isAdultContent()) return null
        val rawId = stringOrNull("_id") ?: return null
        val isMovie = stringOrNull("type").equals("Movie", ignoreCase = true)
        val id = if (isMovie) "movie:$rawId" else rawId
        val title = displayTitleOrNull() ?: return null
        val overview = stringOrNull("description")?.let { Jsoup.parse(it).text() }
        val availableEpisodes = availableEpisodeTranslation(isMovie = isMovie)
        val runtime = stringOrNull("episodeDuration")?.toLongOrNull()?.let { (it / 60000L).toInt() }

        return TvShow(
            id = id,
            title = title,
            overview = overview,
            released = dateString(optJSONObject("airedStart")),
            runtime = runtime,
            rating = optDoubleOrNull("score"),
            poster = imageUrl(stringOrNull("thumbnail")),
            banner = imageUrl(stringOrNull("banner")),
            genres = optJSONArray("genres")?.asSequence()
                ?.mapNotNull { it as? String }
                ?.map { Genre(id = it.lowercase().replace(" ", "_"), name = it) }
                ?.toList()
                ?: emptyList(),
            seasons = if (availableEpisodes != null) {
                listOf(
                    Season(
                        id = "$rawId|${availableEpisodes.translation}",
                        number = 1,
                        title = "Episodes",
                        episodes = buildEpisodes(rawId, availableEpisodes.count, availableEpisodes.translation)
                    )
                )
            } else {
                emptyList()
            }
        )
    }

    private fun JSONObject.availableEpisodeTranslation(isMovie: Boolean): AvailableEpisodes? {
        return translationTypes
            .firstNotNullOfOrNull { translation ->
                val count = availableEpisodeCount(translation = translation, isMovie = isMovie)
                if (count > 0) AvailableEpisodes(translation = translation, count = count) else null
            }
    }

    private fun JSONObject.availableEpisodeCount(translation: String, isMovie: Boolean): Int {
        val available = optJSONObject("availableEpisodes")
        if (available != null && available.has(translation) && !available.isNull(translation)) {
            return available.optInt(translation, 0).coerceAtLeast(0)
        }

        optJSONObject("availableEpisodesDetail")
            ?.optJSONArray(translation)
            ?.let { return it.length().coerceAtLeast(0) }

        return stringOrNull("episodeCount")?.toIntOrNull()?.coerceAtLeast(0)
            ?: optJSONObject("lastEpisodeInfo")
                ?.optJSONObject(translation)
                ?.optString("episodeString")
                ?.toIntOrNull()
                ?.coerceAtLeast(0)
            ?: if (isMovie) 1 else 0
    }

    private fun JSONObject.toMovieOrNull(forceMovie: Boolean = false): Movie? {
        if (isAdultContent()) return null
        val rawId = stringOrNull("_id") ?: return null
        val isMovie = stringOrNull("type").equals("Movie", ignoreCase = true)
        if (!forceMovie && !isMovie) return null
        val title = displayTitleOrNull() ?: return null
        val overview = stringOrNull("description")?.let { Jsoup.parse(it).text() }
        val runtime = stringOrNull("episodeDuration")?.toLongOrNull()?.let { (it / 60000L).toInt() }
        return Movie(
            id = "movie:$rawId",
            title = title,
            overview = overview,
            released = dateString(optJSONObject("airedStart")),
            runtime = runtime,
            rating = optDoubleOrNull("score"),
            poster = imageUrl(stringOrNull("thumbnail")),
            banner = imageUrl(stringOrNull("banner")),
            genres = optJSONArray("genres")?.asSequence()
                ?.mapNotNull { it as? String }
                ?.map { Genre(id = it.lowercase().replace(" ", "_"), name = it) }
                ?.toList()
                ?: emptyList()
        )
    }

    private fun JSONObject.displayTitleOrNull(): String? {
        return stringOrNull("englishName")
            ?: stringOrNull("name")
            ?: stringOrNull("nativeName")
            ?: firstStringOrNull("altNames")
            ?: stringOrNull("nameOnlyString")?.humanizeSlug()
            ?: stringOrNull("slugTime")?.humanizeSlug()
    }

    private fun TvShow.toMovie(): Movie {
        return Movie(
            id = id,
            title = title,
            overview = overview,
            released = released?.let { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(it.time) },
            runtime = runtime,
            rating = rating,
            poster = poster,
            banner = banner,
            genres = genres
        )
    }

    private fun buildEpisodes(showId: String, count: Int, translation: String): List<Episode> {
        if (count <= 0) return emptyList()
        return (1..count).map { number ->
            Episode(
                id = listOf(showId, number.toString(), translation).joinToString("|"),
                number = number,
                title = "Episode $number"
            )
        }
    }

    private fun imageUrl(value: String?): String? {
        val image = value?.takeIf { it.isNotBlank() } ?: return null
        val url = when {
            image.contains("/_tbs/") || image.contains("_tbs/") -> image
                .removePrefix("https://wp.youtube-anime.com/")
                .removePrefix("https://aln.youtube-anime.com/")
                .removePrefix("/")
                .substringBefore("?")
                .let { "$IMAGE_URL/$it?w=250" }
            image.startsWith("http") -> image
            image.startsWith("//") -> "https:$image"
            image.startsWith("images") -> "$IMAGE_URL/$image?w=250"
            else -> "$IMAGE_URL/images/$image?w=250"
        }
        return if (url.contains("youtube-anime.com")) {
            ArtworkRequestHeaders.withHeaders(
                url = url,
                referer = baseUrl,
                origin = "https://mkissa.to",
                userAgent = "Mozilla/5.0",
                accept = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
            )
        } else {
            url
        }
    }

    private fun dateString(date: JSONObject?): String? {
        val year = date?.optInt("year", 0)?.takeIf { it > 0 } ?: return null
        val month = date.optInt("month", 1).coerceIn(1, 12)
        val day = date.optInt("date", 1).coerceIn(1, 31)
        return "%04d-%02d-%02d".format(year, month, day)
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        return if (has(key) && !isNull(key)) optDouble(key) else null
    }

    private fun JSONObject.isAdultContent(): Boolean {
        if (!has("isAdult") || isNull("isAdult")) return false
        return when (val value = opt("isAdult")) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> false
        }
    }

    private fun JSONObject.sourceUrl(): String {
        return stringOrNull("sourceUrl")
            ?: stringOrNull("url")
            ?: stringOrNull("source")
            ?: ""
    }

    private fun JSONObject.isKnownDeadEmbedSource(): Boolean {
        val source = sourceUrl().lowercase()
        return source.contains("streamsb.net") ||
            source.contains("streamlare.com")
    }

    private suspend fun resolveSourceUrl(value: String): String? {
        if (value.isBlank()) return null

        val decoded = decodePackedSourceUrl(value)
        val normalized = when {
            decoded.startsWith("//") -> "https:$decoded"
            decoded.startsWith("http", ignoreCase = true) -> decoded
            decoded.startsWith("/apivtwo/", ignoreCase = true) -> resolveAllanimeClockSource(decoded)
            else -> decoded.takeIf { it.isNotBlank() }
        }

        return normalized?.takeIf { it.isNotBlank() }
    }

    private fun decodePackedSourceUrl(value: String): String {
        if (!value.startsWith("--")) return value

        val bytes = runCatching {
            value.removePrefix("--")
                .chunked(2)
                .map { pair -> pair.toInt(16).xor(56).toByte() }
                .toByteArray()
        }.getOrNull() ?: return value

        return bytes.toString(Charsets.UTF_8).trim()
    }

    private suspend fun resolveAllanimeClockSource(path: String): String? {
        val normalizedPath = path.replace("/apivtwo/clock?", "/apivtwo/clock.json?")
        val request = Request.Builder()
            .url("$CLOCK_URL$normalizedPath")
            .header("Accept", "application/json")
            .header("Origin", CLOCK_URL)
            .header("Referer", "$CLOCK_URL/player.html")
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val body = sourceResolverClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        } ?: return null

        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val links = json.optJSONArray("links") ?: return null

        for (i in 0 until links.length()) {
            val linkObject = links.optJSONObject(i) ?: continue
            val link = linkObject.stringOrNull("link")
                ?: linkObject.stringOrNull("url")
                ?: linkObject.stringOrNull("sourceUrl")
                ?: linkObject.stringOrNull("file")
            if (!link.isNullOrBlank()) return link
        }

        return null
    }

    private fun directPlaybackHeaders(): Map<String, String> {
        return mapOf(
            "Accept" to "*/*",
            "Origin" to CLOCK_URL,
            "Referer" to "$CLOCK_URL/",
            "User-Agent" to "Mozilla/5.0"
        )
    }

    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key)
            .trim()
            .takeUnless {
                it.isBlank() ||
                    it.equals("null", ignoreCase = true) ||
                    it.equals("undefined", ignoreCase = true)
            }
    }

    private fun JSONObject.firstStringOrNull(key: String): String? {
        val values = optJSONArray(key) ?: return null
        return values.asSequence()
            .mapNotNull { it as? String }
            .map { it.trim() }
            .firstOrNull {
                it.isNotBlank() &&
                    !it.equals("null", ignoreCase = true) &&
                    !it.equals("undefined", ignoreCase = true)
            }
    }

    private fun decryptTobeParsed(value: String): JSONObject {
        val bytes = Base64.decode(value, Base64.DEFAULT)
        if (bytes.isEmpty()) throw Exception("Empty MKissa encrypted payload")
        val version = bytes[0].toInt()
        if (version != 1) throw Exception("Unsupported MKissa encryption version: $version")

        val iv = bytes.copyOfRange(1, 13)
        val cipherText = bytes.copyOfRange(13, bytes.size)
        val key = MessageDigest.getInstance("SHA-256")
            .digest("Xot36i3lK3:v$version".toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return JSONObject(String(cipher.doFinal(cipherText), Charsets.UTF_8))
    }

    private fun JSONArray.asSequence(): Sequence<Any?> = sequence {
        for (i in 0 until length()) yield(opt(i))
    }

    private fun currentAnimeSeason(month: Int): String {
        return when (month) {
            1, 2, 3 -> "Winter"
            4, 5, 6 -> "Spring"
            7, 8, 9 -> "Summer"
            else -> "Fall"
        }
    }

    private fun String.normalizedTagType(): String {
        return when (this) {
            "genre", "tag" -> "generic"
            "all" -> ""
            else -> this
        }
    }

    private fun String.slugify(): String {
        return lowercase()
            .replace("'", "")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    private fun String.humanizeSlug(): String {
        return replace('_', ' ')
            .replace('-', ' ')
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
            .takeIf { it.isNotBlank() }
            ?: this
    }

    private data class HomeTag(
        val slug: String,
        val name: String,
        val tagType: String? = null
    )

    private data class AvailableEpisodes(
        val translation: String,
        val count: Int
    )

    private val fallbackHomeTags = listOf(
        "Isekai",
        "Boys' Love",
        "Female Harem",
        "Yuri",
        "Reincarnation",
        "Male Protagonist",
        "Overpowered Protagonist",
        "Yandere",
        "Gyaru",
        "Cultivation",
        "Female Protagonist",
        "Full Color",
        "Magic",
        "Anti-Hero",
        "School",
        "POV",
        "Post-Apocalyptic",
        "Succubus",
        "Primarily Adult Cast",
        "Gender Bending"
    ).map { HomeTag(slug = it.slugify(), name = it) }

    private val genres = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Isekai", "Magic", "Mystery",
        "Romance", "School", "Sci-Fi", "Seinen", "Shoujo", "Shounen", "Slice of Life",
        "Sports", "Super Power", "Supernatural", "Thriller"
    ).map { Genre(id = it.lowercase().replace(" ", "_"), name = it) }
}
