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
import com.streamflixreborn.streamflix.models.Show
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

object RidomoviesProvider : Provider {

    const val URL = "https://ridomovies.su/"
    override val baseUrl = URL
    override val name = "Ridomovies"
    override val logo = "$URL/uploads/logos/hero_logo-1-1769040020-ab537326.png"
    override val language = "en"

    private val service = Service.build()
    private var currentSlug: String? = null

    private fun fixUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return "${URL.trimEnd('/')}/${path.trimStart('/')}"
    }

    override suspend fun getHome(): List<Category> {
        val document = service.getHome()
        val tvResponse = service.getLatestSeries(1)

        val categories = mutableListOf<Category>()

        categories.add(
            Category(
                name = Category.FEATURED,
                list = document.select("div.highlight-card").mapNotNull {
                    val href = it.selectFirst("a")?.attr("href") ?: ""
                    val id = href.trimEnd('/').substringAfterLast("/")
                    if (id.isEmpty()) return@mapNotNull null
                    val title = it.selectFirst("h2, h3")?.text() ?: ""
                    val overview = it.selectFirst("p.highlight-desc")?.text()
                    val banner = fixUrl(it.selectFirst("img")?.attr("src"))

                    if (href.contains("/movie/")) {
                        Movie(
                            id = id,
                            title = title,
                            overview = overview,
                            banner = banner,
                        )
                    } else if (href.contains("/tv/")) {
                        TvShow(
                            id = id,
                            title = title,
                            overview = overview,
                            banner = banner,
                        )
                    } else {
                        null
                    }
                }
            )
        )

        categories.add(
            Category(
                name = "Latest Movies",
                list = document.select("div.movie-card").mapNotNull {
                    val href = it.selectFirst("a")?.attr("href") ?: ""
                    if (!href.contains("/movie/")) return@mapNotNull null
                    Movie(
                        id = href.trimEnd('/').substringAfterLast("/"),
                        title = it.selectFirst(".movie-title")?.text() ?: "",
                        released = it.selectFirst(".movie-year")?.text(),
                        quality = it.selectFirst(".badge-quality")?.text()?.takeIf { q -> q.isNotBlank() },
                        poster = fixUrl(it.selectFirst("img")?.attr("src")),
                    )
                }
            )
        )

        categories.add(
            Category(
                name = "Latest TV Series",
                list = tvResponse.series.map {
                    TvShow(
                        id = it.slug,
                        title = it.title,
                        released = it.releaseDate?.substringBefore("-"),
                        quality = it.quality,
                        poster = fixUrl(it.posterPath),
                    )
                }
            )
        )

        return categories

    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isEmpty()) {
            val document = service.getHome()
            val genres = document.select(".dropdown-grid.genres-grid a, .mobile-accordion-content a.mobile-accordion-link").mapNotNull { a ->
                val href = a.attr("href")
                if (!href.contains("/genre/")) return@mapNotNull null
                val slug = href.substringAfter("/genre/").substringBefore("/")
                val name = a.text()
                if (slug.isEmpty()) return@mapNotNull null
                Genre(id = slug, name = name)
            }.distinctBy { it.id }

            return genres
        }

        val response = service.search(query, page)

        val results = response.data.mapNotNull {
            val slug = it.slug ?: it.slugEn ?: return@mapNotNull null
            when (it.type) {
                "movie" -> Movie(
                    id = slug,
                    title = it.title,
                    released = it.releaseDate?.substringBefore("-"),
                    quality = it.quality,
                    poster = fixUrl(it.posterPath),
                )
                "tv" -> TvShow(
                    id = slug,
                    title = it.title,
                    released = it.releaseDate?.substringBefore("-"),
                    quality = it.quality,
                    poster = fixUrl(it.posterPath),
                )
                else -> null
            }
        }

        return results
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val response = service.getLatestMovies(page)

        val movies = response.movies.map {
            Movie(
                id = it.slug,
                title = it.title,
                released = it.releaseDate?.substringBefore("-"),
                quality = it.quality,
                poster = fixUrl(it.posterPath),
            )
        }

        return movies
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val response = service.getLatestSeries(page)

        val tvShows = response.series.map {
            TvShow(
                id = it.slug,
                title = it.title,
                released = it.releaseDate?.substringBefore("-"),
                quality = it.quality,
                poster = fixUrl(it.posterPath),
            )
        }

        return tvShows
    }

    override suspend fun getMovie(id: String): Movie {
        val document = service.getMovie(id)
        val finalId = currentSlug ?: id

        val h1Text = document.selectFirst("h1")?.text() ?: ""
        val title = h1Text.substringBeforeLast("(").trim()
        val year = if ("(" in h1Text) h1Text.substringAfterLast("(").trimEnd(')') else null

        val movie = Movie(
            id = finalId,
            title = title,
            overview = document.selectFirst(".movie-overview")
                ?.text(),
            released = year,
            runtime = document.select("span.meta-info")
                .find { it.selectFirst("strong")?.text()?.contains("Duration") == true }
                ?.ownText()?.let {
                    val hours = it.substringBefore("h").filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                    val minutes = it.substringAfter("h").substringBefore("m").filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                    if (hours * 60 + minutes != 0) hours * 60 + minutes else null
                },
            rating = document.selectFirst(".imdb-score")
                ?.text()?.toDoubleOrNull(),
            poster = fixUrl(document.selectFirst("img.movie-poster-img")?.attr("src")),

            genres = document.select(".genre-links a").map {
                Genre(
                    id = it.attr("href").split("/").getOrNull(2) ?: "",
                    name = it.text(),
                )
            },
            cast = document.select(".cast-card").map {
                People(
                    id = "",
                    name = it.selectFirst(".cast-name")
                        ?.text()
                        ?: "",
                    image = fixUrl(it.selectFirst("img.cast-photo")?.attr("src")),
                )
            },
        )

        return movie
    }

    override suspend fun getTvShow(id: String): TvShow {
        val document = service.getTv(id)
        val finalId = currentSlug ?: id

        val h1Text = document.selectFirst("h1")?.text() ?: ""
        val title = h1Text.substringBeforeLast("(").trim()
        val year = if ("(" in h1Text) h1Text.substringAfterLast("(").trimEnd(')') else null

        val tvShow = TvShow(
            id = finalId,
            title = title,
            overview = document.selectFirst(".movie-overview")
                ?.text(),
            released = year,
            runtime = document.select("span.meta-info")
                .find { it.selectFirst("strong")?.text()?.contains("Duration") == true }
                ?.ownText()?.let {
                    val hours = it.substringBefore("h").filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                    val minutes = it.substringAfter("h").substringBefore("m").filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                    if (hours * 60 + minutes != 0) hours * 60 + minutes else null
                },
            rating = document.selectFirst(".imdb-score")
                ?.text()?.toDoubleOrNull(),
            poster = fixUrl(document.selectFirst("img[class*='poster']")?.attr("src")),

            seasons = document.select(".season-tabs button").mapNotNull { tab ->
                val seasonNum = tab.attr("data-season-number").toIntOrNull()
                    ?: return@mapNotNull null
                Season(
                    id = "$finalId/$seasonNum",
                    number = seasonNum,
                    title = "Season $seasonNum",
                )
            }.ifEmpty {
                listOf(Season(id = "$finalId/1", number = 1, title = "Season 1"))
            },
            genres = document.select(".genre-links a").map {
                Genre(
                    id = it.attr("href").split("/").getOrNull(2) ?: "",
                    name = it.text(),
                )
            },
            cast = document.select(".cast-card").map {
                People(
                    id = "",
                    name = it.selectFirst(".cast-name")
                        ?.text()
                        ?: "",
                    image = fixUrl(it.selectFirst("img.cast-photo")?.attr("src")),
                )
            },
        )

        return tvShow
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val parts = seasonId.split("/")
        val tvShowSlug = parts.getOrElse(0) { seasonId }
        val seasonNum = parts.getOrNull(1)?.toIntOrNull() ?: 1

        val document = service.getEpisodePage(tvShowSlug, seasonNum, 1)

        val episodes = document.select(".episodes-grid .episode-link").mapNotNull { ep ->
            val href = ep.attr("href")
            val epTitleRow = ep.selectFirst(".ep-title-row")?.text() ?: ""
            val epTitle = ep.selectFirst(".ep-name-row")?.text()
            val epDate = ep.selectFirst(".ep-date")?.text()

            val epNum = epTitleRow.substringAfterLast("Episode ").trim().toIntOrNull()
                ?: href.substringAfterLast("episode-").toIntOrNull()
                ?: return@mapNotNull null

            Episode(
                id = href.trimStart('/'),
                number = epNum,
                title = epTitle ?: "Episode $epNum",
                released = epDate,
            )
        }.distinctBy { it.number }

        return episodes
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            coroutineScope {
                val moviesDeferred = async {
                    try {
                        if (page > 1) service.getGenreMoviesPage(id, page) else service.getGenreMovies(id)
                    } catch (e: Exception) { null }
                }
                val tvDeferred = async {
                    try {
                        if (page > 1) service.getGenreSeriesPage(id, page) else service.getGenreSeries(id)
                    } catch (e: Exception) { null }
                }

                val movieItems = moviesDeferred.await()?.movies.orEmpty()
                val tvItems = tvDeferred.await()?.movies.orEmpty()

                val shows = mutableListOf<Show>()

                movieItems.forEach { item ->
                    shows.add(
                        Movie(
                            id = item.slug,
                            title = item.title,
                            released = item.releaseDate?.substringBefore("-"),
                            quality = item.quality,
                            poster = fixUrl(item.posterPath),
                        )
                    )
                }

                tvItems.forEach { item ->
                    shows.add(
                        TvShow(
                            id = item.slug,
                            title = item.title,
                            released = item.releaseDate?.substringBefore("-"),
                            quality = item.quality,
                            poster = fixUrl(item.posterPath),
                        )
                    )
                }

                Genre(id = id, name = id.replaceFirstChar { it.uppercase() }, shows = shows)
            }
        } catch (e: Exception) {
            Genre(id = id, name = id.replaceFirstChar { it.uppercase() }, shows = emptyList())
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        throw Exception("Not yet implemented")
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val pageUrl = when (videoType) {
            is Video.Type.Episode -> id.trimStart('/')
            is Video.Type.Movie -> "movie/$id"
        }

        val document = service.getPage(pageUrl)
        val servers = mutableListOf<Video.Server>()

        fun extractIframeSrc(rawHtml: String): String? {
            if (rawHtml.isBlank()) return null
            val iframe = Jsoup.parse(rawHtml).selectFirst("iframe")
            return iframe?.attr("src")
        }

        // #player-cover is always present (movies and episodes)
        document.selectFirst("#player-cover[data-embed]")?.let { el ->
            val src = extractIframeSrc(el.attr("data-embed"))
            if (!src.isNullOrBlank()) {
                servers.add(Video.Server(id = src, name = "Server 1", src = src))
            }
        }

        // Dropdown buttons appear only on multi-server movies — add extras deduplicating against player-cover
        document.select(".server-dropdown-item[data-server-embed]").forEachIndexed { idx, btn ->
            val src = extractIframeSrc(btn.attr("data-server-embed"))
            val label = btn.text().ifBlank { "Server ${idx + 1}" }
            if (!src.isNullOrBlank() && servers.none { it.src == src }) {
                servers.add(Video.Server(id = src, name = label, src = src))
            }
        }

        return servers
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.src)
    }


    private interface Service {

        companion object {
            fun build(): Service {
                val client = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .dns(DnsResolver.doh)
                    .addInterceptor { chain ->
                        val request = chain.request()
                        val response = chain.proceed(request)

                        val requestUrl = request.url.toString()
                        val responseUrl = response.request.url.toString()

                        if (requestUrl != responseUrl) {
                            currentSlug = responseUrl.substringBefore("?").substringBefore("#")
                                .trimEnd('/').substringAfterLast("/")
                        }

                        response
                    }
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .addHeader("Accept-Language", "en-US,en;q=0.5")
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                            .addHeader("Platform", "android")
                            .build()
                        chain.proceed(request)
                    }
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(URL)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(Service::class.java)
            }
        }


        @GET("home-rd1")
        suspend fun getHome(): Document

        @GET
        suspend fun getPage(@Url url: String): Document

        @GET("movie/{slug}")
        suspend fun getMovie(
            @Path("slug") slug: String,
        ): Document

        @GET("tv/{slug}")
        suspend fun getTv(
            @Path("slug") slug: String,
        ): Document

        @GET("tv/{slug}/season-{season}/episode-{episode}")
        suspend fun getEpisodePage(
            @Path("slug") slug: String,
            @Path("season") season: Int,
            @Path("episode") episode: Int,
        ): Document

        @GET("api/movies/latest")
        suspend fun getLatestMovies(
            @Query("page") page: Int = 1,
        ): MoviesResponse

        @GET("api/tv/latest")
        suspend fun getLatestSeries(
            @Query("page") page: Int = 1,
        ): SeriesResponse

        @GET("api/search")
        suspend fun search(
            @Query("q") q: String,
            @Query("page") page: Int = 1,
            @Query("lang") lang: String = "en",
            @Query("limit") limit: Int = 20,
        ): SearchResponse

        @Headers("X-Requested-With: XMLHttpRequest")
        @GET("genre/{genre}/movie")
        suspend fun getGenreMovies(
            @Path("genre") genre: String,
        ): MoviesResponse

        @Headers("X-Requested-With: XMLHttpRequest")
        @GET("genre/{genre}/movie/page-{page}")
        suspend fun getGenreMoviesPage(
            @Path("genre") genre: String,
            @Path("page") page: Int,
        ): MoviesResponse

        @Headers("X-Requested-With: XMLHttpRequest")
        @GET("genre/{genre}/tv")
        suspend fun getGenreSeries(
            @Path("genre") genre: String,
        ): MoviesResponse

        @Headers("X-Requested-With: XMLHttpRequest")
        @GET("genre/{genre}/tv/page-{page}")
        suspend fun getGenreSeriesPage(
            @Path("genre") genre: String,
            @Path("page") page: Int,
        ): MoviesResponse


        data class MoviesResponse(
            val success: Boolean,
            val movies: List<ShowItem>,
            val page: Int,
            val hasMore: Boolean,
        )

        data class SeriesResponse(
            val success: Boolean,
            val series: List<ShowItem>,
            val page: Int,
            val hasMore: Boolean,
        )

        data class ShowItem(
            val slug: String,
            val title: String,
            @SerializedName("poster_path") val posterPath: String?,
            @SerializedName("release_date") val releaseDate: String?,
            val quality: String?,
        )

        data class SearchResponse(
            val status: Boolean,
            val data: List<SearchItem>,
        )

        data class SearchItem(
            val id: Int?,
            val slug: String?,
            @SerializedName("slug_en") val slugEn: String?,
            val type: String?,
            val title: String,
            @SerializedName("poster_path") val posterPath: String?,
            @SerializedName("release_date") val releaseDate: String?,
            val quality: String?,
        )
    }
}