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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
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

    override suspend fun getHome(): List<Category> = coroutineScope {

        initializeService()
        val document = service.loadPage(homePath)

        val derniersDeferred = document
            .selectFirst("div#dernieajouts")
            ?.select("div#hann")
            ?.mapNotNull { element ->

                val link = element.selectFirst("a") ?: return@mapNotNull null
                val href = link.attr("href")

                async(Dispatchers.IO) {

                    val docimg = service.loadPage(href)
                    val img = docimg.selectFirst("img")
                        ?.attr("src")
                        ?.replace("/original/", "/w300/")

                    Movie(
                        id = href,
                        title = link.text().trim(),
                        poster = img
                    )
                }
            }
            ?.awaitAll()
            ?: emptyList()

        val topDeferred = document
            .selectFirst("div.column2")
            ?.select("a:has(div.trend_unity)")
            ?.mapNotNull { element ->

                val href = element.attr("href")

                async(Dispatchers.IO) {

                    val docimg = service.loadPage(href)
                    val img = docimg.selectFirst("img")
                        ?.attr("src")
                        ?.replace("/original/", "/w300/")

                    Movie(
                        id = href,
                        title = element
                            .selectFirst("div.trend_title")
                            ?.text()
                            ?.trim()
                            ?.substringBeforeLast(" (")
                            ?: "",
                        banner = img,
                    )
                }
            }
            ?.awaitAll()
            ?: emptyList()

        listOf(
            Category(Category.FEATURED, topDeferred),
                    Category("Derniers Ajouts", derniersDeferred),
        )
    }
    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> = coroutineScope {
        initializeService()

        if (query.isEmpty()) {
            val document = service.loadPage(homePath)
            val genres = document.selectFirst("div.drop-down__menu-box")
                ?.select("li")
                ?.map {
                    Genre(
                        name = it.text(),
                        id = it.text() + "/" +
                                it.attr("onclick")
                                    .substringAfter("'")
                                    .substringBeforeLast("/")
                    )
                } ?: emptyList()

            return@coroutineScope genres
        }
        if (page > 1) return@coroutineScope emptyList()

        val document = service.search(
            homePath,
            query = query
        )

        val deferredResults = document.select("div#hann").mapNotNull { element ->

            val link = element.selectFirst("a") ?: return@mapNotNull null
            val href = link.attr("href")

            async(Dispatchers.IO) {

                val docimg = service.loadPage(href)
                val img = docimg.selectFirst("img")
                    ?.attr("src")
                    ?.replace("/original/", "/w300/")

                Movie(
                    id = href,
                    title = link.text().trim().substringBeforeLast(" ("),
                    poster = img
                )
            }
        }

        deferredResults.awaitAll()
    }

    override suspend fun getMovies(page: Int): List<Movie> = coroutineScope {
        initializeService()

        if (moviePath.isEmpty()) {
            val docmob = service.loadPage(homePath)
            moviePath = docmob.selectFirst("div.drop-down__menu-box")
                ?.selectFirst("li")
                ?.attr("onclick")
                ?.substringAfter("'")
                ?.substringBeforeLast("/")
                ?: ""
        }

        if (moviePath.isEmpty()) return@coroutineScope emptyList()

        val document = service.loadPage("$moviePath/${page - 1}")

        val deferredMovies = document.select("div#hann").mapNotNull { element ->

            val link = element.selectFirst("a") ?: return@mapNotNull null
            val href = link.attr("href")

            async(Dispatchers.IO) {

                val docimg = service.loadPage(href)
                val img = docimg.selectFirst("img")
                    ?.attr("src")
                    ?.replace("/original/", "/w300/")

                Movie(
                    id = href,
                    title = link.text().trim(),
                    poster = img
                )
            }
        }

        deferredMovies.awaitAll()
    }

    override suspend fun getTvShows(page: Int): List<TvShow> = emptyList()

    override suspend fun getMovie(id: String): Movie {
        initializeService()
        val document = service.loadPage(id)

        val bloc = document.selectFirst("div.column16")
            ?.selectFirst("b[style*=text-transform: uppercase]")

        var title : String?= ""
        var released : String?= ""
        var quality : String?= ""
        var version : String ?=""
        if (bloc != null) {
            val fullText = bloc.ownText().trim()
            val regex = Regex("(.+)\\s*\\((\\d{4})\\)\\s*(\\[[^\\]]+)?")
            val match = regex.find(fullText)

            title = match?.groupValues?.get(1)?.trim()
            released = match?.groupValues?.get(2)?.trim()
            version = match?.groupValues?.get(3)?.trim()?.replaceFirst("[", " ")
            quality = bloc.selectFirst("i")?.text()?.trim()
        }
        val header = document.selectFirst("b:containsOwn(CANEVAS DU FILM)")
        val parentP = header?.closest("p")
        val descP = parentP?.nextElementSibling()
        val overview = descP?.text()?.trim()

        val movie = Movie(
            id = id,
            title = title ?: "",
            released = released,
            quality = quality+version,
            overview = overview,
            poster = document.selectFirst("img")?.attr("src"),
        )

        return movie
    }

    override suspend fun getTvShow(id: String): TvShow {
        return TvShow()
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        return emptyList()
    }

    override suspend fun getGenre(id: String, page: Int): Genre = coroutineScope {
        initializeService()
        val (name, url) = id.split("/", limit = 2)

        val document = service.loadPage("$url/${page - 1}")

        val deferredMovies = document.select("div#hann").mapNotNull { element ->

            val link = element.selectFirst("a") ?: return@mapNotNull null
            val href = link.attr("href")

            async(Dispatchers.IO) {

                val docimg = service.loadPage(href)
                val img = docimg.selectFirst("img")
                    ?.attr("src")
                    ?.replace("/original/", "/w300/")

                Movie(
                    id = href,
                    title = link.text().trim(),
                    poster = img
                )
            }
        }

        val movies = deferredMovies.awaitAll()

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
        suspend fun loadPage(
            @Url url: String
        ): Document

        @GET
        suspend fun loadPageRaw(
            @Url url: String
        ): Response<ResponseBody>

        @POST
        @FormUrlEncoded
        suspend fun search(
            @Url url: String,
            @Field("searchword") query: String
        ): Document
    }
}