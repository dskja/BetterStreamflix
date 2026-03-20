package com.streamflixreborn.streamflix.providers

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

object KidrazProvider : Provider, ProviderPortalUrl, ProviderConfigUrl {

    override val name = "Kidraz"

    override val defaultPortalUrl: String = "http://chezlesducs.free.fr/films.php"
    override val portalUrl: String = defaultPortalUrl
        get() {
            val cachePortalURL = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_PORTAL_URL)
            return cachePortalURL.ifEmpty{ field }
        }

    override val defaultBaseUrl: String = "https://www.kidraz.com/saby1jy/home/kidraz"
    override val baseUrl: String = defaultBaseUrl
        get() {
            val cacheURL = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL)
            return cacheURL.ifEmpty{ field }
        }

    override val logo: String
        get() {
            var cacheLogo = UserPreferences.getProviderCache(this,UserPreferences.PROVIDER_LOGO)
            return cacheLogo.ifEmpty { "https://www.kidraz.com/favicon.png" }
        }

    const val user_agent = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:147.0) Gecko/20100101 Firefox/147.0"

    private var homePath = ""
    private var moviePath = ""

    override val language = "fr"
    override val changeUrlMutex = Mutex()

    private lateinit var service: Service
    private var serviceInitialized = false
    private val initializationMutex = Mutex()
    
    data class FilmResponse(
        val films: List<KidrazFilm>,
        val total: Int? = null,
        val hasMore: Boolean? = null
    )

    data class KidrazFilm(
        val id: String,
        val title: String,
        val poster: String?,
        val link: String?,
        val cat: String? = null,
        val hd: Boolean? = null
    )

    override suspend fun getHome(): List<Category> = coroutineScope {
        initializeService()
        val doc = service.loadPage(homePath)

        // 1. Sections
        val categories = mutableListOf<Category>()

        // 2. Dynamic Section Discovery via content-row identification
        val carousels = doc.select(".trend-row, .showcase, .film-grid, .newfilms-wrap, .newfilms-section")
        
        carousels.forEach { rowContainer ->
            // 1. Find items: inside or nearby
            var items = rowContainer.select(".showcase-card, .film-card, .trend-card, a.trend-card")
            if (items.isEmpty()) {
                rowContainer.nextElementSibling()?.let { nextSib ->
                    items = nextSib.select(".showcase-card, .film-card, .trend-card, a.trend-card")
                }
            }
            if (items.isEmpty()) return@forEach

            var titleText = ""
            rowContainer.selectFirst(".trend-vignette, .vignette")?.let { vignette ->
                val vTitle = vignette.selectFirst(".trend-vignette-title, .vignette-title")
                val vCount = vignette.selectFirst(".trend-vignette-count, .vignette-count")
                if (vTitle != null) {
                    titleText = vTitle.text().trim()
                    if (vCount != null) {
                        titleText += " " + vCount.text().trim()
                    }
                }
            }

            if (titleText.isEmpty()) {
                var curr: org.jsoup.nodes.Element? = rowContainer
                while (curr != null && titleText.isEmpty()) {
                    var prev = curr.previousElementSibling()
                    while (prev != null) {
                        val hEl = prev.selectFirst("h2, h3, h4") ?: if (prev.tagName() in listOf("h2", "h3", "h4")) prev else null
                        if (hEl != null) {
                            titleText = hEl.text().trim()
                            break
                        }
                        val tEl = prev.selectFirst(".section-header, .title, .newfilms-header")
                        if (tEl != null) {
                            titleText = tEl.text().trim()
                            break
                        }
                        prev = prev.previousElementSibling()
                    }
                    if (titleText.isNotEmpty()) break
                    curr = curr.parent()
                    if (curr == null || curr.tagName().equals("body", true)) break
                }
            }

            if (titleText.isEmpty()) return@forEach

            var cleanKey = titleText
                .replace(Regex("""(?i)\s*\+\d+$"""), "")
                .replace(Regex("""(?i)tout voir|voir tout"""), "")
                .trim()


            val categoryTitle = if (cleanKey.isNotEmpty()) cleanKey else null
            if (categoryTitle == null) return@forEach

            if (categories.none { it.name.equals(categoryTitle, true) }) {
                val movies = items.mapNotNull { item ->
                    val a = if (item.tagName() == "a") item else item.selectFirst("a")
                    val img = item.selectFirst("img") ?: return@mapNotNull null
                    val rawTitle = (item.selectFirst(".showcase-card-title, .film-card-title, .trend-card-title")?.text()?.trim() 
                        ?: img.attr("alt")?.trim() 
                        ?: "Unknown").toString()
                    
                    val titleMatch = Regex("""^(.*?)\s*\((\d{4})\)\s*$""").find(rawTitle)
                    val name = titleMatch?.groupValues?.getOrNull(1)?.trim() ?: rawTitle.replace(Regex("""\(\d{4}\)"""), "").trim()
                    val year = titleMatch?.groupValues?.getOrNull(2) ?: Regex("""(\d{4})""").find(rawTitle)?.groupValues?.getOrNull(1)
                    
                    Movie(
                        id = a?.attr("href") ?: "",
                        title = name,
                        released = year,
                        poster = img.attr("src")?.replace("/original/", "/w300/")
                    )
                }.distinctBy { it.id }
                
                if (movies.isNotEmpty()) {
                    categories.add(Category(categoryTitle, movies))
                }
            }
        }

        categories
    }
    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> = coroutineScope {
        initializeService()

        if (query.isEmpty()) {
            val doc = service.loadPage(homePath)
            val genres = doc.select(".navbar-dropdown-grid a.navbar-dropdown-item").map { a ->
                val name = a.text().trim()
                val href = a.attr("href")
                val parts = href.substringAfter("/c/").split("/")
                val catid = if (parts.size >= 2) parts[1] else ""
                Genre(
                    name = name,
                    id = "$name|$catid"
                )
            }
            return@coroutineScope genres
        }

        val apiBase = homePath.substringBefore("/home/")
        val folder = apiBase.removePrefix("/").substringBefore("/")
        val pr = homePath.substringAfterLast("/")

        val response = service.search(
            "$apiBase/api_search.php",
            query = query,
            offset = (page - 1) * 20,
            limit = 20,
            folder = folder,
            pr = pr
        )

        response.films.map { film ->
            val titleMatch = Regex("^(.*?)\\s*\\((\\d{4})\\)\\s*$").find(film.title.trim())
            val displayTitle = titleMatch?.groupValues?.getOrNull(1)?.trim() ?: film.title
            val year = titleMatch?.groupValues?.getOrNull(2)

            Movie(
                id = film.link ?: "",
                title = displayTitle,
                released = year,
                poster = film.poster?.replace("/original/", "/w300/"),
                quality = if (film.hd == true) "HD" else null
            )
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> = coroutineScope {
        initializeService()

        val apiBase = homePath.substringBefore("/home/")
        val folder = apiBase.removePrefix("/").substringBefore("/")
        val pr = homePath.substringAfterLast("/")

        val response = service.apiFilms(
            "$apiBase/api_films.php",
            offset = (page - 1) * 20,
            limit = 20,
            folder = folder,
            pr = pr
        )

        response.films.map { film ->
            val titleMatch = Regex("^(.*?)\\s*\\((\\d{4})\\)\\s*$").find(film.title.trim())
            val displayTitle = titleMatch?.groupValues?.getOrNull(1)?.trim() ?: film.title
            val year = titleMatch?.groupValues?.getOrNull(2)

            Movie(
                id = film.link ?: "",
                title = displayTitle,
                released = year,
                poster = film.poster?.replace("/original/", "/w300/"),
                quality = if (film.hd == true) "HD" else null
            )
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> = emptyList()

    override suspend fun getMovie(id: String): Movie {
        initializeService()
        val doc = service.loadPage(id)

        val rawTitle = doc.selectFirst(".film-detail-title")?.text()?.trim() 
            ?: doc.selectFirst("title")?.text()?.trim() 
            ?: ""
        val titleMatch = Regex("^(.*?)\\s*\\((\\d{4})\\)\\s*$").find(rawTitle)
        val title = titleMatch?.groupValues?.getOrNull(1)?.trim() ?: rawTitle
        val year = titleMatch?.groupValues?.getOrNull(2)

        val poster = (doc.selectFirst(".film-detail-poster img")?.attr("src")
            ?: doc.selectFirst("img")?.attr("src"))?.replace("/original/", "/w300/")

        val overview = doc.selectFirst(".film-synopsis-text, .film-detail-synopsis")?.text()?.trim()
        val genre = doc.selectFirst(".film-detail-cat")?.text()?.trim()

        return Movie(
            id = id,
            title = title,
            released = year,
            overview = overview,
            genres = genre?.let { listOf(Genre(name = it, id = it)) } ?: listOf(),
            poster = poster,
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        return TvShow()
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        return emptyList()
    }

    override suspend fun getGenre(id: String, page: Int): Genre = coroutineScope {
        initializeService()
        val (name, catid) = id.split("|", limit = 2)

        val apiBase = homePath.substringBefore("/home/")
        val folder = apiBase.removePrefix("/").substringBefore("/")
        val pr = homePath.substringAfterLast("/")

        val response = service.apiCategory(
            "$apiBase/api_category.php",
            catid = catid,
            offset = (page - 1) * 20,
            limit = 20,
            folder = folder,
            pr = pr
        )

        val movies = response.films.map { film ->
            val titleMatch = Regex("^(.*?)\\s*\\((\\d{4})\\)\\s*$").find(film.title.trim())
            val displayTitle = titleMatch?.groupValues?.getOrNull(1)?.trim() ?: film.title
            val year = titleMatch?.groupValues?.getOrNull(2)

            Movie(
                id = film.link ?: "",
                title = displayTitle,
                released = year,
                poster = film.poster?.replace("/original/", "/w300/"),
                quality = if (film.hd == true) "HD" else null
            )
        }

        Genre(
            id = name,
            name = name,
            shows = movies
        )
    }

    override suspend fun getPeople(id: String, page: Int): People {
        return People(id="", name="")
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        initializeService()
        val document = service.loadPage(id)

        val url = document.selectFirst("iframe")?.attr("src")
        if (url == null) throw Exception("Video unavailable")
        val urlobj = url.toHttpUrl()

        return listOf(Video.Server(
            id = urlobj.host,
            name = urlobj.host.replace(".com", ""),
            src = url))
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val video = Extractor.extract(server.src)

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
                val url = portalUrl.toHttpUrl()
                val addressService = Service.buildAddressFetcher(url)
                try {
                    var document = addressService.loadPage(url.encodedPath.removePrefix("/"))
                    var link = document.selectFirst("a:contains(kidraz)")
                    var newUrl = link?.attr("href")

                    if (!newUrl.isNullOrEmpty()) {

                        newUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"

                        document = addressService.loadPage(newUrl)
                        var newPath = document.selectFirst("a#kidrazc")?.attr("href")?:""
                        val raw = addressService.loadPageRaw(newUrl+newPath)
                        if (raw.isSuccessful) {
                            val homeUrl = raw.raw().request.url
                            homePath = homeUrl.encodedPath //.removePrefix("/")

                            UserPreferences.setProviderCache(this,
                                UserPreferences.PROVIDER_URL, homeUrl.toString())
                            UserPreferences.setProviderCache(
                            this,
                            UserPreferences.PROVIDER_LOGO,
                            newUrl + "favicon.png")

                        }
                    }
                } catch (e: Exception) {
                    // In case of failure, we'll use the default URL
                    // No need to throw as we already have a fallback URL
                }
            }
            val url = baseUrl.toHttpUrl()
            service = Service.build(url)
            homePath = url.encodedPath.removePrefix("/")
            moviePath = ""

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
                .addInterceptor { chain ->
                    val newRequest = chain.request().newBuilder()
                        .addHeader("User-agent", user_agent)
                        .addHeader("Cookie", "g=true")
                        .build()
                    chain.proceed(newRequest)
                }
                .dns(DnsResolver.doh)
                .build()

            fun buildAddressFetcher(url: HttpUrl): Service {
                val burl = url.newBuilder()
                    .encodedPath("/")
                    .query(null)
                    .build()
                    .toString()

                val addressRetrofit = Retrofit.Builder()
                    .baseUrl(burl)
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)
                    .build()

                return addressRetrofit.create(Service::class.java)
            }

            fun build(url: HttpUrl): Service {
                val burl = url.newBuilder()
                    .encodedPath("/")
                    .query(null)
                    .build()
                    .toString()

                val retrofit = Retrofit.Builder()
                    .baseUrl(burl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(Service::class.java)
            }
        }


        @GET
        suspend fun search(
            @Url url: String,
            @Query("searchword") query: String,
            @Query("offset") offset: Int,
            @Query("limit") limit: Int,
            @Query("folder") folder: String,
            @Query("pr") pr: String
        ): FilmResponse

        @GET
        suspend fun apiFilms(
            @Url url: String,
            @Query("offset") offset: Int,
            @Query("limit") limit: Int,
            @Query("folder") folder: String,
            @Query("pr") pr: String
        ): FilmResponse

        @GET
        suspend fun apiCategory(
            @Url url: String,
            @Query("catid") catid: String,
            @Query("offset") offset: Int,
            @Query("limit") limit: Int,
            @Query("folder") folder: String,
            @Query("pr") pr: String
        ): FilmResponse

        @GET
        suspend fun loadPage(
            @Url url: String
        ): Document

        @GET
        suspend fun loadPageRaw(
            @Url url: String
        ): Response<ResponseBody>
    }
}