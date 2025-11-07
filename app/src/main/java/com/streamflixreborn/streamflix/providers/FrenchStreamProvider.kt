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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.streamflixreborn.streamflix.utils.DnsResolver
import retrofit2.http.Query
import kotlin.collections.map
import kotlin.collections.mapNotNull

object FrenchStreamProvider : Provider {

    private var URL = "http://fstream.info/"
    override val baseUrl = URL
    override val name = "FrenchStream"
    override val logo = "$URL/favicon-96x96.png"
    override val language = "fr"

    private lateinit var service: Service
    private var serviceInitialized = false
    private val initializationMutex = Mutex()

    override suspend fun getHome(): List<Category> {
        initializeService()
        val document = service.getHome()

        val categories = mutableListOf<Category>()

        categories.add(
            Category(
                name = "TOP Séries",
                list = document.select("div.pages.clearfix").getOrNull(1)?.select("div.short")?.map {
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
                            ?:"",
                    )
                } ?: emptyList(),
            )
        )

        categories.add(
            Category(
                name = "TOP Films",
                list = document.select("div.pages.clearfix").getOrNull(0)?.select("div.short")?.map {
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
                list = document.select("div.pages.clearfix").getOrNull(2)?.select("div.short")?.map {
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

        return categories
    }

    suspend fun ignoreSource(source: String): Boolean {
        if (arrayOf("VIDZY","Dood.Stream", "VOE", "Netu", "Filmoon").any { it.equals(source, true)})
            return true
        return false
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
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
            story = query,
        )

        val results = document.select("div#dle-content > div.short").mapNotNull {
            val href = it.selectFirst("a.short-poster")
                ?.attr("href")
                ?: ""
            val id = href.substringAfterLast("/")

            val title = it.selectFirst("div.short-title")
                ?.text()
                ?: ""
            var poster = it.selectFirst("img")
                         ?.attr("src")
                         ?: ""
            if (poster.contains("url=")) {
                poster = poster.substringAfter("url=")
            } else {
                poster = URL + poster
            }

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

    override suspend fun getMovie(id: String): Movie {
        initializeService()
        val document = service.getMovie(id)

        val movie = Movie(
            id = id,
            title = document.selectFirst("meta[property=og:title]")?.attr("content")
                ?: "",
            overview = document.selectFirst("meta[name=description]")?.attr("content")
                ?: "",
            released = document.selectFirst("span.release_date")?.selectFirst("a")
                ?.text()?.trim(),
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
                ?.attr("src")?: "",

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
                ?.select("a")?.mapNotNull {
                    People(
                        id = it.attr("href").substringBeforeLast("/").substringAfterLast("/"),
                        name = it.text(),
                    )
                }
                ?: emptyList(),
            cast = document.select("ul#s-list li")
                .find {
                    it.selectFirst("span")?.text()?.contains("Acteur") == true
                }
                ?.select("a")?.mapNotNull {
                    People(
                        id = it.attr("href").substringBeforeLast("/").substringAfterLast("/"),
                        name = it.text(),
                    )
                }
                ?: emptyList(),
        )

        return movie
    }

    override suspend fun getTvShow(id: String): TvShow {
        initializeService()
        val document = service.getTvShow(id)
        val tvShow = TvShow(
            id = id,
            title = document.selectFirst("meta[property=og:title]")
                ?.attr("content")
                ?: "",
            overview = document.selectFirst("meta[name=description]")
                ?.attr("content")
                ?: "",
            released = document.selectFirst("span.release_date")
                ?.selectFirst("a")
                ?.text()
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
                ?.attr("src")?: "",

            seasons = listOfNotNull(
                Season(
                    id = id+"/VOSTFR/VF-tab",
                    title = "Épisodes - VOSTFR",
                ).takeIf { document.selectFirst("div.VF-tab")?.parent()?.selectFirst("a.fstab")?.hasText() ?:false },
                Season(
                    id = id+"/VF/VOSTFR-tab",
                    title = "Épisodes - VF",
                ).takeIf { document.selectFirst("div.VOSTFR-tab")?.parent()?.selectFirst("a.fstab")?.hasText() ?:false },
            ),
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
                ?.select("a")?.mapNotNull {
                    People(
                        id = it.attr("href").substringBeforeLast("/").substringAfterLast("/"),
                        name = it.text(),
                    )
                }
                ?: emptyList(),
            cast = document.select("ul#s-list li")
                .find {
                    it.selectFirst("span")?.text()?.contains("Acteur") == true
                }
                ?.select("a")?.mapNotNull {
                    People(
                        id = it.attr("href").substringBeforeLast("/").substringAfterLast("/"),
                        name = it.text(),
                    )
                }
                ?: emptyList(),
        )

        return tvShow
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        initializeService()
        val (tvShowId, tvShowLang, divFilter) = seasonId.split("/")

        val document = service.getTvShow(tvShowId)
        val episodes = document.selectFirst("div."+divFilter)?.parent()?.select("div.elink>a")
            ?.map {
                val tvShowTitle = it.text().trim()
                val tvShowEpNumber = tvShowTitle.substringAfter("Episode ").toIntOrNull() ?: 0
                Episode(
                    id = tvShowId+"/"+tvShowLang+"/"+tvShowEpNumber,
                    number = tvShowEpNumber,
                    title = tvShowTitle,
                )
            }

        return episodes ?: emptyList()
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
            service.getPeople(id)
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

    suspend fun extractServersDefinition(document: Document): Map<String, Any>? {
        val scriptContent = document.select("script").joinToString("\n") { it.data() }

        val regex = Regex("""var\s+playerUrls\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(scriptContent) ?: return null

        val jsonPart = match.groupValues[1]

        val type = object : TypeToken<Map<String, Any>>() {}.type
        return Gson().fromJson(jsonPart, type)
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        initializeService()

        val servers = when (videoType) {
            is Video.Type.Episode -> {

                val (tvShowId, tvShowLang, tvShowNumber) = id.split("/")

                val document = service.getTvShow(tvShowId)

                document.select("div.fullsfeature > div.selink:has(span:contains("+tvShowLang+"))").
                                getOrNull(tvShowNumber.toInt()-1)
                                ?.select("li > a")
                                ?.filter {
                                    it.text().isNotBlank() && ignoreSource(it.text().trim() ) == false
                                }
                                ?.mapIndexed { index, it ->
                                    Video.Server(
                                        id = index.toString(),
                                        name = it?.text()
                                               ?: "",
                                        src = it.attr("href"),
                                    )
                                }
                                ?: emptyList()
            }

            is Video.Type.Movie -> {
                val document = service.getMovie(id)

                extractServersDefinition(document)?.map { (index, it) ->
                    if (ignoreSource(index.trim() ))
                        null
                    else {
                        if (it is Map<*, *>) {
                            val url = it["Default"] as? String
                                      ?: ""

                            if (url.isEmpty())
                                null
                            else Video.Server(
                                    id = index,
                                    name = index,
                                    src = url,
                                )
                        } else null
                    }
                }?.filterNotNull() ?: emptyList()
            }
        }

        return servers
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
    private suspend fun initializeService() {
        initializationMutex.withLock {
            if (serviceInitialized) return

            val addressService = Service.buildAddressFetcher()
            try {
                val document = addressService.getHome()

                val newUrl = document.select("div.current-url-container")
                    .selectFirst("a")
                    ?.attr("href")
                    ?.trim()
                    ?.replace("http://", "http://")

                if (!newUrl.isNullOrEmpty()) {
                    URL = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
                }
            } catch (e: Exception) {
                // In case of failure, we'll use the default URL
                // No need to throw as we already have a fallback URL
            }
            service = Service.build(URL)
            serviceInitialized = true
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
                    .baseUrl("http://fstream.info")

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
        suspend fun getHome(): Document

        @GET("index.php?do=search&subaction=search")
        suspend fun search(
            @Query("story") story: String,
        ): Document

        @GET("films/page/{page}/")
        suspend fun getMovies(@Path("page") page: Int): Document

        @GET("s-tv/page/{page}")
        suspend fun getTvShows(@Path("page") page: Int): Document

        @GET("films/{id}")
        suspend fun getMovie(@Path("id") id: String): Document

        @GET("s-tv/{id}")
        suspend fun getTvShow(@Path("id") id: String): Document

        @GET("film-en-streaming/{genre}/page/{page}")
        suspend fun getGenre(
            @Path("genre") genre: String,
            @Path("page") page: Int,
        ): Document

        @GET("xfsearch/actors/{id}/")
        suspend fun getPeople(
            @Path("id") id: String,
        ): Document
    }
}