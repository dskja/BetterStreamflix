package com.streamflixreborn.streamflix.providers

import android.util.Log
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
import com.streamflixreborn.streamflix.utils.NetworkClient
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url
import kotlin.math.round

object FrenchMangaProvider : Provider, ProviderPortalUrl, ProviderConfigUrl {
    override val name = "FrenchManga"

    override val defaultBaseUrl: String = "https://w16.french-manga.net/"
    override val defaultPortalUrl: String = "http://fstream.info/"

    override val portalUrl: String = defaultPortalUrl
        get() {
            val cachePortalURL = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_PORTAL_URL)
            return cachePortalURL.ifEmpty { field }
        }
    override val baseUrl: String = FrenchMangaProvider.defaultBaseUrl
        get() {
            val cacheURL = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL)
            return cacheURL.ifEmpty { field }
        }

    override val logo: String
        get() = "$baseUrl/logo.png"

    override val language = "fr"
    override val changeUrlMutex = Mutex()

    private lateinit var service: FrenchMangaService
    private var serviceInitialized = false
    private val initializationMutex = Mutex()

    data class EpisodesData(
        val vf: Map<String, Map<String, String>>? = null,
        val vostfr: Map<String, Map<String, String>>? = null,
        val vo: Map<String, Map<String, String>>? = null,
        val info: Map<String, EpisodeInfo>? = null
    )

    data class EpisodeInfo(
        val title: String? = null,
        val synopsis: String? = null,
        val poster: String? = null
    )

    override suspend fun getHome(): List<Category> {
        initializeService()
        val document = service.getHome()
        val categories = mutableListOf<Category>()


        document.select("div.sect").mapNotNull { cat_item ->
            val title = cat_item.selectFirst("div.st-left > a")
            if (title == null) return@mapNotNull null

            val movies = cat_item.select("div.short > div.short-in").mapNotNull { movie ->
                val mtype = movie.selectFirst("span.mli-type a")?.text()
                if (mtype == null) return@mapNotNull null
                val title = movie.selectFirst("div.short-title")?.text() ?: "Item"
                val href = movie.selectFirst("a.short-poster")
                val poster = href?.selectFirst("> img")?.attr("src")
                val id = href?.attr("href")?.substringAfterLast("=")?.takeIf{ it.isNotBlank() }
                if (id == null) return@mapNotNull null

                if (mtype == "Film") Movie(title = title, poster = poster, id = id)
                else TvShow(title = title, poster = poster, id = id)
            }

            if (movies.isNotEmpty()) {
                categories.add(Category(name = title.text(), list = movies))
            }
        }

        return categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isEmpty() || page > 1) {
            return emptyList()
        }

        initializeService()
        val document = service.search(
            query = query,
        )

        val results = document.select("div.search-item")
            .mapNotNull {
                val id = it
                    .attr("onclick").substringAfter("/").substringBefore("'")
                if (id.isEmpty()) return@mapNotNull null
                val title = it.selectFirst("div.search-title")
                    ?.text()?.replace("\\'","'")
                    ?: ""
                val poster = it.selectFirst("img")
                    ?.attr("src")
                    ?: ""

                if (title.contains(" - Saison ") || title.contains(" - Intégrale "))
                    TvShow(
                        id = id,
                        title = title,
                        poster = poster,
                    )
                else
                    Movie(
                        id = id,
                        title = title,
                        poster = poster,
                    )
            }

        return results
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        initializeService()
        val document = try {
            service.getCategorie( page)
        } catch (e: HttpException) {
            when (e.code()) {
                404 -> return emptyList()
                else -> throw e
            }
        }

        val movies = document.select("div.short > div.short-in").mapNotNull { movie ->
            val mtype = movie.selectFirst("span.mli-type a")?.text()
            if (mtype == null) return@mapNotNull null
            val title = movie.selectFirst("div.short-title")?.text() ?: "Item"
            val href = movie.selectFirst("a.short-poster")
            val poster = href?.selectFirst("> img")?.attr("src")
            val id = href?.attr("href")?.substringAfterLast("=")?.takeIf{ it.isNotBlank() }
            if (id == null) return@mapNotNull null

            if (mtype != "Film") return@mapNotNull null
            Movie(title = title, poster = poster, id = id)
        }

        return movies
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        initializeService()
        val document = try {
            service.getCategorie(page)
        } catch (e: HttpException) {
            if (e.code() == 404) return emptyList()
            else throw e
        }
        val tvshows = document.select("div.short > div.short-in").mapNotNull { movie ->
            val mtype = movie.selectFirst("span.mli-type a")?.text()

            if (mtype == null) return@mapNotNull null
            val title = movie.selectFirst("div.short-title")?.text() ?: "Item"
            val href = movie.selectFirst("a.short-poster")
            val poster = href?.selectFirst("> img")?.attr("src")
            val id = href?.attr("href")?.substringAfterLast("=")?.takeIf{ it.isNotBlank() }
            if (id == null) return@mapNotNull null
            if (mtype == "Film") return@mapNotNull null
            TvShow(title = title, poster = poster, id = id)
        }

        return tvshows
    }

    override suspend fun getMovie(id: String): Movie {
        initializeService()
        val document = service.getItem(id)

        val md = document.selectFirst("div#manga-data")
        if (md == null) return Movie()

        val votes = document.selectFirst("div.fr-votes")
        val rating = if (votes != null) getRating(votes) else null
        val actors = extractActors(document)
        val title = md.attr("data-title")
        val poster = md.attr("data-affiche")

        val movie = Movie( id = md.attr("data-newsid"),
            title = title,
            poster = poster,
            trailer = md.attr("data-trailer").takeIf { it.isNotBlank() }
                .let { "https://www.youtube.com/watch?v=$it" },
            released = document.selectFirst("span.release")?.text(),
            overview = document.selectFirst("div.fdesc")?.text(),
            genres = document.select("span.genres")
                .select("a").mapNotNull {
                    Genre(
                        id = it.attr("href").substringBeforeLast("/").substringAfterLast("/"),
                        name = it.text(),
                    )
                },
            rating = rating,
            directors = document.select("ul#s-list li")
                .find {
                    it.selectFirst("span")?.text()?.contains("Director:") == true
                } ?.select("a")?.mapIndexedNotNull { index, it ->
                    People(
                        id = "director$index",
                        name = it.text(),
                    )
                } ?: emptyList(),
            cast = actors.map {
                People(
                    id = it[0],
                    name = it[0],
                    image = it[1]
                )
            }
        )
        return movie
    }

    fun getRating(votes: Element): Double {
        val voteplus = votes.selectFirst("span.ratingtypeplusminus")
            ?.text()
            ?.toIntOrNull() ?: 0

        val votenum = votes.select("span[id]")
            .last()
            ?.text()
            ?.toIntOrNull() ?: 0

        val rating = if (votenum >= voteplus && votenum > 0) {
            round((votenum - (votenum - voteplus) / 2.0) / votenum * 100) / 10
        } else 0.0

        return rating
    }

    fun extractActors(document: Document): List<List<String>> {
        val scriptContent = document.select("script").joinToString("\n") { it.data() }
        val arrayContentRegex = Regex("""actorData\s*=\s*\[(.*?)];""", RegexOption.DOT_MATCHES_ALL)

        return arrayContentRegex.find(scriptContent)
            ?.groupValues?.get(1)
            ?.let { content ->
                Regex(""""(.+?)\s*\(.*?\)\s*-\s*([^"]+)"""")
                    .findAll(content)
                    .map { match ->
                        val actorName = match.groupValues[1].trim()
                        val poster = match.groupValues[2].trim()
                        listOf(actorName, poster)
                    }
                    .toList()
            } ?: emptyList()
    }

    override suspend fun getTvShow(id: String): TvShow {
        initializeService()
        val document = service.getItem(id)

        val md = document.selectFirst("div#manga-data")
        if (md == null) return TvShow()

        val votes = document.selectFirst("div.fr-votes")
        val rating = if (votes != null) getRating(votes) else null
        val actors = extractActors(document)
        val title = md.attr("data-title")
        val poster = md.attr("data-affiche")
        val seasonNumber = title.substringAfter("Saison ").trim().toIntOrNull() ?: 0

        val tvShow = TvShow( id = md.attr("data-newsid"),
                             title = title,
                             poster = poster,
                             trailer = md.attr("data-trailer").takeIf { it.isNotBlank() }
                                 .let { "https://www.youtube.com/watch?v=$it" },
                             released = document.selectFirst("span.release")?.text(),
                             overview = document.selectFirst("div.fdesc")?.text(),
                             genres = document.select("span.genres")
                                        .select("a").mapNotNull {
                                            Genre(
                                                id = it.attr("href").substringBeforeLast("/").substringAfterLast("/"),
                                                name = it.text(),
                                            )
                                        },
                            rating = rating,
                            directors = document.select("ul#s-list li")
                                            .find {
                                                it.selectFirst("span")?.text()?.contains("Director:") == true
                                            } ?.select("a")?.mapIndexedNotNull { index, it ->
                                                People(
                                                    id = "director$index",
                                                    name = it.text(),
                                                )
                                            } ?: emptyList(),
                            cast = actors.map {
                                        People(
                                            id = it[0],
                                            name = it[0],
                                            image = it[1]
                                        )
                                },
                            seasons = listOf(Season(
                                id = id,
                                number = seasonNumber,
                                title = if (title.contains("- Saison")) "Saison "+title.substringAfter("- Saison ") else title,
                                poster = poster
                            ))

        )

        return tvShow
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        initializeService()

        val episodesData = try {
            service.getEpisodesData(seasonId)
        } catch (e: Exception) {
            return emptyList()
        }

        val result = mutableListOf<Episode>()
        var number = 1
        val maps = listOf(episodesData.vf, episodesData.vostfr, episodesData.vo )
        while (maps.any { it?.containsKey(number.toString()) == true }) {
            val info = episodesData.info?.get(number.toString())
            result.add(
                Episode(
                    id = "$seasonId/$number",
                    number = number,
                    poster = info?.poster ?: "",
                    title = info?.title?.replace("\\'", "'") ?: "Episode $number",
                    overview = info?.synopsis?.replace("\\'", "'") ?: ""
                )
            )

            number++
        }

        return result
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return Genre("","",emptyList())
    }

    override suspend fun getPeople(id: String, page: Int): People {
        return People(id = id, name = id)
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        initializeService()

        val servers = when (videoType) {
            is Video.Type.Episode -> {
                val (tvShowId, tvShowNumber) = id.split("/")
                val episodesData = try {
                    service.getEpisodesData(tvShowId)
                } catch (e: Exception) {
                    null
                }
                val tvShowServers = mutableListOf<Video.Server>()

                episodesData?.vf?.get(tvShowNumber)?.forEach { (provider, url) ->
                    tvShowServers.add(Video.Server(
                        id = "vf$provider",
                        name = provider.replaceFirstChar { it.uppercase() }+" (VF)",
                        src = url
                    ))
                }
                episodesData?.vostfr?.get(tvShowNumber)?.forEach { (provider, url) ->
                    tvShowServers.add(Video.Server(
                        id = "vostfr$provider",
                        name = provider.replaceFirstChar { it.uppercase() }+" (VOSTFR)",
                        src = url
                    ))
                }
                episodesData?.vo?.get(tvShowNumber)?.forEach { (provider, url) ->
                    tvShowServers.add(Video.Server(
                        id = "vo$provider",
                        name = provider.replaceFirstChar { it.uppercase() }+" (VO)",
                        src = url
                    ))
                }
                tvShowServers
            }
            is Video.Type.Movie -> {
                val movieServers = mutableListOf<Video.Server>()
                val episodesData = try {
                    service.getEpisodesData(id)
                } catch (e: Exception) {
                    null
                }

                val counts = mutableMapOf<String, Int>()

                fun addServers(map: Map<String, Map<String, String>>?, lang: String) {
                    map?.values?.forEach { servers ->
                        servers.forEach { (provider, url) ->
                            val key = "${provider}_$lang"
                            val count = counts.getOrDefault(key, 0) + 1
                            counts[key] = count
                            val suffix = if (count > 1) " $count" else ""
                            
                            movieServers.add(Video.Server(
                                id = "${lang.lowercase()}${provider}$count",
                                name = provider.replaceFirstChar { it.uppercase() }+" ($lang)$suffix",
                                src = url
                            ))
                        }
                    }
                }

                addServers(episodesData?.vf, "VF")
                addServers(episodesData?.vostfr, "VOSTFR")
                addServers(episodesData?.vo, "VO")
                
                movieServers
            }
        }

        return servers
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val video = Extractor.extract(server.src)
        if (video.subtitles.isNotEmpty()) {
            if (!server.name.contains("VOSTFR")) {
                // Disable subtitles when not watching in VOSTFR
                return video.copy(
                    subtitles = emptyList()
                )
            } else {
                // otherwise select the initial subtitle
                video.subtitles.forEach {
                    if (it.initialDefault) {
                        it.default = true
                    }
                }
            }
        }

        return video
    }

    override suspend fun onChangeUrl(forceRefresh: Boolean): String {
        FrenchStreamProvider.changeUrlMutex.withLock {
            if (forceRefresh || UserPreferences.getProviderCache(this,UserPreferences.PROVIDER_AUTOUPDATE) != "false") {
                val addressService = FrenchMangaService.buildAddressFetcher()
                try {
                    val document = addressService.getHome()

                    val fsUrl = document.select("div.container > div.url-card")
                        .selectFirst("a")
                        ?.attr("href")
                        ?.trim()
                    if (!fsUrl.isNullOrEmpty()) {
                        val fsdoc = addressService.loadPage(fsUrl)
                        var newUrl = fsdoc
                            .selectFirst("li.submenu:has(a:contains(ANIMES)) a")
                            ?.attr("href")
                        if (newUrl.isNullOrEmpty()) throw Exception()
                        val finalUrl = addressService.followPage(newUrl)
                        newUrl = finalUrl.raw().request.url.toString()
                        newUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
                        UserPreferences.setProviderCache(this,UserPreferences.PROVIDER_URL, newUrl)
                        UserPreferences.setProviderCache(
                            this,
                            UserPreferences.PROVIDER_LOGO,
                            newUrl + "favicon-96x96.png"
                        )
                    }
                } catch (e: Exception) {
                    // In case of failure, we'll use the default URL
                    // No need to throw as we already have a fallback URL
                }
            }
            service = FrenchMangaService.build(baseUrl)
            serviceInitialized = true
        }
        return baseUrl
    }

    private suspend fun initializeService() {
        initializationMutex.withLock {
            if (serviceInitialized) return
            onChangeUrl()
        }
    }

    private interface FrenchMangaService {
        companion object {
            private val client = NetworkClient.default.newBuilder()
                .build()
            fun buildAddressFetcher(): FrenchMangaService {
                val addressRetrofit = Retrofit.Builder()
                    .baseUrl(FrenchStreamProvider.portalUrl)

                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)

                    .build()

                return addressRetrofit.create(FrenchMangaService::class.java)
            }
            fun build(baseUrl: String): FrenchMangaService {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(FrenchMangaService::class.java)
            }
        }

        @GET
        suspend fun loadPage(
            @Url url: String
        ): Document

        @GET
        suspend fun followPage(
            @Url url: String
        ): Response<ResponseBody>

        @GET(".")
        suspend fun getHome(
            @Header("cookie") cookie: String = "dle_skin=MGM"
        ): Document

        @GET("index.php")
        suspend fun getCategorie(
            @Query("cstart") cstart: Int = 1,
            @Query("do") vdo: String = "cat",
            @Query("category") categorie: String = "manga-streaming-1",
            @Header("cookie") cookie: String = "dle_skin=MGM"
        ): Document

        @FormUrlEncoded
        @POST("engine/ajax/search.php")
        suspend fun search(
            @Field("query") query: String,
            @Field("page") page: Int = 1
        ): Document

        @FormUrlEncoded
        @POST("index.php")
        suspend fun getItem(
            @Query("newsid") id: String,
            @Field("cookie") skin_name: String = "skin_name=MGV3",
            @Field("cookie") skin_change: String = "action_skin_change=yes",
            @Header("cookie") dle_skin: String = "dle_skin=MGV1"
        ): Document

        @GET("engine/ajax/manga_episodes_api.php")
        suspend fun getEpisodesData(
            @Query("id") id: String,
            @Header("Cookie") cookie: String = "dle_skin=MGV1",
            @Header("X-Requested-With") requestedWith: String = "XMLHttpRequest"
        ): EpisodesData
    }
}
