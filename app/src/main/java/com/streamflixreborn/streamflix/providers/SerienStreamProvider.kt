package com.streamflixreborn.streamflix.providers

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.database.SerienStreamDatabase
import com.streamflixreborn.streamflix.database.dao.TvShowDao
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
import com.streamflixreborn.streamflix.utils.TmdbUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


object SerienStreamProvider : Provider {

    private val URL = Base64.decode(
        "aHR0cHM6Ly9z", Base64.NO_WRAP
    ).toString(Charsets.UTF_8) + Base64.decode(
        "LnRvLw==", Base64.NO_WRAP
    ).toString(Charsets.UTF_8)
    override val baseUrl = URL
    @SuppressLint("StaticFieldLeak")
    override val name = Base64.decode(
        "U2VyaWVuU3RyZWFt", Base64.NO_WRAP
    ).toString(Charsets.UTF_8)
    override val logo =
        "$URL/public/img/logo-sto-serienstream-sx-to-serien-online-streaming-vod.png"
    override val language = "de"
    private val service = SerienStreamService.build()


    private var tvShowDao: TvShowDao? = null
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        if (tvShowDao == null) {
            tvShowDao = SerienStreamDatabase.getInstance(context).tvShowDao()

            this.appContext = context.applicationContext

        }
    }

    private fun getDao(): TvShowDao {
        return tvShowDao ?: throw IllegalStateException("SerienStreamProvider not initialized")
    }


    private fun getTvShowIdFromLink(link: String): String {
        val linkWithoutStaticPrefix = link.removePrefix(URL).removePrefix("/").removePrefix("serie/")
        val linkWithSplitData = linkWithoutStaticPrefix.split("/")
        return linkWithSplitData[0]
    }

    private fun getSeasonIdFromLink(link: String): String {
        val linkWithoutStaticPrefix = link.removePrefix(URL).removePrefix("/").removePrefix("serie/")
        val linkWithSplitData = linkWithoutStaticPrefix.split("/")
        val justTvShowId = linkWithSplitData[0]
        val justTvShowSeason = linkWithSplitData[1]
        return "$justTvShowId/$justTvShowSeason"
    }

    private fun getEpisodeIdFromLink(link: String): String {
        val linkWithoutStaticPrefix = link.removePrefix(URL).removePrefix("/").removePrefix("serie/")
        val linkWithSplitData = linkWithoutStaticPrefix.split("/")
        val justTvShowId = linkWithSplitData[0]
        val justTvShowSeason = linkWithSplitData[1]
        val justTvShowEpisode = linkWithSplitData[2]
        return "$justTvShowId/$justTvShowSeason/$justTvShowEpisode"
    }

    override suspend fun getHome(): List<Category> {
        val document = service.getHome()
        val categories = mutableListOf<Category>()
        categories.add(
            Category(name = Category.FEATURED,
                list = document.select(".home-hero-slide").map {
                    TvShow(
                        id = getTvShowIdFromLink(it.selectFirst("a.home-hero-cta")?.attr("href") ?: ""),
                        title = it.selectFirst("h2.home-hero-title")?.text() ?: "",
                        banner = normalizeImageUrl(
                            it.select("picture.home-hero-bg img")
                                .flatMap { img -> img.attr("srcset").split(",") }
                                .find { url -> url.contains("hero-2x-desktop") }
                                ?.trim()?.split(" ")?.firstOrNull()

                                ?: it.select("picture.home-hero-bg source[type='image/webp']")
                                    .flatMap { s -> s.attr("srcset").split(",") }
                                    .find { url -> url.contains("hero-2x-desktop") }
                                    ?.trim()?.split(" ")?.firstOrNull()

                                ?: it.select("picture.home-hero-bg source[type='image/avif']")
                                    .flatMap { s -> s.attr("srcset").split(",") }
                                    .find { url -> url.contains("hero-2x-desktop") }
                                    ?.trim()?.split(" ")?.firstOrNull()
                        )

                    )
                })
        )
        categories.add(
            Category(name = "Angesagt",
                list = document.select(".trending-widget .swiper-slide").map {
                    TvShow(
                        id = getTvShowIdFromLink(it.selectFirst("h3.trend-title a")?.attr("href") ?: ""),
                        title = it.selectFirst("h3.trend-title a")?.text()?.trim() ?: "",
                        poster = normalizeImageUrl(it.extractPoster()))
                })
        )
        categories.add(
            Category(name = "Neu auf S.to",
                list = document.select("div:has(h4:contains(Neu auf S.to)) + div.row > div").map {
                    TvShow(
                        id = getTvShowIdFromLink(it.selectFirst("a")?.attr("href") ?: ""),
                        title = it.selectFirst("h6 a")?.text() ?: "",
                        poster = normalizeImageUrl(it.extractPoster()))
                })
        )
        document.select("#discover-blocks .col").forEach { column ->
            val categoryName = column.selectFirst("h4")?.text()?.trim() ?: ""
            if (categoryName.isNotEmpty()) {
                categories.add(
                    Category(name = categoryName,
                        list = column.select("li").map {
                            TvShow(
                                id = getTvShowIdFromLink(it.selectFirst("a")?.attr("href") ?: ""),
                                title = it.selectFirst("span.h6")?.text()?.trim() ?: "",
                                poster = normalizeImageUrl(it.extractPoster()))
                        })
                )
            }
        }
        categories.add(
            Category(name = "Derzeit beliebte Serien",
                list = document.select("div.carousel:contains(Derzeit beliebt) div.coverListItem").map {
                    TvShow(
                        id = getTvShowIdFromLink(it.selectFirst("a")?.attr("href") ?: ""),
                        title = it.selectFirst("a h3")?.text() ?: "",
                        poster = normalizeImageUrl(it.extractPoster())
                    )
                })
        )
        return categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isEmpty()) {
            val document = service.getSeriesListWithCategories()
            return document
                .select("div[data-group='genres'] .list-inline-item a")
                .map {
                    Genre(
                        id = it.attr("href").substringAfterLast("/"),
                        name = it.text().trim()
                    )
                }
        }
        val document = service.search(query, page)
        return document
            .select("div.search-results-list div.card.cover-card")
            .mapNotNull { card ->
                val link = card.selectFirst("a[href^=/serie/]")?.attr("href")
                    ?: return@mapNotNull null

                TvShow(
                    id = getTvShowIdFromLink(link),
                    title = card.selectFirst("h6.show-title")?.text().orEmpty(),
                    poster = normalizeImageUrl(card.extractPoster())
                )
            }
            .distinctBy { it.id }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        throw Exception("Keine Filme verfügbar")
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val document = service.getAllTvShows(page)
        return document
            .select("div.search-results-list div.card.cover-card")
            .mapNotNull { card ->
                val link = card.selectFirst("a[href^=/serie/]")?.attr("href")
                    ?: return@mapNotNull null
                TvShow(
                    id = getTvShowIdFromLink(link),
                    title = card.selectFirst("h6.show-title")?.text().orEmpty(),
                    poster = normalizeImageUrl(card.extractPoster())
                )
            }
            .distinctBy { it.id }
    }

    override suspend fun getMovie(id: String): Movie {
        throw Exception("Keine Filme verfügbar")
    }

    override suspend fun getTvShow(id: String): TvShow {
        val document = service.getTvShow(id)
        val title = document.selectFirst("h1")?.text()?.trim() ?: ""
        
        val tmdbTvShow = TmdbUtils.getTvShow(title, language = language)
        
        val localRating = if (tmdbTvShow?.rating == null) {
            val imdbTitleUrl = document.selectFirst("a[href*='imdb.com']")?.attr("href") ?: ""
            val imdbDocument = if (imdbTitleUrl.isNotEmpty()) try { service.getCustomUrl(imdbTitleUrl) } catch (e: Exception) { null } else null
            imdbDocument?.selectFirst("div[data-testid='hero-rating-bar__aggregate-rating__score'] span")
                ?.text()?.toDoubleOrNull() ?: document.selectFirst(".text-white-50:contains(Bewertungen)")?.text()?.split(" ")?.firstOrNull()?.toDoubleOrNull() ?: 0.0
        } else {
            0.0
        }
        
        val localCast = document.select(".series-group:contains(Besetzung) a").map {
            val actorName = it.text()
            val tmdbPerson = tmdbTvShow?.cast?.find { person -> person.name.equals(actorName, ignoreCase = true) }
            People(
                id = it.attr("href").removePrefix(URL).removePrefix("/"),
                name = actorName,
                image = tmdbPerson?.image
            )
        }
        
        return TvShow(id = id,
            title = title,
            overview = tmdbTvShow?.overview ?: document.selectFirst("span.description-text")?.text() ?: document.selectFirst("div.series-description p")?.text(),
            released = tmdbTvShow?.released?.let { "${it.get(java.util.Calendar.YEAR)}" } 
                ?: document.selectFirst("a.small.text-muted")?.text() ?: "",
            rating = tmdbTvShow?.rating ?: localRating,
            runtime = tmdbTvShow?.runtime,
            directors = document.select(".series-group:contains(Regisseur) a").map {
                People(
                    id = it.attr("href").removePrefix(URL).removePrefix("/"),
                    name = it.text()
                )
            },
            cast = localCast,
            genres = tmdbTvShow?.genres ?: document.select(".series-group:contains(Genre) a").map {
                Genre(
                    id = it.text().lowercase(Locale.getDefault()),
                    name = it.text()
                )
            },
            trailer = tmdbTvShow?.trailer ?: document.selectFirst("div[itemprop='trailer'] a")?.attr("href") ?: "",
            poster = tmdbTvShow?.poster ?: document.selectFirst("div.show-header-wrapper img")?.let { img -> img.attr("data-src").takeIf { it.isNotEmpty() } ?: img.attr("src") },
            banner = tmdbTvShow?.banner ?: document.selectFirst("div.backdrop-picture img")?.let { img -> img.attr("data-src").takeIf { it.isNotEmpty() } ?: img.attr("src") },
            seasons = document.select("#season-nav ul li a").map {
                val seasonText = it.text().trim()
                val seasonNumber = seasonText.toIntOrNull() ?: 0
                Season(
                    id = getSeasonIdFromLink(it.attr("href")),
                    number = seasonNumber,
                    title = if (seasonText == "Filme") "Filme" else "Staffel $seasonNumber",
                    poster = tmdbTvShow?.seasons?.find { s -> s.number == seasonNumber }?.poster
                )
            },
            imdbId = tmdbTvShow?.imdbId
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val linkWithSplitData = seasonId.split("/")
        val showName = linkWithSplitData[0]
        val seasonNumberStr = linkWithSplitData[1]
        val seasonNumber = Regex("""\d+""").find(seasonNumberStr)!!.value.toInt()

        val document = service.getTvShowEpisodes(showName, seasonNumberStr)
        
        // Get show title for TMDB lookup
        val title = (document.selectFirst("h1")?.text()?.trim() ?: "").split(" Staffel").firstOrNull()?.trim() ?: ""
        
        val tmdbTvShow = TmdbUtils.getTvShow(title, language = language)
        val tmdbEpisodes = tmdbTvShow?.let { 
            TmdbUtils.getEpisodesBySeason(it.id, seasonNumber, language = language) 
        } ?: emptyList()
        
        return document.select("tr.episode-row").map {
            val episodeNumber = it.selectFirst(".episode-number-cell")?.text()?.trim()?.toIntOrNull() ?: 0
            val tmdbEp = tmdbEpisodes.find { ep -> ep.number == episodeNumber }
            
            val episodeLink = it.attr("onclick")
                .substringAfter("window.location='")
                .substringBefore("'")

            Episode(
                id = getEpisodeIdFromLink(episodeLink),
                number = episodeNumber,
                title = tmdbEp?.title ?: it.selectFirst(".episode-title-ger")?.text() 
                    ?: it.selectFirst(".episode-title-eng")?.text() 
                    ?: "Episode $episodeNumber",
                poster = tmdbEp?.poster,
                overview = tmdbEp?.overview
            )
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {

        try {
            val shows = mutableListOf<TvShow>()
            val document = service.getGenre(id, page)
            document.select("div.row.g-3 > div").map {
                shows.add(
                    TvShow(
                        id = it.selectFirst("a")?.attr("href")
                            ?.let { it1 -> getTvShowIdFromLink(it1) } ?: "",
                        title = it.selectFirst("h6")?.text()?.trim() ?: "",
                        poster = it.selectFirst("img")?.let { img -> img.attr("data-src").takeIf { it.isNotEmpty() } ?: img.attr("src") }
                    )
                )
            }
            return Genre(id = id, name = id.replaceFirstChar { it.uppercase() }, shows = shows)
        } catch (e: Exception) {
            Log.e("SerienStreamProvider", "Error fetching genre $id page $page", e)
            return Genre(id = id, name = id, shows = emptyList())
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        if (page > 1) return People(id, "")
        val document = service.getPeople(id)
        return People(id = id,
            name = document.selectFirst("h1 strong")?.text() ?: "",
            filmography = document.select("div.row.g-3 > div").map {
                TvShow(
                    id = it.selectFirst("a")?.attr("href")?.let { it1 -> getTvShowIdFromLink(it1) } ?: "",
                    title = it.selectFirst("h6 a")?.text() ?: "",
                    poster = it.selectFirst("img")?.let { img -> img.attr("data-src").takeIf { it.isNotEmpty() } ?: img.attr("src") }
                )
            })
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val servers = mutableListOf<Video.Server>()
        val linkWithSplitData = id.split("/")
        val showName = linkWithSplitData[0]
        val seasonNumber = linkWithSplitData[1]
        val episodeNumber = linkWithSplitData[2]
        val document = service.getTvShowEpisodeServers(showName, seasonNumber, episodeNumber)

        val elements = document.select("button.link-box")
        for (element in elements) {
            val serverName = element.attr("data-provider-name")
            val language = element.attr("data-language-label")
            val href = element.attr("data-play-url")
            
            if (href.isEmpty()) continue

            try {
                val redirectUrl = URL + href.removePrefix("/")

                val serverAfterRedirect = try {
                    service.getRedirectLink(redirectUrl)
                } catch (exception: Exception) {
                    val unsafeOkHttpClient = SerienStreamService.buildUnsafe()
                    unsafeOkHttpClient.getRedirectLink(redirectUrl)
                }
                val videoUrl = (serverAfterRedirect.raw() as okhttp3.Response).request.url
                val videoUrlString = videoUrl.toString()
                
                servers.add(
                    Video.Server(
                        id = videoUrlString,
                        name = "$serverName ($language)"
                    )
                )
            } catch (e: Exception) {
                Log.e("SerienStreamProvider", "Failed to process server '$serverName' with URL '$href'")
            }
        }
        return servers

    }

    override suspend fun getVideo(server: Video.Server): Video {
        val link = server.id
        return Extractor.extract(link)
    }

    interface SerienStreamService {

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
                    val trustAllCerts = arrayOf<TrustManager>(
                        object : X509TrustManager {
                            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                        }
                    )
                    val sslContext = SSLContext.getInstance("SSL")
                    sslContext.init(null, trustAllCerts, SecureRandom())
                    val sslSocketFactory = sslContext.socketFactory

                    val appCache = Cache(File("cacheDir", "okhttpcache"), 10 * 1024 * 1024)
                    val clientBuilder = OkHttpClient.Builder()
                        .cache(appCache)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                        .hostnameVerifier { _, _ -> true }

                    return clientBuilder
                        .dns(DnsResolver.doh)
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }

            fun build(): SerienStreamService {
                val client = getOkHttpClient()
                val retrofit = Retrofit.Builder()
                    .baseUrl(URL)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()
                return retrofit.create(SerienStreamService::class.java)
            }

            fun buildUnsafe(): SerienStreamService {
                val client = getUnsafeOkHttpClient()
                val retrofit = Retrofit.Builder()
                    .baseUrl(URL)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()
                return retrofit.create(SerienStreamService::class.java)
            }
        }


        @GET(".")
        suspend fun getHome(): Document

        @GET("suche?tab=genres")
        suspend fun getSeriesListWithCategories(): Document

        @GET("serien-alphabet")
        suspend fun getSeriesListAlphabet(): Document

        @GET("suche")
        suspend fun search(
            @Query("term") keyword: String,
            @Query("page") page: Int,
            @Query("tab") tab: String = "shows"
        ): Document
        @GET("suche")
        suspend fun getAllTvShows( @Query("page") page: Int,
                                   @Query("tab") tab: String = "shows"): Document

        @GET("genre/{genreName}")
        suspend fun getGenre(
            @Path("genreName") genreName: String, @Query("page") page: Int
        ): Document

        @GET("{peopleId}")
        suspend fun getPeople(@Path("peopleId", encoded = true) peopleId: String): Document

        @GET("serie/{tvShowName}")
        suspend fun getTvShow(@Path("tvShowName") tvShowName: String): Document

        @GET("serie/{tvShowName}/{seasonNumber}")
        suspend fun getTvShowEpisodes(
            @Path("tvShowName") showName: String, @Path("seasonNumber") seasonNumber: String
        ): Document

        @GET("serie/{tvShowName}/{seasonNumber}/{episodeNumber}")
        suspend fun getTvShowEpisodeServers(
            @Path("tvShowName") tvShowName: String,
            @Path("seasonNumber") seasonNumber: String,
            @Path("episodeNumber") episodeNumber: String
        ): Document

        @GET
        @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        suspend fun getCustomUrl(@Url url: String): Document

        @GET
        @Headers(
            "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
            "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language: en-US,en;q=0.5",
            "Connection: keep-alive"
        )
        suspend fun getRedirectLink(@Url url: String): Response<ResponseBody>
    }

    fun Element.extractPoster(): String {
        selectFirst("img[data-src]")?.attr("data-src")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        select("source[data-srcset]")
            .firstOrNull { it.attr("type") != "image/webp" }
            ?.attr("data-srcset")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.split(" ")
            ?.firstOrNull()
            ?.let { return it }
        select("source[data-srcset]")
            .firstOrNull { it.attr("type") != "image/avif" }
            ?.attr("data-srcset")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.split(" ")
            ?.firstOrNull()
            ?.let { return it }
        selectFirst("img[src]")?.attr("src")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return ""
    }

    fun normalizeImageUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return if (url.startsWith("http")) url
        else "https://s.to$url"
    }


}