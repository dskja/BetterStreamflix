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
import com.streamflixreborn.streamflix.extractors.VixcloudExtractor
import com.streamflixreborn.streamflix.utils.DnsResolver
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Body
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import java.util.concurrent.TimeUnit

object AnimeUnityProvider : Provider {
    override val name = "AnimeUnity"
    override val baseUrl = "https://www.animeunity.so"
    override val logo: String get() = "$baseUrl/images/scritta2.png"
    override val language = "it"
    
    private const val USER_AGENT = "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private fun getImageUrl(imageurl: String): String {
        return AnimeUnityService.getImageUrl(imageurl, baseUrl)
    }
    
    private interface KitsuService {
        @POST("graphql")
        suspend fun getEpisodes(@Body body: RequestBody): ResponseBody
    }
    
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
    
    private suspend fun fetchEpisodeThumbnails(anilistId: Int): Map<Int, String> {
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
            
            val thumbnails = mutableMapOf<Int, String>()
            for (i in 0 until episodes.length()) {
                try {
                    val episode = episodes.optJSONObject(i) ?: continue
                    val number = episode.optInt("number", 0)
                    val thumbnail = episode
                        .optJSONObject("thumbnail")
                        ?.optJSONObject("original")
                        ?.optString("url", "")
                        ?: ""
                    
                    if (number > 0 && thumbnail.isNotEmpty()) {
                        thumbnails[number] = thumbnail
                    }
                } catch (e: Exception) {
                    // Skip invalid episodes
                    continue
                }
            }
            thumbnails
        } catch (e: Exception) {
            emptyMap()
        }
    }
    

    private var savedCookieHeader = ""
    private var savedCsrfToken = ""

    private interface AnimeUnityService {
        @Headers(USER_AGENT)
        @GET(".")
        suspend fun getHome(): Document

        @Headers(USER_AGENT)
        @GET("archivio")
        suspend fun getArchivio(): Document

        @Headers(USER_AGENT)
        @GET("anime/{id}")
        suspend fun getAnime(@Path("id") id: String): Document

        @Headers(USER_AGENT)
        @GET("embed-url/{episodeId}")
        suspend fun getEmbedUrl(@Path("episodeId") episodeId: String): Document

        @Headers(USER_AGENT, "Content-Type: application/json")
        @POST("archivio/get-animes")
        suspend fun getAnimes(@Body body: RequestBody): ResponseBody

        @Headers(USER_AGENT)
        @GET("info_api/{animeId}/1")
        suspend fun getEpisodesByRange(
            @Path("animeId") animeId: String,
            @Query("start_range") startRange: Int,
            @Query("end_range") endRange: Int
        ): ResponseBody

        companion object {
            fun getImageUrl(imageurl: String, baseUrl: String): String {
                if (imageurl.isEmpty()) return ""
                
                val parts = imageurl.split(Regex("[\\\\/]"))
                val filename = parts.lastOrNull() ?: ""
                
                val domain = baseUrl.replace("https://", "").replace("www.", "")
                
                return "https://img.$domain/anime/$filename"
            }
            
            fun build(baseUrl: String): AnimeUnityService {
                val clientBuilder = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)

                val client = clientBuilder
                    .dns(DnsResolver.doh)
                    .addInterceptor { chain ->
                        val originalRequest = chain.request()
                        val requestBuilder = originalRequest.newBuilder()
                        
                        
                        if (originalRequest.url.toString().endsWith("/archivio/get-animes")) {
                                requestBuilder.header("Cookie", savedCookieHeader)
                                requestBuilder.header("X-CSRF-TOKEN", savedCsrfToken)
                        }
                        
                        val response = chain.proceed(requestBuilder.build())
                        
                        if (originalRequest.url.toString().endsWith("/archivio")) {
                            val cookies = response.headers("Set-Cookie")
                            savedCookieHeader = cookies.joinToString("; ") { it.substringBefore(";") }
                            
                            val responseBody = response.body
                            if (responseBody != null) {
                                val html = responseBody.string()
                                val csrfMeta = Jsoup.parse(html).selectFirst("meta[name=csrf-token]")
                                savedCsrfToken = csrfMeta?.attr("content") ?: ""
                                
                                val newResponseBody = html.toResponseBody(responseBody.contentType())
                                response.newBuilder().body(newResponseBody).build()
                            } else {
                                response
                            }
                        } else {
                            response
                        }
                    }
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(AnimeUnityService::class.java)
            }
        }
    }

    private val service = AnimeUnityService.build(baseUrl)


    override suspend fun getHome(): List<Category> {
        return try {
            val document = service.getHome()
            
            val latestEpisodes = parseLatestEpisodes(document)
            
            val latestAdditions = parseLatestAdditions(document)
            
            val featuredAnime = parseFeaturedAnime(document)
            
            val categories = mutableListOf<Category>()
            
            if (latestEpisodes.isNotEmpty()) {
                categories.add(
                    Category(
                        name = "Ultimi Episodi",
                        list = latestEpisodes
                    )
                )
            }
            
            if (latestAdditions.isNotEmpty()) {
                categories.add(
                    Category(
                        name = "Ultime Aggiunte",
                        list = latestAdditions
                    )
                )
            }
            
            if (featuredAnime.isNotEmpty()) {
                categories.add(
                    Category(
                        name = Category.FEATURED,
                        list = featuredAnime
                    )
                )
            }
            
            categories
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun parseLatestEpisodes(document: Element): List<TvShow> {
        val tvShows = mutableListOf<TvShow>()
        val seenAnimeIds = mutableSetOf<String>()
        
        try {
            val layoutItems = document.select("layout-items[items-json]").firstOrNull()
                ?: return emptyList()
            
            val itemsJson = layoutItems.attr("items-json")
            if (itemsJson.isEmpty()) {
                return emptyList()
            }
            
            val jsonObject = org.json.JSONObject(itemsJson)
            val dataArray = jsonObject.getJSONArray("data")

            for (i in 0 until dataArray.length()) {
                try {
                    val episodeData = dataArray.getJSONObject(i)
                    val animeData = episodeData.getJSONObject("anime")
                    
                    val animeId = animeData.getString("id")
                    val animeSlug = animeData.getString("slug")
                    
                    if (seenAnimeIds.contains(animeId)) continue
                    seenAnimeIds.add(animeId)
                    
                    val animeTitle = if (animeData.has("title_eng") && !animeData.isNull("title_eng")) {
                        animeData.getString("title_eng")
                    } else {
                        animeData.getString("title")
                    }
                    val animeImage = animeData.getString("imageurl")
                    
                    val tvShow = TvShow(
                        id = "$animeId-$animeSlug",
                        title = animeTitle,
                        poster = getImageUrl(animeImage)
                    )
                    
                    tvShows.add(tvShow)
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }
        
        return tvShows
    }
    
    private fun parseLatestAdditions(document: Element): List<AppAdapter.Item> {
        val items = mutableListOf<AppAdapter.Item>()
        
        try {
            val homeSidebar = document.select("div.home-sidebar").firstOrNull()
                ?: return emptyList()

            val animeContainers = homeSidebar.select("div.latest-anime-container")

            for (container in animeContainers) {
                try {
                    val linkElement = container.select("a.unstile-a").firstOrNull()
                        ?: continue
                    
                    val animeUrl = linkElement.attr("href")
                    if (animeUrl.isEmpty()) continue
                    
                    val animeId = animeUrl.substringAfterLast("/")
                    
                    val titleElement = container.select("strong.latest-anime-title").firstOrNull()
                    val animeTitle = titleElement?.text()?.trim() ?: continue
                    
                    val imgElement = container.select("img").firstOrNull()
                    val animePoster = imgElement?.attr("src") ?: ""
                    
                    val typeInfo = container.select("div.latest-anime-info.mt-2.mb-2").firstOrNull()?.text() ?: ""
                    val isMovie = typeInfo.contains("Movie", ignoreCase = true)
                    
                    if (isMovie) {
                        val movie = Movie(
                            id = animeId,
                            title = animeTitle,
                            poster = getImageUrl(animePoster)
                        )
                        items.add(movie)
                    } else {
                        val tvShow = TvShow(
                            id = animeId,
                            title = animeTitle,
                            poster = getImageUrl(animePoster)
                        )
                        items.add(tvShow)
                    }
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }
        
        return items
    }
    
    private fun parseFeaturedAnime(document: Element): List<AppAdapter.Item> {
        val items = mutableListOf<AppAdapter.Item>()
        
        try {
            val carouselTag = document.select("the-carousel[animes]").firstOrNull()
                ?: return emptyList()
            
            val animesJson = carouselTag.attr("animes")
            if (animesJson.isEmpty()) return emptyList()
            
            val decodedJson = animesJson.replace("&quot;", "\"")
            val jsonArray = org.json.JSONArray(decodedJson)

            for (i in 0 until jsonArray.length()) {
                try {
                    val animeData = jsonArray.getJSONObject(i)
                    val animeId = animeData.getInt("id")
                    val slug = animeData.getString("slug")
                    val titleEng = animeData.optString("title_eng", "")
                    val imageurl_cover = animeData.optString("imageurl_cover", "")
                    val plot = animeData.optString("plot", "")
                    val date = animeData.optString("date", "")
                    val typeInfo = animeData.optString("type", "")
                    val score = animeData.optString("score", "").toDoubleOrNull()
                    
                    val Title = if (titleEng.isNotEmpty()) titleEng else animeData.optString("title", "")
                    
                    if (animeId > 0 && slug.isNotEmpty() && Title.isNotEmpty()) {
                        
                        val isMovie = typeInfo == "Movie"
                        
                        val item = if (isMovie) {
                            Movie(
                                id = "$animeId-$slug",
                                title = Title,
                                banner = getImageUrl(imageurl_cover),
                                overview = plot,
                                rating = score,
                                released = date
                            )
                        } else {
                            TvShow(
                                id = "$animeId-$slug",
                                title = Title,
                                banner = getImageUrl(imageurl_cover),
                                overview = plot,
                                rating = score,
                                released = date
                            )
                        }
                        
                        items.add(item)
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            
        } catch (e: Exception) {
        }
        
        return items
    }

    private fun parseGenresFromJson(allGenresData: String): List<Genre> {
        if (allGenresData.isEmpty()) return emptyList()
        
            val decodedJson = allGenresData.replace("&quot;", "\"")
            val jsonArray = org.json.JSONArray(decodedJson)
            
            val genres = mutableListOf<Genre>()
            for (i in 0 until jsonArray.length()) {
                try {
                    val genreObj = jsonArray.getJSONObject(i)
                    val genreId = genreObj.getInt("id")
                    val genreName = genreObj.getString("name")
                    
                    genres.add(Genre(
                        id = genreId.toString(),
                        name = genreName
                    ))
                } catch (e: Exception) {
                }
            }
            
        return genres
    }

    private fun parseAnimeFromJson(records: org.json.JSONArray): List<AppAdapter.Item> {
        val results = mutableListOf<AppAdapter.Item>()
        for (i in 0 until records.length()) {
            try {
                val record = records.getJSONObject(i)
                
                val animeId = record.optInt("id", 0)
                val slug = record.optString("slug", "")
                val titleEng = record.optString("title_eng", "")
                val imageUrl = record.optString("imageurl", "")
                val type = record.optString("type", "")
                
                val Title = if (titleEng.isNotEmpty()) titleEng else record.optString("title", "")
                
                if (animeId > 0 && slug.isNotEmpty() && Title.isNotEmpty()) {
                    val fullId = "$animeId-$slug"
                    
                    val isMovie = type.contains("Movie", ignoreCase = true)
                    
                    if (isMovie) {
                        results.add(Movie(
                            id = fullId,
                            title = Title,
                            poster = getImageUrl(imageUrl)
                        ))
                    } else {
                        results.add(TvShow(
                            id = fullId,
                            title = Title,
                            poster = getImageUrl(imageUrl)
                        ))
                    }
                }
            } catch (e: Exception) {
            }
        }
        
        return results
    }

    private suspend fun getAnimeByGenre(genreId: Int, genreName: String, offset: Int = 0): List<AppAdapter.Item> {
        return try {
            service.getArchivio()
            
            val payload = JSONObject().apply {
                put("title", false)
                put("type", false)
                put("year", false)
                put("order", false)
                put("status", false)
                put("offset", offset)
                put("dubbed", false)
                put("season", false)
                
                val genresArray = org.json.JSONArray()
                val genreObj = JSONObject().apply {
                    put("id", genreId)
                    put("name", genreName)
                }
                genresArray.put(genreObj)
                put("genres", genresArray)
            }
            
            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            
            val response = service.getAnimes(requestBody)
            
            val responseString = response.string()
            val jsonResponse = JSONObject(responseString)
            val records = jsonResponse.getJSONArray("records")
            
            parseAnimeFromJson(records)
            
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        return try {
            if (query.isBlank()) {
                if (page > 1) return emptyList()
                
                val document = service.getArchivio()
                
                val archivioElement = document.selectFirst("archivio")
                val allGenresData = archivioElement?.attr("all_genres") ?: ""
                
                return parseGenresFromJson(allGenresData)
            }

            service.getArchivio()
            
            val offset = (page - 1) * 30
            
            val payload = JSONObject().apply {
                put("title", query)
                put("type", false)
                put("year", false)
                put("order", false)
                put("status", false)
                put("genres", false)
                put("offset", offset)
                put("dubbed", false)
                put("season", false)
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val response = service.getAnimes(requestBody)

            val responseString = response.string()
            val jsonResponse = JSONObject(responseString)
            val records = jsonResponse.getJSONArray("records")

            parseAnimeFromJson(records)
            
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return try {
            service.getArchivio()
            
            val offset = (page - 1) * 30
            
            val payload = JSONObject().apply {
                put("title", false)
                put("type", "Movie")
                put("year", false)
                put("order", false)
                put("status", false)
                put("genres", false)
                put("offset", offset)
                put("dubbed", false)
                put("season", false)
            }
            
            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            
            val response = service.getAnimes(requestBody)
            
            val responseString = response.string()
            val jsonResponse = JSONObject(responseString)
            val records = jsonResponse.getJSONArray("records")
            
            return (0 until records.length()).mapNotNull { i ->
                try {
                    val record = records.getJSONObject(i)
                    
                    val animeId = record.optInt("id", 0)
                    val slug = record.optString("slug", "")
                    val titleEng = record.optString("title_eng", "")
                    val imageUrl = record.optString("imageurl", "")
                    
                    val title = if (titleEng.isNotEmpty()) titleEng else record.optString("title", "")
                    
                    if (animeId > 0 && slug.isNotEmpty() && title.isNotEmpty()) {
                        Movie(
                            id = "$animeId-$slug",
                            title = title,
                            poster = getImageUrl(imageUrl)
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return try {
            service.getArchivio()
            
            val offset = (page - 1) * 30
            
            val payload = JSONObject().apply {
                put("title", false)
                put("type", "TV")
                put("year", false)
                put("order", false)
                put("status", false)
                put("genres", false)
                put("offset", offset)
                put("dubbed", false)
                put("season", false)
            }
            
            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            
            val response = service.getAnimes(requestBody)
            
            val responseString = response.string()
            val jsonResponse = JSONObject(responseString)
            val records = jsonResponse.getJSONArray("records")
            
            return (0 until records.length()).mapNotNull { i ->
                try {
                    val record = records.getJSONObject(i)
                    
                    val animeId = record.optInt("id", 0)
                    val slug = record.optString("slug", "")
                    val titleEng = record.optString("title_eng", "")
                    val imageUrl = record.optString("imageurl", "")
                    
                    val title = if (titleEng.isNotEmpty()) titleEng else record.optString("title", "")
                    
                    if (animeId > 0 && slug.isNotEmpty() && title.isNotEmpty()) {
                        TvShow(
                            id = "$animeId-$slug",
                            title = title,
                            poster = getImageUrl(imageUrl)
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovie(id: String): Movie {
        return try {
            val document = service.getAnime(id)
            
            val titleElement = document.selectFirst("h1.title")
            val title = titleElement?.text()?.trim() ?: ""
            
            val descriptionElement = document.selectFirst("div.description")
            val overview = descriptionElement?.text()?.trim() ?: ""
            
            val coverImg = document.selectFirst("img.cover")
            val poster = coverImg?.attr("src") ?: ""
            
            val ratingElement = document.selectFirst("div.info-item:has(strong:contains(Valutazione)) small")
            val rating = ratingElement?.text()?.trim()?.toDoubleOrNull()
            
            val yearElement = document.selectFirst("div.info-item:has(strong:contains(Anno)) small")
            val released = yearElement?.text()?.trim() ?: ""
            
            val durationElement = document.selectFirst("div.info-item:has(strong:contains(Durata)) small")
            val runtime = durationElement?.text()?.trim()?.split(" ")?.firstOrNull()?.toIntOrNull()
            
            val genres = mutableListOf<Genre>()
            val genresSection = document.selectFirst("div.info-wrapper:has(strong:contains(Generi))")
            if (genresSection != null) {
                val genreLinks = genresSection.select("a.genre-link")
                for (link in genreLinks) {
                    val genreName = link.text().trim().trimEnd(',')
                    if (genreName.isNotEmpty()) {
                        genres.add(Genre(
                            id = genreName.lowercase().replace(" ", "-"),
                            name = genreName
                        ))
                    }
                }
            }
            
            val relatedMovies = mutableListOf<Movie>()
            val relatedTvShows = mutableListOf<TvShow>()
            val relatedWrapper = document.selectFirst("div.related-wrapper")
            if (relatedWrapper != null) {
                val relatedItems = relatedWrapper.select("div.related-item")
                for (item in relatedItems) {
                    try {
                        val linkElement = item.selectFirst("a.unstile-a")
                        if (linkElement != null) {
                            val relatedUrl = linkElement.attr("href")
                            if (relatedUrl.isNotEmpty()) {
                                val relatedId = relatedUrl.substringAfterLast("/")
                                
                                val titleElement = item.selectFirst("strong.related-anime-title")
                                val relatedTitle = titleElement?.text()?.trim() ?: ""
                                
                                val imgElement = item.selectFirst("img")
                                val relatedPoster = imgElement?.attr("src") ?: ""
                                
                                val relatedInfo = item.selectFirst("div.related-info")
                                val typeInfo = relatedInfo?.text() ?: ""
                                val isMovie = typeInfo.contains("Movie", ignoreCase = true)
                                
                                if (relatedTitle.isNotEmpty()) {
                                    if (isMovie) {
                                        relatedMovies.add(Movie(
                                            id = relatedId,
                                            title = relatedTitle,
                                            poster = getImageUrl(relatedPoster)
                                        ))
                                    } else {
                                        relatedTvShows.add(TvShow(
                                            id = relatedId,
                                            title = relatedTitle,
                                            poster = getImageUrl(relatedPoster)
                                        ))
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
            }
            
            val recommendations = relatedMovies + relatedTvShows
            Movie(
                id = id,
                title = title,
                poster = getImageUrl(poster),
                overview = overview,
                rating = rating,
                released = released,
                runtime = runtime,
                genres = genres,
                recommendations = recommendations
            )
        } catch (e: Exception) {
            Movie(id = id, title = "", poster = "")
        }
    }

    override suspend fun getTvShow(id: String): TvShow {
        return try {
            val document = service.getAnime(id)
            
            val titleElement = document.selectFirst("h1.title")
            val title = titleElement?.text()?.trim() ?: ""
            
            val descriptionElement = document.selectFirst("div.description")
            val overview = descriptionElement?.text()?.trim() ?: ""
            
            val coverImg = document.selectFirst("img.cover")
            val poster = coverImg?.attr("src") ?: ""
            
            val ratingElement = document.selectFirst("div.info-item:has(strong:contains(Valutazione)) small")
            val rating = ratingElement?.text()?.trim()?.toDoubleOrNull()
            
            val yearElement = document.selectFirst("div.info-item:has(strong:contains(Anno)) small")
            val released = yearElement?.text()?.trim() ?: ""
            
            val durationElement = document.selectFirst("div.info-item:has(strong:contains(Durata)) small")
            val runtime = durationElement?.text()?.trim()?.split(" ")?.firstOrNull()?.toIntOrNull()
            
            val genres = mutableListOf<Genre>()
            val genresSection = document.selectFirst("div.info-wrapper:has(strong:contains(Generi))")
            if (genresSection != null) {
                val genreLinks = genresSection.select("a.genre-link")
                for (link in genreLinks) {
                    val genreName = link.text().trim().trimEnd(',')
                    if (genreName.isNotEmpty()) {
                        genres.add(Genre(
                            id = genreName.lowercase().replace(" ", "-"),
                            name = genreName
                        ))
                    }
                }
            }
            
            val relatedMovies = mutableListOf<Movie>()
            val relatedTvShows = mutableListOf<TvShow>()
            val relatedWrapper = document.selectFirst("div.related-wrapper")
            if (relatedWrapper != null) {
                val relatedItems = relatedWrapper.select("div.related-item")
                for (item in relatedItems) {
                    try {
                        val linkElement = item.selectFirst("a.unstile-a")
                        if (linkElement != null) {
                            val relatedUrl = linkElement.attr("href")
                            if (relatedUrl.isNotEmpty()) {
                                val relatedId = relatedUrl.substringAfterLast("/")
                                
                                val titleElement = item.selectFirst("strong.related-anime-title")
                                val relatedTitle = titleElement?.text()?.trim() ?: ""
                                
                                val imgElement = item.selectFirst("img")
                                val relatedPoster = imgElement?.attr("src") ?: ""
                                
                                val relatedInfo = item.selectFirst("div.related-info")
                                val typeInfo = relatedInfo?.text() ?: ""
                                val isMovie = typeInfo.contains("Movie", ignoreCase = true)
                                
                                if (relatedTitle.isNotEmpty()) {
                                    if (isMovie) {
                                        relatedMovies.add(Movie(
                                            id = relatedId,
                                            title = relatedTitle,
                                            poster = getImageUrl(relatedPoster)
                                        ))
                                    } else {
                                        relatedTvShows.add(TvShow(
                                            id = relatedId,
                                            title = relatedTitle,
                                            poster = getImageUrl(relatedPoster)
                                        ))
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
            }
            
            val recommendations = relatedMovies + relatedTvShows
            
            val videoPlayer = document.selectFirst("video-player")
            val episodesCount = videoPlayer?.attr("episodes_count")?.toIntOrNull() ?: 0
            
            val seasons = if (episodesCount > 120) {
                val ranges = calculateEpisodeRanges(episodesCount)
                ranges.mapIndexed { index, (startRange, endRange) ->
                    Season(
                        id = "$id-$startRange-$endRange",
                        number = 0,
                        title = "$startRange-$endRange"
                    )
                }
            } else {
                listOf(
                    Season(
                        id = id,
                        number = 0,
                        title = "Episodi"
                    )
                )
            }
            TvShow(
                id = id,
                title = title,
                poster = getImageUrl(poster),
                overview = overview,
                rating = rating,
                released = released,
                runtime = runtime,
                genres = genres,
                recommendations = recommendations,
                seasons = seasons
            )
        } catch (e: Exception) {
            TvShow(id = id, title = "", poster = "")
        }
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        return try {
            val parts = seasonId.split("-")
            val hasRange = parts.size >= 4 && parts[parts.size - 2].toIntOrNull() != null && parts[parts.size - 1].toIntOrNull() != null
            
            val (animeId, startRange, endRange) = if (hasRange) {
                val animeIdParts = parts.dropLast(2)
                val animeIdStr = animeIdParts.joinToString("-")
                val start = parts[parts.size - 2].toInt()
                val end = parts[parts.size - 1].toInt()
                Triple(animeIdStr, start, end)
            } else {
                Triple(seasonId, null, null)
            }
            
            val animeIdClean = animeId.split("-")[0]
            
            val document = service.getAnime(animeId)
            val videoPlayer = document.selectFirst("video-player")
            if (videoPlayer == null) {
                return emptyList()
            }
            
            val episodesCount = videoPlayer.attr("episodes_count").toIntOrNull() ?: 0
            
            if (startRange != null && endRange != null) {
                return getEpisodesFromApiRange(animeIdClean, animeId, startRange, endRange)
            }
            
            if (episodesCount <= 120) {
                return getEpisodesNormal(animeId, videoPlayer)
            }
            
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private suspend fun getEpisodesNormal(seasonId: String, videoPlayer: Element): List<Episode> {
        return try {
            val episodesData = videoPlayer.attr("episodes")
            if (episodesData.isEmpty()) {
                return emptyList()
            }

            val animeJson = videoPlayer.attr("anime")
            val anilistId = try {
                val animeObject = JSONObject(animeJson)
                animeObject.optInt("anilist_id", 0).takeIf { it > 0 }
            } catch (e: Exception) {
                null
            }
            
            val thumbnails = if (anilistId != null) {
                fetchEpisodeThumbnails(anilistId)
            } else {
                emptyMap()
            }
            
            val decodedEpisodes = java.net.URLDecoder.decode(episodesData, "UTF-8")
            val episodesJson = org.json.JSONArray(decodedEpisodes)

            (0 until episodesJson.length()).map { i ->
                    val episodeData = episodesJson.getJSONObject(i)
                    
                    val fileName = episodeData.optString("file_name", "")
                    val episodeName = extractEpisodeNameFromFileName(fileName)
                    
                    val episodeNumber = episodeData.optString("number", "").toIntOrNull() ?: (i + 1)
                    
                    val episodeTitle = if (episodeName.isNotEmpty()) {
                        episodeName
                    } else {
                        "Episodio $episodeNumber"
                    }
                    
                    val episodeId = episodeData.optString("id", "")
                    val thumbnail = thumbnails[episodeNumber]
                    
                Episode(
                    id = "$seasonId/$episodeId",
                        number = episodeNumber,
                    title = episodeTitle,
                    poster = thumbnail
                )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun calculateEpisodeRanges(episodesCount: Int): List<Pair<Int, Int>> {
        if (episodesCount <= 120) {
            return listOf(Pair(1, episodesCount))
        }
        
        val ranges = mutableListOf<Pair<Int, Int>>()
        ranges.add(Pair(1, 120))
        
        val remainingEpisodes = episodesCount - 120
        val additionalRanges = remainingEpisodes / 120
        val finalRange = if (remainingEpisodes % 120 > 0) 1 else 0
        
        for (i in 0 until additionalRanges + finalRange) {
            val start = 121 + (i * 120)
            val end = minOf(start + 119, episodesCount)
            ranges.add(Pair(start, end))
        }
        
        return ranges
    }
    
    private suspend fun getEpisodesFromApiRange(animeId: String, animeIdFull: String, startRange: Int, endRange: Int): List<Episode> {
        return try {
            val document = service.getAnime(animeIdFull)
            val videoPlayer = document.selectFirst("video-player")
            
            val animeJson = videoPlayer?.attr("anime") ?: ""
            val anilistId = try {
                if (animeJson.isNotEmpty()) {
                    val animeObject = JSONObject(animeJson)
                    animeObject.optInt("anilist_id", 0).takeIf { it > 0 }
                } else null
            } catch (e: Exception) {
                null
            }
            
            val thumbnails = if (anilistId != null) {
                fetchEpisodeThumbnails(anilistId)
            } else {
                emptyMap()
            }
            
            val response = service.getEpisodesByRange(animeId, startRange, endRange)
            val responseBody = response.string()
            val jsonObject = JSONObject(responseBody)
            
            val episodesData = jsonObject.optJSONArray("episodes") ?: return emptyList()
            (0 until episodesData.length()).map { i ->
                    val episodeData = episodesData.getJSONObject(i)
                    
                    val fileName = episodeData.optString("file_name", "")
                    val episodeName = extractEpisodeNameFromFileName(fileName)
                    
                    val numberString = episodeData.optString("number", "0")
                    // For ranges like "235-236-237-238", use the first number for Episode.number for compatibility
                    val episodeNumber = if (numberString.contains("-")) {
                        numberString.split("-")[0].toIntOrNull() ?: 0
                    } else {
                        numberString.toIntOrNull() ?: 0
                    }
                    
                    val episodeTitle = if (episodeName.isNotEmpty()) {
                        if (numberString.contains("-")) {
                            "$episodeName ($numberString)"
                        } else {
                        episodeName
                        }
                    } else {
                        if (numberString.contains("-")) {
                            "Episodio $numberString"
                    } else {
                        "Episodio $episodeNumber"
                        }
                    }
                    val episodeId = episodeData.optString("id", "")
                    val thumbnail = thumbnails[episodeNumber]
                    
                Episode(
                    id = "$animeIdFull/$episodeId",
                        number = episodeNumber,
                    title = episodeTitle,
                    poster = thumbnail
                )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private suspend fun findEpisodeInPaginatedData(animeId: String, episodeNumber: Int, episodesCount: Int): org.json.JSONObject? {
        return try {
            val ranges = calculateEpisodeRanges(episodesCount)
            
            for ((startRange, endRange) in ranges) {
                if (episodeNumber in startRange..endRange) {
                    
                    val response = service.getEpisodesByRange(animeId, startRange, endRange)
                    val responseBody = response.string()
                    val jsonObject = JSONObject(responseBody)
                    
                    val episodesData = jsonObject.optJSONArray("episodes") ?: continue
                    
                    for (i in 0 until episodesData.length()) {
                        val episode = episodesData.getJSONObject(i)
                        val episodeNumberStr = episode.optString("number", "0")
                        
                        val episodeMatches = if (episodeNumberStr.contains("-")) {
                            val firstNumber = episodeNumberStr.split("-")[0].toIntOrNull() ?: 0
                            firstNumber == episodeNumber
                        } else {
                            episodeNumberStr.toIntOrNull() == episodeNumber
                        }
                        
                        if (episodeMatches) {
                            return episode
                        }
                    }
                }
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractEpisodeNameFromFileName(fileName: String): String {
        if (fileName.isEmpty()) return ""
        
        return try {
            val episodeNameRegex = Regex("""(?:\.S\d+E\d+\.(.+?)(?:\.\d+p|\.CR\.WEB-DL|\.WEB-DL|\.JPN|\.ITA|\.AAC2\.0|\.H\.264|\.mkv|\.AMZN)|Ep_\d+_(.+?)(?:_SUB_ITA|\.mp4|\.mkv)|ep\s+\d+\s+(.+?)(?:\.mp4|\.mkv))""")
            val episodeNameMatch = episodeNameRegex.find(fileName)
            
            if (episodeNameMatch != null) {
                var extractedName = (episodeNameMatch.groupValues[1].ifEmpty { 
                    episodeNameMatch.groupValues[2].ifEmpty { 
                        episodeNameMatch.groupValues[3] 
                    } 
                }).replace(".", " ").replace("_", " ").trim()
                
                extractedName = extractedName.replace(Regex("""^Episodio\s+\d+\s*-?\s*""", RegexOption.IGNORE_CASE), "").trim()
                
                val technicalTerms = listOf("CR", "WEB-DL", "JPN", "ITA", "AAC2.0", "H.264", "mkv", "AMZN", "SUB", "mp4")
                
                val meaningfulWords = extractedName.split(" ").filter { word ->
                    word.length > 2 && !technicalTerms.any { tech -> 
                        word.equals(tech, ignoreCase = true) 
                    } && !Regex("""\d+p""").containsMatchIn(word)
                }
                
                if (meaningfulWords.isNotEmpty()) {
                    extractedName
                } else {
                    ""
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            val document = service.getArchivio()
            val archivioElement = document.selectFirst("archivio")
            val allGenresData = archivioElement?.attr("all_genres")
            
            var genreName = "Genre $id"
            if (!allGenresData.isNullOrEmpty()) {
                val decodedJson = allGenresData.replace("&quot;", "\"")
                val jsonArray = org.json.JSONArray(decodedJson)
                
                for (i in 0 until jsonArray.length()) {
                    try {
                        val genreObj = jsonArray.getJSONObject(i)
                        val genreId = genreObj.getInt("id")
                        if (genreId.toString() == id) {
                            genreName = genreObj.getString("name")
                            break
                        }
                    } catch (e: Exception) {
                    }
                }
            }
            
            val genreId = id.toIntOrNull() ?: 0
            val animeList = getAnimeByGenre(genreId, genreName, (page - 1) * 30)
            
            Genre(
                id = id,
                name = genreName,
                shows = animeList.mapNotNull { item ->
                    when (item) {
                        is Movie -> item
                        is TvShow -> item
                        else -> null
                    }
                }
            )
        } catch (e: Exception) {
            Genre(id = id, name = "", shows = emptyList())
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        throw Exception("AnimeUnity doesn't support people search")
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return VixcloudExtractor().extract(server.src)
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return try {
            
            val episodeNumber = if (videoType is Video.Type.Episode) {
                videoType.number
            } else {
                1
            }
            
            val animeIdFull = if (id.contains("/")) {
                id.split("/")[0]
            } else {
                id
            }
            val animeIdClean = animeIdFull.split("-")[0]
            
            val document = service.getAnime(animeIdFull)
            val videoPlayer = document.selectFirst("video-player")
            if (videoPlayer == null) {
                return emptyList()
            }
            
            val episodesCount = videoPlayer.attr("episodes_count").toIntOrNull() ?: 0
            
            var embedUrl = ""
            
            val isMovie = videoType !is Video.Type.Episode
            
            if (isMovie || episodeNumber == 1) {
                embedUrl = videoPlayer.attr("embed_url")
            } else {
                val targetEpisode = if (episodesCount <= 120) {
                    val episodesData = videoPlayer.attr("episodes")
                    if (episodesData.isEmpty()) {
                        return emptyList()
                    }
                    
                    val decodedEpisodes = java.net.URLDecoder.decode(episodesData, "UTF-8")
                    val episodesJson = org.json.JSONArray(decodedEpisodes)
                    
                    var foundEpisode: org.json.JSONObject? = null
                    for (i in 0 until episodesJson.length()) {
                        val episode = episodesJson.getJSONObject(i)
                        val episodeNumberStr = episode.optString("number", "")
                        
                        val episodeMatches = if (episodeNumberStr.contains("-")) {
                            val firstNumber = episodeNumberStr.split("-")[0].toIntOrNull() ?: 0
                            firstNumber == episodeNumber
                        } else {
                            episode.optInt("number", 0) == episodeNumber
                        }
                        
                        if (episodeMatches) {
                            foundEpisode = episode
                            break
                        }
                    }
                    foundEpisode
                } else {
                    val foundEpisode = findEpisodeInPaginatedData(animeIdClean, episodeNumber, episodesCount)
                    foundEpisode
                }
                
                if (targetEpisode == null) {
                    return emptyList()
                }
                
                val episodeId = targetEpisode.optString("id", "")
                if (episodeId.isEmpty()) {
                    return emptyList()
                }
                
                val embedDocument = service.getEmbedUrl(episodeId)
                embedUrl = embedDocument.text().trim()
            }
            
            if (embedUrl.isNotEmpty()) {
                listOf(Video.Server(
                    id = id,
                    name = "Vixcloud",
                    src = embedUrl
                ))
            } else {
                emptyList()
            }
            
        } catch (e: Exception) {
            emptyList()
        }
    }

}
