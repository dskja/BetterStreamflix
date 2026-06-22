package com.streamflixreborn.streamflix.providers

import android.util.Base64
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
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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

    private const val API_URL = "https://api.allanime.day/"
    private const val SEARCH_HASH = "a24c500a1b765c68ae1d8dd85174931f661c71369c89b92b88b75a725afc471c"
    private const val POPULAR_DAILY_HASH = "a0aca6827cc9a3ad7bc711da4d200a04adea8f1a7545dc418d5e92e74c3aad15"
    private const val POPULAR_HASH = "ac2c75884a11fca5707ce4ad10f2e3e2aae31e42af5e4d9c511a4a5e708e4c6d"
    private const val DETAIL_HASH = "043448386c7a686bc2aabfbb6b80f6074e795d350df48015023b079527b0848a"
    private const val SOURCE_HASH = "d405d0edd690624b66baba3068e0edc3ac90f1597d898a1ec8db4e5c43c00fec"
    private const val GENRE_HASH = "ff61a63ff776f334f80c1e6ad1aa49ef71eab831e235e5d6ec679eae5b83450f"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val SHOW_FIELDS = """
        _id
        type
        englishName
        name
        nativeName
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
    """.trimIndent()

    private val SEARCH_QUERY = """
        query(
          ${'$'}search: SearchInput
          ${'$'}limit: Int
          ${'$'}page: Int
          ${'$'}translationType: VaildTranslationTypeEnumType
          ${'$'}countryOrigin: VaildCountryOriginEnumType
        ) {
          shows(
            search: ${'$'}search
            limit: ${'$'}limit
            page: ${'$'}page
            translationType: ${'$'}translationType
            countryOrigin: ${'$'}countryOrigin
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

    private val DETAIL_QUERY = """
        query(${ '$' }_id: String!) {
          show(_id: ${ '$' }_id) {
            $SHOW_FIELDS
            status
            altNames
            averageScore
            rating
            airedEnd
            studios
            countryOfOrigin
            availableEpisodesDetail
            nameOnlyString
            isAdult
            tags
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

        val currentSeason = category("This Season") {
            val now = java.util.Calendar.getInstance()
            searchShows(
                search = mapOf(
                    "season" to currentAnimeSeason(now.get(java.util.Calendar.MONTH) + 1),
                    "year" to now.get(java.util.Calendar.YEAR)
                ),
                limit = 12,
                page = 1,
                countryOrigin = "JP"
            )
        }

        listOf(
            category(Category.FEATURED) { popularShows(page = 1, size = 12).take(10) },
            category("Popular Today") { popularByDateRange(dateRange = 1, page = 1, size = 12) },
            category("Recently Updated") { searchShows(mapOf("sortBy" to "Recent"), limit = 12, page = 1) },
            currentSeason,
            category("TV Anime") { searchShows(mapOf("sortBy" to "Popular", "types" to listOf("TV")), limit = 12, page = 1) },
            category("Anime Movies") { searchShows(mapOf("sortBy" to "Popular", "types" to listOf("Movie")), limit = 12, page = 1) },
            category("OVAs") { searchShows(mapOf("sortBy" to "Popular", "types" to listOf("OVA")), limit = 12, page = 1) },
            category("ONAs") { searchShows(mapOf("sortBy" to "Popular", "types" to listOf("ONA")), limit = 12, page = 1) },
            category("Specials") { searchShows(mapOf("sortBy" to "Popular", "types" to listOf("Special")), limit = 12, page = 1) },
            category("Shounen Action") {
                searchShows(
                    search = mapOf(
                        "genres" to listOf(
                            "Action", "Adventure", "Comedy", "Super Power", "Drama",
                            "Fantasy", "Shounen", "Samurai", "Isekai"
                        )
                    ),
                    limit = 12,
                    page = 1
                )
            },
            category("Isekai") { tagShows(slug = "isekai", name = "Isekai") },
            category("Magic") { tagShows(slug = "magic", name = "Magic") },
            category("School") { tagShows(slug = "school", name = "School") },
            category("Reincarnation") { tagShows(slug = "reincarnation", name = "Reincarnation") },
            category("Female Protagonist") { tagShows(slug = "female_protagonist", name = "Female Protagonist") },
            category("Anti-Hero") { tagShows(slug = "anti_hero", name = "Anti-Hero") }
        )
            .map { it.await() }
            .filter { it.list.isNotEmpty() }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) return genres
        return searchShows(mapOf("query" to query), limit = 26, page = page)
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return searchShows(mapOf("sortBy" to "Popular"), limit = 50, page = page)
            .filter { it.id.startsWith("movie:") }
            .map { it.toMovie() }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return searchShows(mapOf("sortBy" to "Popular"), limit = 26, page = page)
            .filterNot { it.id.startsWith("movie:") }
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
        return listOf("sub", "dub")
            .filter { translation ->
                requestedTranslation == null || requestedTranslation == translation
            }
            .filter { translation ->
                (available?.optInt(translation, 0) ?: if (translation == "sub") 1 else 0) > 0
            }
            .map { translation ->
                Video.Server(
                    id = listOf(showId, episode, translation).joinToString("|"),
                    name = if (translation == "dub") "MKissa Dub" else "MKissa Sub"
                )
            }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val parts = server.id.split("|")
        val showId = parts.getOrNull(0).orEmpty()
        val episode = parts.getOrNull(1) ?: "1"
        val translation = parts.getOrNull(2) ?: "sub"
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

        val sources = sequenceOf(
            data.optJSONArray("sourceUrls"),
            data.optJSONObject("episode")?.optJSONArray("sourceUrls")
        )
            .filterNotNull()
            .flatMap { it.asSequence() }
            .mapNotNull { it as? JSONObject }
            .filter {
                it.sourceUrl().let { url -> url.isNotBlank() && !url.startsWith("--") }
            }
            .toList()

        if (sources.isEmpty()) throw Exception("No playable MKissa source found")

        var lastError: Exception? = null
        for (sourceObject in sources.sortedByDescending { it.optDouble("priority", 0.0) }) {
            val source = sourceObject.sourceUrl()
            val sourceServer = server.copy(name = sourceObject.optString("sourceName").ifBlank { server.name })
            try {
                if (source.contains(".m3u8", ignoreCase = true) || source.contains(".mp4", ignoreCase = true)) {
                    return Video(
                        source = source,
                        headers = mapOf(
                            "Referer" to baseUrl,
                            "User-Agent" to "Mozilla/5.0"
                        )
                    )
                }
                return Extractor.extract(source, sourceServer)
            } catch (error: Exception) {
                lastError = error
            }
        }

        throw Exception("No MKissa source could be extracted", lastError)
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

    private suspend fun tagShows(slug: String, name: String, limit: Int = 10, page: Int = 1): List<TvShow> {
        val variables = JSONObject()
            .put(
                "search",
                JSONObject()
                    .put("slug", slug)
                    .put("format", "anime")
                    .put("page", page)
                    .put("limit", limit)
                    .put("name", name)
            )
        return parseShows(api(variables, GENRE_HASH, TAG_QUERY))
    }

    private suspend fun searchShows(
        search: Map<String, Any?>,
        limit: Int,
        page: Int,
        countryOrigin: String? = null,
        hash: String = SEARCH_HASH
    ): List<TvShow> {
        val variables = JSONObject()
            .put("search", JSONObject(search))
            .put("limit", limit)
            .put("page", page)
            .put("translationType", "sub")
        if (countryOrigin != null) variables.put("countryOrigin", countryOrigin)
        return parseShows(api(variables, hash, SEARCH_QUERY))
    }

    private suspend fun showDetails(id: String): TvShow {
        val show = showJson(id)
        return show.toTvShow(detailed = true)
    }

    private suspend fun showJson(id: String): JSONObject {
        return api(JSONObject().put("_id", id), DETAIL_HASH, DETAIL_QUERY)
            .optJSONObject("data")
            ?.optJSONObject("show")
            ?: throw Exception("MKissa show not found")
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

    private fun parseShows(response: JSONObject): List<TvShow> {
        val edges = response.optJSONObject("data")
            ?.optJSONObject("shows")
            ?.optJSONArray("edges")
            ?: response.optJSONObject("data")
                ?.optJSONObject("queryListForTag")
                ?.optJSONArray("edges")
            ?: JSONArray()
        return edges.asSequence()
            .mapNotNull { it as? JSONObject }
            .map { it.toTvShow(detailed = false) }
            .toList()
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
            .map { it.toTvShow(detailed = false) }
            .toList()
    }

    private fun JSONObject.toTvShow(detailed: Boolean): TvShow {
        val rawId = optString("_id")
        val isMovie = optString("type").equals("Movie", ignoreCase = true)
        val id = if (isMovie) "movie:$rawId" else rawId
        val title = optString("englishName")
            .ifBlank { optString("name") }
            .ifBlank { optString("nativeName") }
        val overview = optString("description").takeIf { it.isNotBlank() }?.let { Jsoup.parse(it).text() }
        val episodeCount = optJSONObject("availableEpisodes")?.optInt("sub", 0)
            ?.takeIf { it > 0 }
            ?: optString("episodeCount").toIntOrNull()
            ?: optJSONObject("lastEpisodeInfo")?.optJSONObject("sub")?.optString("episodeString")?.toIntOrNull()
            ?: if (isMovie) 1 else 0
        val runtime = optString("episodeDuration").toLongOrNull()?.let { (it / 60000L).toInt() }

        return TvShow(
            id = id,
            title = title,
            overview = overview,
            released = dateString(optJSONObject("airedStart")),
            runtime = runtime,
            rating = optDoubleOrNull("score"),
            poster = imageUrl(optString("thumbnail")),
            banner = imageUrl(optString("banner")),
            genres = optJSONArray("genres")?.asSequence()
                ?.mapNotNull { it as? String }
                ?.map { Genre(id = it.lowercase().replace(" ", "_"), name = it) }
                ?.toList()
                ?: emptyList(),
            seasons = if (detailed || episodeCount > 0) {
                listOf(
                    Season(
                        id = "$rawId|sub",
                        number = 1,
                        title = "Episodes",
                        episodes = buildEpisodes(rawId, episodeCount, "sub")
                    )
                )
            } else {
                emptyList()
            }
        )
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
        return (1..count.coerceAtLeast(1)).map { number ->
            Episode(
                id = listOf(showId, number.toString(), translation).joinToString("|"),
                number = number,
                title = "Episode $number"
            )
        }
    }

    private fun imageUrl(value: String?): String? {
        val image = value?.takeIf { it.isNotBlank() } ?: return null
        return when {
            image.startsWith("http") -> image
            image.startsWith("//") -> "https:$image"
            else -> "https://wp.youtube-anime.com/$image?w=250"
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

    private fun JSONObject.sourceUrl(): String {
        return optString("sourceUrl")
            .ifBlank { optString("url") }
            .ifBlank { optString("source") }
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

    private val genres = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Isekai", "Magic", "Mystery",
        "Romance", "School", "Sci-Fi", "Seinen", "Shoujo", "Shounen", "Slice of Life",
        "Sports", "Super Power", "Supernatural", "Thriller"
    ).map { Genre(id = it.lowercase().replace(" ", "_"), name = it) }
}
