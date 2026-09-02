package com.betterstreamflix.providers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.database.AniWorldDatabase
import com.betterstreamflix.database.dao.TvShowDao
import com.betterstreamflix.extractors.Extractor
import com.betterstreamflix.models.Category
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Genre
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.People
import com.betterstreamflix.models.Season
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.models.Video
import com.betterstreamflix.providers.SerienStreamProvider.SerienStreamService
import com.betterstreamflix.utils.AniWorldUpdateTvShowWorker
import com.betterstreamflix.utils.DnsResolver
import com.betterstreamflix.utils.TmdbUtils
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.dnsoverhttps.DnsOverHttps
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object AniWorldProvider : Provider {

    private const val DEFAULT_DOMAIN = "aniworld.to"

    private fun currentBaseUrl(): String {
        val domain = UserPreferences.aniworldDomain
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        return "https://$domain/"
    }

    override val baseUrl: String
        get() = currentBaseUrl()

    override val name = "AniWorld"
    override val logo = "${currentBaseUrl()}public/img/facebook.jpg"
    override val language = "de"

    @Volatile
    private var serviceInstance: Service? = null
    @Volatile
    private var serviceBaseUrl: String? = null

    private val service: Service
        get() {
            val currentBase = currentBaseUrl()
            val synced = serviceInstance
            if (synced != null && serviceBaseUrl == currentBase) {
                return synced
            }
            return Service.build(currentBase).also {
                serviceInstance = it
                serviceBaseUrl = currentBase
            }
        }

    private var tvShowDao: TvShowDao? = null
    private var isWorkerScheduled = false
    private lateinit var appContext: Context

    private var preloadJob: Job? = null
    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cacheLock = Any()

    fun initialize(context: Context) {
        if (AniWorldProvider.tvShowDao == null) {
            AniWorldProvider.tvShowDao = AniWorldDatabase.getInstance(context).tvShowDao()

            this.appContext = context.applicationContext

        }
        if (!AniWorldProvider.isWorkerScheduled) {
            scheduleUpdateWorker(context)
            AniWorldProvider.isWorkerScheduled = true
        }
    }


    private fun scheduleUpdateWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<AniWorldUpdateTvShowWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "AniWorldUpdateTvShowWorker",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun getDao(): TvShowDao {
        return tvShowDao ?: throw IllegalStateException("AniWorldProvider not initialized")
    }

    override suspend fun getHome(): List<Category> {
        preloadSeriesAlphabetAsync()
        val document = service.getHome()
        val base = currentBaseUrl()

        val categories = mutableListOf<Category>()

        categories.add(
            Category(
                name = "Beliebt bei AniWorld",
                list = document.select("div.container > div:nth-child(7) > div.previews div.coverListItem")
                    .map {
                        TvShow(
                            id = it.selectFirst("a")
                                ?.attr("href")?.substringAfter("/anime/stream/")
                                ?: "",
                            title = it.selectFirst("a h3")
                                ?.text()
                                ?: "",
                            poster = it.selectFirst("img")
                                ?.attr("data-src")?.let { src -> base + src },
                        )
                    }
            )
        )

        categories.add(
            Category(
                name = "Neue Animes",
                list = document.select("div.container > div:nth-child(11) > div.previews div.coverListItem")
                    .map {
                        TvShow(
                            id = it.selectFirst("a")
                                ?.attr("href")?.substringAfter("/anime/stream/")
                                ?: "",
                            title = it.selectFirst("a h3")
                                ?.text()
                                ?: "",
                            poster = it.selectFirst("img")
                                ?.attr("data-src")?.let { src -> base + src },
                        )
                    }
            )
        )

        categories.add(
            Category(
                name = "Derzeit beliebte Animes",
                list = document.select("div.container > div:nth-child(16) > div.previews div.coverListItem")
                    .map {
                        TvShow(
                            id = it.selectFirst("a")
                                ?.attr("href")?.substringAfter("/anime/stream/")
                                ?: "",
                            title = it.selectFirst("a h3")
                                ?.text()
                                ?: "",
                            poster = it.selectFirst("img")
                                ?.attr("data-src")?.let { src -> base + src },
                        )
                    }
            )
        )

        return categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isEmpty()) {
            val document = service.getGenres()

            val genres = document.select("#seriesContainer h3").map {
                Genre(
                    id = it.text().lowercase(Locale.getDefault()),
                    name = it.text(),
                )
            }

            return genres
        }

        val lowerQuery = query.trim().lowercase(Locale.getDefault())
        val limit = chunkSize
        val offset = (page - 1) * chunkSize
        val results = getDao().searchTvShows(lowerQuery, limit, offset)
        return results
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        throw Exception("Keine Filme verfügbar")
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val fromIndex = (page - 1) * chunkSize
        val toIndex = page * chunkSize

        if (!isSeriesCacheLoaded) {
            var cachedShows = emptyList<TvShow>()
            try {
                cachedShows = getDao().getAll().first()
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                // ignore for now
            }
            if (cachedShows.isNotEmpty()) {
                synchronized(cacheLock) {
                    seriesCache.clear()
                    seriesCache.addAll(cachedShows)
                    isSeriesCacheLoaded = true
                }
            } else {
                preloadSeriesAlphabet()
            }
        }
        preloadSeriesAlphabetAsync()
        synchronized(cacheLock) {
            if (fromIndex >= seriesCache.size) return emptyList()
            val actualToIndex = minOf(toIndex, seriesCache.size)
            return seriesCache.subList(fromIndex, actualToIndex).toList()
        }
    }

    override suspend fun getMovie(id: String): Movie {
        throw Exception("Keine Filme verfügbar")
    }

    override suspend fun getTvShow(id: String): TvShow {
        val document = service.getAnime(id)

        val tvShow = TvShow(
            id = id,
            title = document.selectFirst("h1 > span")
                ?.text()
                ?: "",
            overview = document.selectFirst("p.seri_des")
                ?.attr("data-full-description"),
            released = document.selectFirst("div.series-title > small > span:nth-child(1)")
                ?.text()
                ?: "",
            trailer = document.selectFirst("div[itemprop='trailer'] a")
                ?.attr("href"),
            poster = document.selectFirst("div.seriesCoverBox img")
                ?.attr("data-src")?.let { currentBaseUrl() + it },
            banner = document.selectFirst("#series > section > div.backdrop")
                ?.attr("style")
                ?.replace("background-image: url(/", "")?.replace(")", "")
                ?.let { currentBaseUrl() + it },


            seasons = document.select("#stream > ul:nth-child(1) > li")
                .filter { it.select("a").isNotEmpty() }
                .mapIndexed { index, it ->
                    val seasonText = it.selectFirst("a")?.text() ?: ""
                    val seasonNumber = when {
                        seasonText.contains("Filme", true) || seasonText.contains("Specials", true) -> 0
                        else -> Regex("""\d+""").find(seasonText)?.value?.toIntOrNull() ?: (index + 1)
                    }
                    Season(
                        id = it.selectFirst("a")
                            ?.attr("href")?.substringAfter("/anime/stream/")
                            ?: "",
                        number = seasonNumber,
                        title = it.selectFirst("a")?.attr("title") ?: seasonText,
                    )
                },
            genres = document.select(".genres li").map {
                Genre(
                    id = it.selectFirst("a")
                        ?.text()?.lowercase(Locale.getDefault())
                        ?: "",
                    name = it.selectFirst("a")
                        ?.text()
                        ?: "",
                )
            },
            directors = document.select(".cast li[itemprop='director']").map {
                People(
                    id = it.selectFirst("a")
                        ?.attr("href")?.substringAfter("/animes/")
                        ?: "",
                    name = it.selectFirst("span")
                        ?.text()
                        ?: ""
                )
            },
            cast = document.select(".cast li[itemprop='actor']").map {
                People(
                    id = it.selectFirst("a")
                        ?.attr("href")?.substringAfter("/animes/")
                        ?: "",
                    name = it.selectFirst("span")
                        ?.text()
                        ?: "",
                )
            },
        )

        val tmdbTvShow = TmdbUtils.getTvShow(tvShow.title, language = language)

        return tvShow.copy(
            overview = tmdbTvShow?.overview ?: tvShow.overview,
            rating = tmdbTvShow?.rating ?: tvShow.rating,
            trailer = tmdbTvShow?.trailer ?: tvShow.trailer,
            banner = tmdbTvShow?.banner ?: tvShow.banner,
            imdbId = tmdbTvShow?.imdbId,
            seasons = tvShow.seasons.map { season ->
                season.copy(
                    poster = tmdbTvShow?.seasons?.find { it.number == season.number }?.poster
                )
            },
            cast = tvShow.cast.map { person ->
                val tmdbPerson = tmdbTvShow?.cast?.find { it.name.equals(person.name, ignoreCase = true) }
                    ?: TmdbUtils.enrichPersonByName(person.name, language = language)
                person.copy(image = tmdbPerson?.image ?: person.image)
            },
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val parts = seasonId.split("/")
        if (parts.size < 2) return emptyList()
        val tvShowId = parts[0]
        val season = parts[1]

        val document = service.getSeason(tvShowId, season)

        val tmdbTvShow = TmdbUtils.getTvShow(document.selectFirst("h1 > span")?.text() ?: "", language = language)
        val seasonNumber = when {
            season.contains("Filme", true) || season.contains("Specials", true) -> 0
            else -> Regex("""\d+""").find(season)?.value?.toIntOrNull() ?: 1
        }
        val tmdbEpisodes = tmdbTvShow?.let { TmdbUtils.getEpisodesBySeason(it.id, seasonNumber, language = language) } ?: emptyList()

        val episodes = document.select("tbody tr").map {
            val epNumber = it.selectFirst("meta")?.attr("content")?.toIntOrNull() ?: 0
            val tmdbEp = tmdbEpisodes.find { it.number == epNumber }
            
            Episode(
                id = it.selectFirst("a")
                    ?.attr("href")?.substringAfter("/anime/stream/")
                    ?: "",
                number = epNumber,
                title = tmdbEp?.title ?: it.selectFirst("strong")?.text(),
                poster = tmdbEp?.poster,
                overview = tmdbEp?.overview
            )
        }

        return episodes
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        if (page > 1) return Genre(id, "")

        val document = service.getGenre(id, page)

        val genre = Genre(
            id = id,
            name = document.selectFirst("h1")
                ?.text()?.substringBefore(" Animes")
                ?: "",

            shows = document.select(".seriesListContainer > div").map {
                TvShow(
                    id = it.selectFirst("a")
                        ?.attr("href")?.substringAfter("/anime/stream/")
                        ?: "",
                    title = it.selectFirst("h3")
                        ?.text()
                        ?: "",
                    poster = it.selectFirst("img")
                        ?.attr("data-src")?.let { src -> currentBaseUrl() + src },
                )
            }
        )

        return genre
    }

    override suspend fun getPeople(id: String, page: Int): People {
        if (page > 1) return People(id, "")

        val document = service.getPeople(id)
        val peopleName = document.selectFirst("h1 strong")?.text() ?: ""
        // AniWorld's own people pages have no bio/photo; enrich from TMDB (when enabled) instead
        // of leaving the profile blank, same as SerienStreamProvider.
        val tmdbPerson = TmdbUtils.enrichPersonByName(peopleName, language = language)

        val people = People(
            id = id,
            name = peopleName,
            image = tmdbPerson?.image,
            biography = tmdbPerson?.biography,
            placeOfBirth = tmdbPerson?.placeOfBirth,
            birthday = tmdbPerson?.birthday?.format("yyyy-MM-dd"),
            deathday = tmdbPerson?.deathday?.format("yyyy-MM-dd"),

            filmography = document.select(".seriesListContainer > div").map {
                TvShow(
                    id = it.selectFirst("a")
                        ?.attr("href")?.substringAfter("/anime/stream/")
                        ?: "",
                    title = it.selectFirst("h3")
                        ?.text() ?: "",
                    poster = it.selectFirst("img")
                        ?.attr("data-src")?.let { src -> currentBaseUrl() + src },
                )
            }
        )

        return people
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val parts = id.split("/")
        if (parts.size < 3) return emptyList()
        val tvShowId = parts[0]
        val seasonId = parts[1]
        val episodeId = parts[2]

        val document = service.getEpisode(tvShowId, seasonId, episodeId)

        val servers = document.select("div.hosterSiteVideo > ul > li").mapNotNull {
            val redirectUrl = it.selectFirst("a")
                ?.attr("href")?.let { href -> currentBaseUrl() + href }
                ?: return@mapNotNull null

            val name = it.selectFirst("h4")
                ?.text()?.let { name ->
                    name + when (it.attr("data-lang-key")) {
                        "1" -> " - DUB"
                        "2" -> " - SUB English"
                        "3" -> " - SUB"
                        else -> ""
                    }
                }
                ?: ""

            val audioVariant = when (it.attr("data-lang-key")) {
                "1" -> Video.AudioVariant.DUB
                "2", "3" -> Video.AudioVariant.SUB
                else -> Video.AudioVariant.UNKNOWN
            }

            Video.Server(
                id = name,
                name = name,
                src = redirectUrl,
                audioVariant = audioVariant,
            )
        }

        return servers
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val response = service.getRedirectLink(server.src)
            .let { response -> response.raw() as okhttp3.Response }
        val videoUrl = response.request.url

        val link = when (server.name) {
            "VOE" -> "https://voe.sx${videoUrl.encodedPath}"
            else -> videoUrl.toString()
        }

        return Extractor.extract(link)
    }


    private val seriesCache = mutableListOf<TvShow>()
    private const val chunkSize = 25
    private var isSeriesCacheLoaded = false

    private fun preloadSeriesAlphabetAsync() {
        if (preloadJob?.isActive == true) return
        preloadJob = providerScope.launch {
            runCatching { preloadSeriesAlphabet() }
        }
    }

    private suspend fun preloadSeriesAlphabet() {
        val document = service.getAnimesAlphabet()
        val elements = document.select(".genre > ul > li")

        val loadedShows = elements.map {
            TvShow(
                id = it.selectFirst("a[data-alternative-title]")
                    ?.attr("href")?.substringAfter("/anime/stream/")
                    ?: "",
                title = it.selectFirst("a[data-alternative-title]")
                    ?.text()
                    ?: "",
                overview = "",
            )
        }
        val dao = getDao()
        val existingIds = dao.getAllIds()
        val newShows = loadedShows.filter { it.id !in existingIds }

        if (newShows.isNotEmpty()) {
            dao.insertAll(newShows)
        }
        val allShows = dao.getAll().first()
        synchronized(cacheLock) {
            seriesCache.clear()
            seriesCache.addAll(allShows)
            isSeriesCacheLoaded = true
        }

        scheduleUpdateWorker(appContext)
    }

    fun invalidateCache() {
        synchronized(cacheLock) {
            seriesCache.clear()
            isSeriesCacheLoaded = false
        }
    }

    fun getSeriesChunk(pageIndex: Int): List<TvShow> {
        val fromIndex = pageIndex * chunkSize
        synchronized(cacheLock) {
            if (fromIndex >= seriesCache.size) return emptyList()
            val toIndex = minOf(fromIndex + chunkSize, seriesCache.size)
            return seriesCache.subList(fromIndex, toIndex).toList()
        }
    }

    fun getTotalPages(): Int {
        synchronized(cacheLock) {
            return (seriesCache.size + chunkSize - 1) / chunkSize
        }
    }


    private interface Service {
        companion object {
            private fun getOkHttpClient(): OkHttpClient {
                val appCache = Cache(File("cacheDir", "okhttpcache"), 10 * 1024 * 1024)
                val clientBuilder = OkHttpClient.Builder()
                    .cache(appCache)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                return clientBuilder
                    .dns(DnsResolver.doh)
                    .build()
            }

            private fun getUnsafeOkHttpClient(): OkHttpClient {
                try {
                    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                    tmf.init(null as java.security.KeyStore?)
                    val systemTrustManager = tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull() ?: return getUnsafeOkHttpClientFallback()
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, arrayOf(systemTrustManager), SecureRandom())
                    val sslSocketFactory = sslContext.socketFactory

                    val appCache = Cache(File("cacheDir", "okhttpcache"), 10 * 1024 * 1024)
                    val clientBuilder = OkHttpClient.Builder()
                        .cache(appCache)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .sslSocketFactory(sslSocketFactory, systemTrustManager)

                    return clientBuilder
                        .dns(DnsResolver.doh)
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    return getUnsafeOkHttpClientFallback()
                }
            }

            private fun getUnsafeOkHttpClientFallback(): OkHttpClient {
                return OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .dns(DnsResolver.doh)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
            }

            fun build(baseUrl: String): Service {
                val client = getOkHttpClient()
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()
                return retrofit.create(Service::class.java)
            }

            fun buildUnsafe(baseUrl: String): Service {
                val client = getUnsafeOkHttpClient()
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
        suspend fun getHome(): Document

        @POST
        @FormUrlEncoded
        suspend fun search(@Url url: String, @Field("keyword") query: String): List<SearchItem>

        @GET("animes-genres")
        suspend fun getGenres(): Document

        @GET("animes-alphabet")
        suspend fun getAnimesAlphabet(): Document

        @GET("anime/stream/{id}")
        suspend fun getAnime(@Path("id") id: String): Document

        @GET("anime/stream/{tvShowId}/{seasonId}")
        suspend fun getSeason(
            @Path("tvShowId") tvShowId: String,
            @Path("seasonId") seasonId: String,
        ): Document

        @GET("genre/{id}/{page}")
        suspend fun getGenre(
            @Path("id") id: String,
            @Path("page") page: Int,
        ): Document

        @GET("animes/{id}")
        suspend fun getPeople(@Path("id", encoded = true) id: String): Document

        @GET("anime/stream/{tvShowId}/{seasonId}/{episodeId}")
        suspend fun getEpisode(
            @Path("tvShowId") tvShowId: String,
            @Path("seasonId") seasonId: String,
            @Path("episodeId") episodeId: String,
        ): Document

        @GET
        @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        suspend fun getRedirectLink(@Url url: String): Response<ResponseBody>


        data class SearchItem(
            val title: String,
            val description: String,
            val link: String,
        )
    }
}
