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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url
import retrofit2.Response
import okhttp3.ResponseBody
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.UserPreferences
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST
import kotlin.collections.map
import kotlin.collections.mapNotNull
import kotlin.math.round

object FrenchStreamProvider : Provider, ProviderPortalUrl, ProviderConfigUrl {
    override val name = "FrenchStream"

    override val defaultPortalUrl: String = "http://fstream.info/"

    override val portalUrl: String = defaultPortalUrl
        get() {
            val cachePortalURL = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_PORTAL_URL)
            return cachePortalURL.ifEmpty { field }
        }

    override val defaultBaseUrl: String = "https://fs2.lol/"
    override val baseUrl: String = defaultBaseUrl
        get() {
            val cacheURL = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL)
            return cacheURL.ifEmpty { field }
        }

    override val logo: String
        get() {
            val cacheLogo = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_LOGO)
            return cacheLogo.ifEmpty { portalUrl + "favicon-96x96.png" }
        }
    override val language = "fr"
    override val changeUrlMutex = Mutex()

    private lateinit var service: Service
    private var serviceInitialized = false
    private val initializationMutex = Mutex()

    override suspend fun getHome(): List<Category> {
        initializeService()
        val cookie = if (UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_NEW_INTERFACE) != "false") "dle_skin=VFV2"
                     else "dle_skin=VFV1"
        val document = service.getHome(cookie)
        val categories = mutableListOf<Category>()
        if ( cookie.contains("VFV2")) {
            document.select("div.pages.clearfix div.gallery-container").map { cat_item ->

                val title = cat_item
                    .selectFirst("div.sectionTitlesContainer a h1")
                    ?.text()
                    ?: ""

                val movies = cat_item
                    .select("div.short.cinema-item, div.short.vod-item")
                    .mapNotNull { item ->
                        val a = item.selectFirst("a.cinema-clickable-area, a.vod-clickable-area") ?: return@mapNotNull null
                        val link = a.attr("href")
                        val href = link.substringAfterLast("/")
                        val title = a.selectFirst("div.cinema-title, div.vod-title")?.text() ?: ""
                        val poster = a.selectFirst("img")?.attr("data-src") ?: ""
                        if (link.startsWith("/s-tv/"))
                            TvShow(
                                id = href,
                                title = title,
                                poster = poster
                            )
                        else
                            Movie(
                                id = href,
                                title = title,
                                poster = poster
                            )
                    }

                if (movies.isNotEmpty()) {
                    categories.add(
                        Category(
                            name = title,
                            list = movies
                        )
                    )
                }
            }

        } else {
            categories.add(
                Category(
                    name = "TOP Séries",
                    list = document.select("div.pages.clearfix").getOrNull(1)?.select("div.short")
                        ?.map {
                            TvShow(
                                id = it.selectFirst("a.short-poster")
                                    ?.attr("href")?.substringAfterLast("/")
                                    ?: "",
                                title = listOfNotNull(
                                    it.selectFirst("div.short-title")?.text(),
                                    it.selectFirst("span.film-version")?.text(),
                                ).joinToString(" - "),
                                poster = it.selectFirst("img")
                                    ?.attr("src")
                                    ?: "",
                            )
                        } ?: emptyList(),
                )
            )

            categories.add(
                Category(
                    name = "TOP Films",
                    list = document.select("div.pages.clearfix").getOrNull(0)?.select("div.short")
                        ?.map {
                            Movie(
                                id = it.selectFirst("a.short-poster")
                                    ?.attr("href")?.substringAfterLast("/")
                                    ?: "",
                                title = it.selectFirst("div.short-title")
                                    ?.text()
                                    ?: "",
                                poster = it.selectFirst("img")
                                    ?.attr("src")
                                    ?: "",
                            )
                        } ?: emptyList(),
                )
            )

            categories.add(
                Category(
                    name = "Box office",
                    list = document.select("div.pages.clearfix").getOrNull(2)?.select("div.short")
                        ?.map {
                            Movie(
                                id = it.selectFirst("a.short-poster")
                                    ?.attr("href")?.substringAfterLast("/")
                                    ?: "",
                                title = it.selectFirst("div.short-title")
                                    ?.text()
                                    ?: "",
                                poster = it.selectFirst("img")
                                    ?.attr("src")
                                    ?: "",
                            )
                        } ?: emptyList(),
                )
            )
        }

        return categories
    }

    suspend fun ignoreSource(source: String, href: String): Boolean {
        if (arrayOf("Netu").any {
                it.equals(
                    source.trim(),
                    true
                )
            })
            return true
        if (source.trim().equals("Dood.Stream", ignoreCase = true) && href.contains("/bigwar5/")) return true
        return false
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (page > 1) return emptyList()
        initializeService()
        if (query.isEmpty()) {
            val document = service.getHome()

            val genres = document.selectFirst("div.menu-section")?.select(">a")?.map {
                Genre(
                    id = it.attr("href").substringBeforeLast("/").substringAfterLast("/"),
                    name = it.text(),
                )
            } ?: emptyList()

            return genres
        }
        val document = service.search(
            query = query
        )
        val results = document.select("div.search-item")
            .mapNotNull {
                val id = it
                    .attr("onclick").substringAfter("/").substringBefore("'")
                if (id.isEmpty()) return@mapNotNull null
                val title = it.selectFirst("div.search-title")
                    ?.text()?.replace("\\'","'")
                    ?: ""
                var poster = it.selectFirst("img")
                    ?.attr("src")
                    ?: ""

                if (id.contains("-saison-"))
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

        val document = service.getMovies(page)

        val movies = document.select("div#dle-content>div.short").map {
            Movie(
                id = it.selectFirst("a.short-poster")
                    ?.attr("href")?.substringAfterLast("/")
                    ?: "",
                title = it.selectFirst("div.short-title")
                    ?.text()
                    ?: "",
                poster = it.selectFirst("img")
                    ?.attr("src"),
            )
        }

        return movies
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        initializeService()
        val document = service.getTvShows(page)

        val tvShows = document.select("div#dle-content>div.short").map {
            TvShow(
                id = it.selectFirst("a.short-poster")
                    ?.attr("href")?.substringAfterLast("/")
                    ?: "",
                title = it.selectFirst("div.short-title")
                    ?.text()
                    ?: "",
                poster = it.selectFirst("img")
                    ?.attr("src"),
            )
        }

        return tvShows
    }

    suspend fun getRating(votes: Element): Double {
        val voteplus = votes
            ?.selectFirst("span.ratingtypeplusminus")
            ?.text()
            ?.toIntOrNull() ?: 0

        val votenum = votes
            ?.select("span[id]")
            ?.last()
            ?.text()
            ?.toIntOrNull() ?: 0

        val rating = if (votenum >= voteplus && votenum > 0) {
            round((votenum - (votenum - voteplus) / 2.0) / votenum * 100) / 10
        } else 0.0

        return rating
    }

    override suspend fun getMovie(id: String): Movie {
        initializeService()
        val document = service.getMovie(id)
        val actors = extractActors(document)
        val trailerURL = document.selectFirst("div#film-data")
                                  ?.attr("data-trailer")
                                  ?.let { "https://www.youtube.com/watch?v=$it" }

        val votes = document.selectFirst("div.fr-votes")
        val rating = if (votes != null) getRating(votes) else null
        val movie = Movie(
            id = id,
            title = document.selectFirst("meta[property=og:title]")?.attr("content")
                ?: "",
            overview = document.selectFirst("div#s-desc")
                ?.apply {
                    selectFirst("p.desc-text")?.remove()
                }
                ?.text()
                ?.trim()
                ?: "",
            released = document.selectFirst("span.release_date")
                ?.text()
                ?.substringAfter("-")
                ?.trim(),
            runtime = document.select("span.runtime")
                .text().substringAfter(" ").let {
                    val hours = it.substringBefore("h").toIntOrNull() ?: 0
                    val minutes =
                        it.substringAfter("h").trim().toIntOrNull() ?: 0
                    hours * 60 + minutes
                }.takeIf { it != 0 },
            quality = document.selectFirst("span[id=film_quality]")
                ?.text(),
            poster = document.selectFirst("img.dvd-thumbnail")
                ?.attr("src")
                ?: "",
            trailer = trailerURL,
            genres = document.select("span.genres")
                .select("a").mapNotNull {
                    Genre(
                        id = it.attr("href").substringBeforeLast("/").substringAfterLast("/"),
                        name = it.text(),
                    )
                },
            directors = document.select("ul#s-list li")
                .find {
                    it.selectFirst("span")?.text()?.contains("alisateur") == true
                }
                ?.select("a")?.mapIndexedNotNull { index, it ->
                    People(
                        id = "director$index",
                        name = it.text(),
                    )
                }
                ?: emptyList(),
            cast = actors.map {
                People(
                    id = it[0].replace(" ", "+"),
                    name = it[0],
                    image = it[1]
                )
            },
            rating = rating

        )

        return movie
    }

    override suspend fun getTvShow(id: String): TvShow {
        initializeService()
        val document = service.getTvShow(id)
        val actors = extractActors(document)
        val versions = extractTvShowVersions(document)
        val poster = document.selectFirst("img.dvd-thumbnail")
            ?.attr("src") ?: ""
        val votes = document.selectFirst("div.fr-votes")
        val rating = if (votes != null) getRating(votes) else null
        val title = document.selectFirst("meta[property=og:title]")
            ?.attr("content")
            ?: ""

        val seasonNumber = title.substringAfter("Saison ").trim().toIntOrNull() ?: 0

        val tvShow = TvShow(
            id = id,
            title = title,
            overview = document.selectFirst("div.fdesc > p")
                ?.text()
                ?.trim()
                ?: "",
            released = document.selectFirst("span.release")
                ?.text()
                ?.substringBefore("-")
                ?.trim(),
            runtime = document.select("span.runtime")
                .text().substringAfter(" ").let {
                    val hours = it.substringBefore("h").toIntOrNull() ?: 0
                    val minutes =
                        it.substringAfter("h").trim().toIntOrNull() ?: 0
                    hours * 60 + minutes
                }.takeIf { it != 0 },
            quality = document.selectFirst("span[id=film_quality]")
                ?.text(),
            poster = poster,
            seasons = versions.map { version ->
                Season(
                    id = "$id/$version/-$version",
                    number = seasonNumber,
                    title = "Épisodes - "+version.uppercase()
                )
            },
            genres = document.select("span.genres").text().
                split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map {
                    Genre(
                        id = it,
                        name = it,
                    )
                },
            directors = document.select("ul#s-list li")
                .find {
                    it.selectFirst("span")?.text()?.contains("alisateur") == true
                }
                ?.select("a")?.mapIndexedNotNull { index, it ->
                    People(
                        id = "director$index",
                        name = it.text(),
                    )
                }
                ?: emptyList(),
            cast = actors.map {
                People(
                    id = it[0].replace(" ", "+"),
                    name = it[0],
                    image = it[1]
                )
            },
            rating = rating
        )

        return tvShow
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        initializeService()
        val (tvShowId, tvShowLang, divFilter) = seasonId.split("/")

        val document = service.getTvShow(tvShowId, "dle_skin=VFV2")

        var defaultPoster = document.selectFirst("div.fposter > div.dvd-cover > img")?.attr("src") ?: ""

        val template = document.select("script#template1").first()?.data()?:""
        val innerDoc = Jsoup.parse(template)

        val episodes = innerDoc.select("div.episode-container").mapNotNull { epDiv ->

            val number = epDiv.attr("id").removePrefix("episode-").toIntOrNull() ?: 0
            val poster = epDiv.selectFirst("img.episode-image")?.attr("src")?.takeIf { it.isNotEmpty() } ?: defaultPoster

            val title = epDiv.selectFirst("div.episode-details > div.episode-title")?.text()?.substringAfter(":")?.trim() ?: ""

            val url = epDiv.selectFirst("button[data-episode$='"+divFilter+"'][data-url^='h']")?.attr("data-url") ?: ""

            if (url.isEmpty()) return@mapNotNull null
            Episode(
                id = "$tvShowId/$tvShowLang/$number",
                number = number,
                poster = poster,
                title = title,
                overview = epDiv.selectFirst("div.episode-details > div.episode-synopsis")?.text()?: ""
            )
        }

        return episodes
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        initializeService()
        val document = service.getGenre(id, page)

        val genre = Genre(
            id = id,
            name = "",
            shows = document.select("div#dle-content>div.short").map {
                Movie(
                    id = it.selectFirst("a.short-poster")
                        ?.attr("href")?.substringAfterLast("/")
                        ?: "",
                    title = it.selectFirst("div.short-title")
                        ?.text()
                        ?: "",
                    poster = it.selectFirst("img")
                        ?.attr("src"),
                )
            },
        )

        return genre
    }

    override suspend fun getPeople(id: String, page: Int): People {
        initializeService()

        val document = try {
            service.getPeople(id, page)
        } catch (e: HttpException) {
            when (e.code()) {
                404 -> return People(id, "")
                else -> throw e
            }
        }

        val people = People(
            id = id,
            name = "",
            filmography = document.select("div#dle-content > div.short").mapNotNull {
                val href = it.selectFirst("a.short-poster")
                    ?.attr("href")
                    ?: ""
                val id = href.substringAfterLast("/")

                val title = it.selectFirst("div.short-title")
                    ?.text()
                    ?: ""
                val poster = it.selectFirst("img")
                    ?.attr("src")
                    ?: ""

                if (href.contains("films/")) {
                    Movie(
                        id = id,
                        title = title,
                        poster = poster,
                    )
                } else if (href.contains("s-tv/")) {
                    TvShow(
                        id = id,
                        title = title,
                        poster = poster,
                    )
                } else {
                    null
                }
            },
        )

        return people
    }

    fun extractTvShowVersions(document: Document): MutableList<String> {
        val versions = listOf("vostfr", "vf")
        val found = mutableListOf<String>()

        for (version in versions) {
            val hasAtLeastOneEp = document
                .select("div#episodes-"+version+"-data > div")
                .any { epDiv ->
                    epDiv.attributes()
                        .asList()
                        .any { attr ->
                            attr.value.startsWith("http")
                        }
                }
            if (hasAtLeastOneEp) {
                found.add(version)
            }
        }

        return found
    }

    suspend fun extractActors(document: Document): List<List<String>> {
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

    data class VideoProvider(
        val id: Number,
        val order: Int,
        val name: String,
        val lang: String,
        val url: String
    )

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        initializeService()

        val servers = when (videoType) {
            is Video.Type.Episode -> {
                val (tvShowId, tvShowLang, tvShowNumber) = id.split("/")

                val document = service.getTvShow(tvShowId)

                document.selectFirst("div#episodes-"+tvShowLang+"-data")
                    ?.selectFirst("div[data-ep="+tvShowNumber+"]")
                    ?.attributes()
                    ?.asList()
                    ?.filter { it.key.startsWith("data-") && it.value.startsWith("http") }
                    ?.mapIndexedNotNull { id, attr ->
                        val name = attr.key.removePrefix("data-").replaceFirstChar{ it.uppercase() }
                        if (ignoreSource(name, attr.value)) return@mapIndexedNotNull null
                        Video.Server(
                            id = "vid${id}",
                            name = name,
                            src = attr.value
                        )
                    } ?: emptyList()
            }

            is Video.Type.Movie -> {
                val document = service.getMovie(id)
                val providerIndex = mutableMapOf<String, Int>()
                var pIndex = 0

                val labels = mapOf(
                    "vff" to "TrueFrench",
                    "vfq" to "French",
                    "vostfr" to "VOSTFR",
                    "vo" to "VO"
                )

                document.selectFirst("div#film-data")
                    ?.attributes()
                    ?.asList()
                    ?.filter { it.key.startsWith("data-") && it.value.startsWith("http") }
                    ?.mapIndexedNotNull { id, attr ->
                        val name = attr.key.removePrefix("data-")

                        val provider = name.removeSuffix("vo").removeSuffix("vostfr").removeSuffix("vfq").removeSuffix("vff")
                        val lang = name.removePrefix(provider)

                        if (lang.isEmpty()) return@mapIndexedNotNull null
                        val order = providerIndex.getOrPut(provider) { pIndex++ }

                        VideoProvider(
                            id = id,
                            name = provider,
                            lang = lang,
                            url = attr.value,
                            order = order
                        )
                    }
                    ?.sortedWith(
                        compareBy<VideoProvider> { it.order }.thenBy {
                            when (it.lang) {
                                "vostfr" -> 4
                                "vo"     -> 3
                                "vfq"    -> 2
                                "vff"    -> 1
                                else     -> 0
                            }
                        }
                    )
                    ?.map {
                        Video.Server(
                            id = "vid${it.id}",
                            name = "${it.name.replaceFirstChar{ it.uppercase() } } ("+(labels[it.lang] ?: it.lang)+")",
                            src = it.url
                        )
                    } ?: emptyList()
            }
        }

        return servers
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val finalUrl = if (server.src.contains("newplayer", ignoreCase = true)) {
            val response = service.getRedirectLink(server.src)
                .let { response -> response.raw() as okhttp3.Response }
            response.request.url.toString()
        } else {
            server.src
        }
        
        val video = Extractor.extract(finalUrl)

        return video
    }

    /**
     * Initializes the service with the current domain URL.
     * This function is necessary because the provider's domain frequently changes.
     * We fetch the latest URL from a dedicated website that tracks these changes.
     */
    override suspend fun onChangeUrl(forceRefresh: Boolean): String {
        changeUrlMutex.withLock {
            if (forceRefresh || UserPreferences.getProviderCache(this,UserPreferences.PROVIDER_AUTOUPDATE) != "false") {
                val addressService = Service.buildAddressFetcher()
                try {
                    val document = addressService.getHome()

                    val newUrl = document.select("div.container > div.url-card")
                        .selectFirst("a")
                        ?.attr("href")
                        ?.trim()
                    if (!newUrl.isNullOrEmpty()) {
                        val newUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
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
            service = Service.build(baseUrl)
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

    private interface Service {

        companion object {
            private val client = OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .dns(DnsResolver.doh)
                .build()

            fun buildAddressFetcher(): Service {
                val addressRetrofit = Retrofit.Builder()
                    .baseUrl(portalUrl)

                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)

                    .build()

                return addressRetrofit.create(Service::class.java)
            }

            fun build(baseUrl: String): Service {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET(".")
        suspend fun getHome(
            @Header("Cookie") cookie: String = "dle_skin=VFV1"
        ): Document

        @FormUrlEncoded
        @POST("engine/ajax/search.php")
        suspend fun search(
            @Field("query") query: String,
            @Field("page") page: Int = 1
        ): Document

        @GET("films/page/{page}/")
        suspend fun getMovies(@Path("page") page: Int): Document

        @GET("s-tv/page/{page}")
        suspend fun getTvShows(@Path("page") page: Int): Document

        @GET("films/{id}")
        suspend fun getMovie(@Path("id") id: String): Document

        @GET("s-tv/{id}")
        suspend fun getTvShow(
            @Path("id") id: String,
            @Header("Cookie") cookie: String = "dle_skin=VFV1"
        ): Document


        @GET("film-en-streaming/{genre}/page/{page}")
        suspend fun getGenre(
            @Path("genre") genre: String,
            @Path("page") page: Int,
        ): Document

        @GET("xfsearch/actors/{id}/page/{page}")
        suspend fun getPeople(
            @Path("id") id: String,
            @Path("page") page: Int,
        ): Document

        @GET
        suspend fun getRedirectLink(@Url url: String): Response<ResponseBody>
    }
}
