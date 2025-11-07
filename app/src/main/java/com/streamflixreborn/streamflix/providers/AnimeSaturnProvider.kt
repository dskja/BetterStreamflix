package com.streamflixreborn.streamflix.providers

import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import retrofit2.http.Body
import retrofit2.http.POST
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AnimeSaturnProvider : Provider {
    override val name = "AnimeSaturn"
    override val baseUrl = "https://www.animesaturn.cx"
    
    override val logo = "https://www.animesaturn.cx/immagini/PlanetAS.png"
    override val language = "it"

    private const val USER_AGENT = "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private interface AnimeSaturnService {
        companion object {
            fun build(baseUrl: String): AnimeSaturnService {
                val clientBuilder = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)

                val client = clientBuilder.dns(DnsResolver.doh).build()

                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)
                    .build()
                    .create(AnimeSaturnService::class.java)
            }
        }

        @Headers(USER_AGENT)
        @GET(".")
        suspend fun getHome(): Document

        @Headers(USER_AGENT)
        @GET("anime/{id}")
        suspend fun getAnime(@Path("id") id: String): Document

        @Headers(USER_AGENT)
        @GET
        suspend fun getEpisodeByUrl(@Url url: String): ResponseBody

        @Headers(USER_AGENT)
        @GET("filter")
        suspend fun getFilter(): Document

        @Headers(USER_AGENT)
        @GET("filter")
        suspend fun getGenre(@Query("categories[0]") categoryId: String, @Query("page") page: Int? = null): Document

        @Headers(USER_AGENT)
        @GET("filter")
        suspend fun getTvShows(@Query("page") page: Int? = null): Document

        @Headers(USER_AGENT)
        @GET("animelist")
        suspend fun getSearch(@Query("search") query: String, @Query("page") page: Int? = null): Document
    }

    private interface KitsuService {
        @POST("graphql")
        suspend fun getEpisodes(@Body body: okhttp3.RequestBody): okhttp3.ResponseBody
    }

    private val service = AnimeSaturnService.build(baseUrl)
    
    private val kitsuService by lazy {
        val client = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
            
        Retrofit.Builder()
            .baseUrl("https://kitsu.io/api/")
            .client(client)
            .build()
            .create(KitsuService::class.java)
    }

    private data class EpisodeExtra(
        val thumbnail: String?,
        val title: String?
    )

    private suspend fun fetchEpisodeThumbnails(anilistId: Int): Map<Int, EpisodeExtra> {
        return try {
            val query = """
                query {
                  lookupMapping(externalId: $anilistId, externalSite: ANILIST_ANIME) {
                    __typename
                    ... on Anime {
                      id
                      episodes(first: 2000) {
                        nodes {
                          number
                          titles { canonical }
                          thumbnail {
                            original {
                              url
                            }
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent()
            
            val requestBody = JSONObject().apply {
                put("query", query)
            }.toString().toRequestBody("application/json".toMediaType())
            
            val response = kitsuService.getEpisodes(requestBody)
            val responseString = response.string()
            val jsonResponse = JSONObject(responseString)
            
            val episodes = jsonResponse
                .optJSONObject("data")
                ?.optJSONObject("lookupMapping")
                ?.optJSONObject("episodes")
                ?.optJSONArray("nodes")
                ?: return emptyMap()
            
            val extras = mutableMapOf<Int, EpisodeExtra>()
            for (i in 0 until episodes.length()) {
                try {
                    val episode = episodes.optJSONObject(i) ?: continue
                    val number = episode.optInt("number", 0)
                    val thumbnail = episode
                        .optJSONObject("thumbnail")
                        ?.optJSONObject("original")
                        ?.optString("url", "")
                        ?: ""
                    val canonicalTitle = episode
                        .optJSONObject("titles")
                        ?.optString("canonical", "")
                        ?: ""
                    
                    if (number > 0) {
                        extras[number] = EpisodeExtra(
                            thumbnail = thumbnail.takeIf { it.isNotEmpty() },
                            title = canonicalTitle.takeIf { it.isNotEmpty() }
                        )
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            
            extras
        } catch (e: Exception) {
            emptyMap()
        }
    }

    override suspend fun getHome(): List<Category> {
        return try {
            val document = service.getHome()
            
            val categories = mutableListOf<Category>()
            
            val featuredAnimes = document.select(".carousel-item").mapNotNull { parseFeaturedAnime(it) }
            if (featuredAnimes.isNotEmpty()) {
                categories.add(Category(Category.FEATURED, featuredAnimes))
            }
            
            val sections = document.select("div.container.p-3.shadow.rounded.bg-dark-as-box")
            sections.forEach { section ->
                val titleElement = section.selectFirst("h4 .saturn-title-special")
                val title = titleElement?.text()?.trim() ?: ""
                
                val iconElement = titleElement?.selectFirst("i")
                val iconClass = iconElement?.attr("class") ?: ""
                
                if (iconClass.contains("bi-plus-lg") || iconClass.contains("bi-shuffle")) {
                    val animes = section.select(".anime-card-newanime").mapNotNull { parseNewAnime(it) }
                    
                    if (animes.isNotEmpty()) {
                        categories.add(Category(title, animes))
                    }
                }
            }
            
            categories
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseFeaturedAnime(element: Element): TvShow? {
        return try {
            val banner = element.selectFirst("img.d-block.w-100")?.attr("src") ?: return null
            
            val captionLink = element.selectFirst(".carousel-caption a") ?: return null
            val animeId = captionLink.attr("href").substringAfterLast("/")
            val title = captionLink.text().trim()
            
            TvShow(
                id = animeId,
                title = title,
                banner = banner
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseNewAnime(element: Element): TvShow? {
        return try {
            val linkElement = element.selectFirst("a[href]") ?: return null
            val href = linkElement.attr("href")
            val title = linkElement.attr("title").trim()
            val poster = element.selectFirst("img.new-anime")?.attr("src") ?: ""
            val animeId = href.substringAfterLast("/")
            
            TvShow(
                id = animeId,
                title = title,
                poster = poster
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        return try {
            if (query.isBlank()) {
                if (page > 1) return emptyList()
                
                val document = service.getFilter()
                val genres = document.select("#categories option").map { option ->
                    val genreId = option.attr("value")
                    val genreName = option.text().trim()
                    Genre(id = genreId, name = genreName)
                }
                return genres
            }
            
            val document = service.getSearch(query, if (page > 1) page else null)

            val totalPages = document.toString().substringAfter("totalPages:", "1").trim().takeWhile { it.isDigit() }.toIntOrNull() ?: 1
            
            if (page > totalPages) {
                return emptyList()
            }
            
            val results = document.select(".list-group-item.bg-dark-as-box-shadow").map { item ->
                val linkElement = item.selectFirst(".info-archivio h3 a.badge-archivio") ?: return@map null
                val href = linkElement.attr("href")
                val title = linkElement.text().trim()
                val animeId = href.substringAfterLast("/")
                val poster = item.selectFirst("img.locandina-archivio")?.attr("src") ?: ""
                
                TvShow(id = animeId, title = title, poster = poster)
            }.filterNotNull()
            
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            val document = service.getGenre(id, if (page > 1) page else null)

            val totalPages = document.toString().substringAfter("totalPages:", "1").trim().takeWhile { it.isDigit() }.toIntOrNull() ?: 1
            
            if (page > totalPages) {
                val genreName = service.getFilter()
                    .select("#categories option")
                    .find { it.attr("value") == id }
                    ?.text()?.trim() ?: id
                return Genre(id = id, name = genreName, shows = emptyList())
            }
            
            val genreName = service.getFilter()
                .select("#categories option")
                .find { it.attr("value") == id }
                ?.text()?.trim() ?: id
            
            val shows = document.select("div.row.pt-4.justify-content-center .anime-card-newanime")
                .mapNotNull { parseNewAnime(it) }
            
            Genre(id = id, name = genreName, shows = shows)
        } catch (e: Exception) {
            Genre(id = id, name = id)
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return emptyList() // AnimeSaturn is TV shows only
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return try {
            val document = service.getTvShows(if (page > 1) page else null)

            val totalPages = document.toString().substringAfter("totalPages:", "1").trim().takeWhile { it.isDigit() }.toIntOrNull() ?: 1

            if (page > totalPages) {
                return emptyList()
            }

            val results = document
                .select("div.row.pt-4.justify-content-center .anime-card-newanime")
                .mapNotNull { parseNewAnime(it) }

            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTvShow(id: String): TvShow {
        return try {
            val document = service.getAnime(id)
            
            val posterElement = document.selectFirst(".cover-anime")
            val poster = posterElement?.attr("src") ?: ""
            
            val titleElement = document.selectFirst(".anime-title-as b")
            val title = titleElement?.text()?.trim() ?: ""
            
            val ratingElement = document.selectFirst("i.bi-star")?.parent()
            val ratingText = ratingElement?.text()?.trim() ?: ""
            val rating = ratingText.substringAfter("Voto:").trim().substringBefore("/5").toDoubleOrNull()
            
            val releasedText = document.select(".container.shadow.rounded.bg-dark-as-box")
                .find { it.text().contains("Data di uscita:") }
                ?.text()
                ?.substringAfter("Data di uscita:")
                ?.trim()
                ?: ""
            val released = releasedText.split(" ")
                .find { it.length == 4 && it.all { char -> char.isDigit() } } ?: ""
            
            val runtimeText = document.select(".container.shadow.rounded.bg-dark-as-box")
                .find { it.text().contains("Durata episodi:") }
                ?.text()
                ?.substringAfter("Durata episodi:")
                ?.trim()
                ?: ""
            val runtime = runtimeText.substringBefore(" ").toIntOrNull()
            
            val overviewElement = document.selectFirst("#full-trama") ?: document.selectFirst("#shown-trama")
            val overview = overviewElement?.text()?.trim() ?: ""
            
            val trailer = document.selectFirst("iframe#trailer-iframe")
                ?.attr("src")
                ?.replace("/embed/", "/watch?v=")
            
            val seasons = document.select(".episode-range").map { rangeElement ->
                val rangeText = rangeElement.text().trim()
                Season(
                    id = "$id-$rangeText",
                    number = 0,
                    title = rangeText
                )
            }.ifEmpty {
                listOf(
                    Season(
                        id = id,
                        number = 0,
                        title = "Episodi"
                    )
                )
            }
            
            val recommendations = document
                .select("div.owl-item.anime-card-newanime.main-anime-card")
                .mapNotNull { parseNewAnime(it) }
            
            val genres = document
                .select("a.badge.badge-light.generi-as")
                .mapNotNull { a ->
                    val name = a.text().trim()
                    if (name.isNotEmpty()) Genre(id = name, name = name) else null
                }

            TvShow(
                id = id,
                title = title,
                poster = poster,
                overview = overview,
                trailer = trailer,
                rating = rating,
                released = released,
                runtime = runtime,
                seasons = seasons,
                genres = genres,
                recommendations = recommendations
            )
        } catch (e: Exception) {
            TvShow(id = id, title = "", poster = "")
        }
    }

    override suspend fun getMovie(id: String): Movie {
        throw Exception("Movies not supported")
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        return try {
            val animeId: String
            val targetRange: String?
            
            val rangePattern = Regex("-(\\d+-\\d+)$")
            val match = rangePattern.find(seasonId)
            
            if (match != null) {
                targetRange = match.groupValues[1]
                animeId = seasonId.substringBeforeLast("-$targetRange")
            } else {
                animeId = seasonId
                targetRange = null
            }
            
            val document = service.getAnime(animeId)
            
            val anilistLink = document.selectFirst("a[href*='anilist.co/anime/']")
            val anilistHref = anilistLink?.attr("href")
            
            val anilistId = anilistHref
                ?.substringAfter("/anime/", "")
                ?.trimEnd('/')
                ?.takeWhile { it.isDigit() }
                ?.toIntOrNull()
            
            val extrasByNumber = if (anilistId != null) {
                fetchEpisodeThumbnails(anilistId)
            } else {
                emptyMap()
            }
            
            val episodeRanges = document.select(".episode-range")
            
            val episodes = if (episodeRanges.isNotEmpty()) {
                episodeRanges.flatMap { rangeElement ->
                    val rangeText = rangeElement.text().trim()
                    val tabId = rangeElement.attr("href").substringAfter("#")
                    
                    if (targetRange == null || rangeText == targetRange) {
                        val tabContent = document.selectFirst("#$tabId")
                        if (tabContent != null) {
                            tabContent.select(".episodi-link-button a").mapIndexed { index, button ->
                                val episodeUrl = button.attr("href")
                                val episodeTitle = button.text().trim()
                                
                                val rawEpisodeToken = episodeUrl.substringAfterLast("-ep-")
                                val episodeNumber = rawEpisodeToken
                                    .substringBefore('.')
                                    .toIntOrNull() ?: (index + 1)
                                val extras = extrasByNumber[episodeNumber]
                                val thumbnail = extras?.thumbnail
                                val kitsuTitle = extras?.title
                                val displayTitle = if (rawEpisodeToken.contains('.')) {
                                    episodeTitle
                                } else {
                                    kitsuTitle ?: episodeTitle
                                }
                                
                                Episode(
                                    id = episodeUrl,
                                    number = episodeNumber,
                                    title = displayTitle,
                                    poster = thumbnail
                                )
                            }
                        } else {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }
            } else {
                document.select(".episodi-link-button a").mapIndexed { index, button ->
                    val episodeUrl = button.attr("href")
                    val episodeTitle = button.text().trim()
                    
                    val rawEpisodeToken = episodeUrl.substringAfterLast("-ep-")
                    val episodeNumber = rawEpisodeToken
                        .substringBefore('.')
                        .toIntOrNull() ?: (index + 1)
                    val extras = extrasByNumber[episodeNumber]
                    val thumbnail = extras?.thumbnail
                    val kitsuTitle = extras?.title
                    val displayTitle = if (rawEpisodeToken.contains('.')) {
                        episodeTitle
                    } else {
                        kitsuTitle ?: episodeTitle
                    }
                    
                    Episode(
                        id = episodeUrl,
                        number = episodeNumber,
                        title = displayTitle,
                        poster = thumbnail
                    )
                }
            }
            
            episodes.sortedBy { it.number }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return try {
            val response = service.getEpisodeByUrl(id)
            val document = Jsoup.parse(response.string())
            
            val servers = mutableListOf<Video.Server>()
            
            val streamingLink = document.selectFirst("a[href*='watch?file=']")
            if (streamingLink != null) {
                val watchUrl = streamingLink.attr("href")
                
                val videoResponse = service.getEpisodeByUrl(watchUrl)
                val videoDocument = Jsoup.parse(videoResponse.string())
                
                val videoSource = videoDocument.selectFirst("source[type='video/mp4']")
                val videoUrl = videoSource?.attr("src")
                
                if (videoUrl != null) {
                    servers.add(
                        Video.Server(
                            id = videoUrl,
                            name = "AnimeSaturn",
                            src = videoUrl
                        )
                    )
                } else {
                    val scriptTags = videoDocument.select("script[type*=javascript]")
                    for (script in scriptTags) {
                        val scriptData = script.data()
                        if ("jwplayer" in scriptData && "file" in scriptData) {
                            val fileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")
                            val match = fileRegex.find(scriptData)
                            if (match != null) {
                                val fileUrl = match.groupValues[1]
                                servers.add(
                                    Video.Server(
                                        id = fileUrl,
                                        name = "AnimeSaturn",
                                        src = fileUrl
                                    )
                                )
                                break
                            }
                        }
                    }
                }
            }
            
            servers
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        return People(id = id, name = "Person $id") // TODO: Implement people functionality
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Video(
            source = server.src
        )
    }

}
