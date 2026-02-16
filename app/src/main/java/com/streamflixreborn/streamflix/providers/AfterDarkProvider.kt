package com.streamflixreborn.streamflix.providers

import android.util.Log
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.AfterDarkExtractor
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.Show
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.Response
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.TMDb3
import com.streamflixreborn.streamflix.utils.TMDb3.original
import com.streamflixreborn.streamflix.utils.TMDb3.w500
import com.streamflixreborn.streamflix.utils.UserPreferences
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Header
import kotlin.collections.map
import kotlin.collections.mapNotNull

object AfterDarkProvider : Provider, ProviderPortalUrl, ProviderConfigUrl {
    override val name = "AfterDark"

    override val defaultPortalUrl: String = "https://topsitestreaming.club/site/afterdark/"

    override val portalUrl: String = defaultPortalUrl
        get() {
            val cachePortalURL = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_PORTAL_URL)
            return cachePortalURL.ifEmpty { field }
        }

    override val defaultBaseUrl: String = "https://afterdark.mom/"
    override val baseUrl: String = defaultBaseUrl
        get() {
            val cacheURL = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL)
            return cacheURL.ifEmpty { field }
        }

    override val logo: String
        get() {
            val cacheLogo = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_LOGO)
            return cacheLogo.ifEmpty { baseUrl + "logo.png" }
        }
    override val language = "fr"
    override val changeUrlMutex = Mutex()

    private lateinit var service: Service
    private var serviceInitialized = false
    private val initializationMutex = Mutex()

    private lateinit var homeUrl: String
    private lateinit var movieUrl: String
    private lateinit var tvUrl: String
    private lateinit var searchUrl: String

    data class AfterDarkItem(
        val message: String,
        val tmdbId: Int,
        val title: String,

        val posterPath: String?,
        val backdropPath: String?,
        val overview: String,

        val kind: String, // "movie" or "show"

        val titleImage: String?,

        val season: Int?,
        val episode: Int?
    )

    data class AfterDarkResponse(
        val items: List<AfterDarkItem>
    )

    fun AfterDarkItem.toShow(): Show =
        when (kind) {
            "show" -> TvShow(
                id = tmdbId.toString(),
                title = buildString {
                    append(title)
                    if (season != null && episode != null) {
                        append(" - S${season}E${episode}")
                    } }
                ,
                banner = backdropPath?.original,
                poster = posterPath?.w500,
            )
            else -> Movie(
                id = tmdbId.toString(),
                title = title,
                banner = backdropPath?.original,
                poster = posterPath?.w500,
            )
        }

    fun AfterDarkResponse.toCategorie(name: String): Category {
    return Category(
                name = name,
                list = items
                    .map { it.toShow() }
            )
    }

    fun TMDb3.MultiItem.toAppItem(): AppAdapter.Item? =
        when (this) {
            is TMDb3.Movie -> Movie(
                id = id.toString(),
                title = title,
                overview = overview,
                released = releaseDate,
                rating = voteAverage.toDouble(),
                poster = posterPath?.w500,
                banner = backdropPath?.original,
            )

            is TMDb3.Tv -> TvShow(
                id = id.toString(),
                title = name,
                overview = overview,
                released = firstAirDate,
                rating = voteAverage.toDouble(),
                poster = posterPath?.w500,
                banner = backdropPath?.original,
            )

            else -> null
        }

    fun extractTitles(js: String): List<String> {
        val titleRegex = Regex("""title:"([^"]+)"""")

        return titleRegex.findAll(js)
            .map { it.groupValues[1] }
            .toList()
    }

    override suspend fun getHome(): List<Category> {
        initializeService()

        val categories = mutableListOf<Category>()

        try {
            val carousel = service.getCarousel("home")
            categories.add(carousel.toCategorie(Category.FEATURED))
        } catch (e: Exception) { }

        val homePage = service.loadPage(homeUrl)
        val html = homePage.body()

        if (! html.isNullOrEmpty()) {
            val queries = extractQueries(html)
            val titles = extractTitles(html)

            categories.addAll(
                queries.mapIndexedNotNull { idx, it ->
                    val params = parseParams(it.paramsRaw).toMutableMap()
                    params.putIfAbsent("language", "fr-FR")

                    val list =
                        if (it.endpoint.contains("discover/movie")) {
                            TMDb3.Discover.movie(params)
                        } else if (it.endpoint.contains("discover/tv")) {
                            TMDb3.Discover.tv(params)
                        } else if (it.endpoint.contains("tv/top_rated")) {
                            TMDb3.TvSeriesLists.topRated(params)
                        } else if (it.endpoint.contains("movie/top_rated")) {
                            TMDb3.MovieLists.topRated(params)
                        } else {
                            return@mapIndexedNotNull null
                        }

                    Category(
                        name = titles.getOrElse(idx + 2) { "Autres $idx" },
                        list = list.results.mapNotNull { it.toAppItem() }
                    )
                }
            )
        }

        try {
            val advisors = mutableListOf<Show>()
            advisors.add(service.getFeat("home").toShow())
            advisors.add(service.getFeat("movies").toShow())
            advisors.add(service.getFeat("shows").toShow())

            categories.add(
                2,
                Category(
                    name = "Les recommandations du mois",
                    list = advisors
                )
            )
        } catch (e: Exception) {

        }

        return categories
    }

    data class GenreCategory(
        val name: String,
        val slug: String,
        val movieIds: String,
        val tvIds: String
    )

    fun extractGenres(js: String): List<GenreCategory> {
        val genreRegex = Regex(
            """\{\s*name\s*:\s*"([^"]+)"\s*,\s*slug\s*:\s*"([^"]*)"\s*,\s*movieIds\s*:\s*\[([^\]]*)]\s*,\s*tvIds\s*:\s*\[([^\]]*)]\s*\}"""
        )
        return genreRegex.findAll(js).map { match ->

            val name = match.groupValues[1]
            val slug = match.groupValues[2]
            val movieIds = match.groupValues[3]
            val tvIds = match.groupValues[4]

            GenreCategory(
                name = name,
                slug = slug,
                movieIds = movieIds,
                tvIds = tvIds
            )
        }.toList()
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (page == 1) {
            if (query.isEmpty()) {
                initializeService()
                val document = service.loadPage(searchUrl).body() ?: ""

                return extractGenres(document).map {
                    Genre(
                        id = it.movieIds + "/" + it.tvIds + "/" + it.name,
                        name = it.name
                    )
                }
            }
        }

        return TMDb3.Search.multi(query, page = page, language = "fr-FR").results.mapNotNull { multi ->
            when (multi) {
                is TMDb3.Movie -> Movie(
                    id = multi.id.toString(),
                    title = multi.title,
                    overview = multi.overview,
                    released = multi.releaseDate,
                    rating = multi.voteAverage.toDouble(),
                    poster = multi.posterPath?.w500,
                    banner = multi.backdropPath?.original,
                )

                is TMDb3.Tv -> TvShow(
                    id = multi.id.toString(),
                    title = multi.name,
                    overview = multi.overview,
                    released = multi.firstAirDate,
                    rating = multi.voteAverage.toDouble(),
                    poster = multi.posterPath?.w500,
                    banner = multi.backdropPath?.original,
                )

                else -> null
            }
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        if (page > 1) return emptyList()

        initializeService()

        val movies = mutableListOf<Movie>()

        try {
            val carousel = service.getCarousel("movies")
            movies.addAll(carousel.items.map { it.toShow() as Movie })
        } catch (e: Exception) { }

        val moviePage = service.loadPage(movieUrl)
        val html = moviePage.body()

        if (! html.isNullOrEmpty()) {
            extractQueries(html)
                .mapIndexed { idx, it ->
                    val params = parseParams(it.paramsRaw).toMutableMap()
                    params.putIfAbsent("language", "fr-FR")
                    movies.addAll(TMDb3.Discover.movie(params).results.map { it.toAppItem() as Movie })
                }
        }

        return movies
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        if (page > 1) return emptyList()

        initializeService()

        val tvShows = mutableListOf<TvShow>()

        try {
            val carousel = service.getCarousel("shows")
            tvShows.addAll(carousel.items.map { it.toShow() as TvShow })
        } catch (e: Exception) { }

        val moviePage = service.loadPage(movieUrl)
        val html = moviePage.body()

        if (! html.isNullOrEmpty()) {
            extractQueries(html)
                .mapIndexed { idx, it ->
                    val params = parseParams(it.paramsRaw).toMutableMap()
                    params.putIfAbsent("language", "fr-FR")
                    tvShows.addAll(TMDb3.Discover.tv(params).results.map { it.toAppItem() as TvShow })
                }
        }

        return tvShows
    }

    override suspend fun getMovie(id: String): Movie {
        val movie = TMDb3.Movies.details(
            movieId = id.toInt(),
            appendToResponse = listOf(
                TMDb3.Params.AppendToResponse.Movie.CREDITS,
                TMDb3.Params.AppendToResponse.Movie.RECOMMENDATIONS,
                TMDb3.Params.AppendToResponse.Movie.VIDEOS,
                TMDb3.Params.AppendToResponse.Movie.EXTERNAL_IDS,
            ),
            language = "fr-FR"
        ).let { movie ->
            Movie(
                id = movie.id.toString(),
                title = movie.title,
                overview = movie.overview,
                released = movie.releaseDate,
                runtime = movie.runtime,
                trailer = movie.videos?.results
                    ?.sortedBy { it.publishedAt ?: "" }
                    ?.firstOrNull { it.site == TMDb3.Video.VideoSite.YOUTUBE }
                    ?.let { "https://www.youtube.com/watch?v=${it.key}" },
                rating = movie.voteAverage.toDouble(),
                poster = movie.posterPath?.original,
                banner = movie.backdropPath?.original,
                imdbId = movie.externalIds?.imdbId,

                genres = movie.genres.map { genre ->
                    Genre(
                        genre.id.toString(),
                        genre.name,
                    )
                },
                cast = movie.credits?.cast?.map { cast ->
                    People(
                        id = cast.id.toString(),
                        name = cast.name,
                        image = cast.profilePath?.w500,
                    )
                } ?: listOf(),
                recommendations = movie.recommendations?.results?.mapNotNull { multi ->
                    when (multi) {
                        is TMDb3.Movie -> Movie(
                            id = multi.id.toString(),
                            title = multi.title,
                            overview = multi.overview,
                            released = multi.releaseDate,
                            rating = multi.voteAverage.toDouble(),
                            poster = multi.posterPath?.w500,
                            banner = multi.backdropPath?.original,
                        )

                        is TMDb3.Tv -> TvShow(
                            id = multi.id.toString(),
                            title = multi.name,
                            overview = multi.overview,
                            released = multi.firstAirDate,
                            rating = multi.voteAverage.toDouble(),
                            poster = multi.posterPath?.w500,
                            banner = multi.backdropPath?.original,
                        )

                        else -> null
                    }
                } ?: listOf(),
            )
           }

        return movie
    }

    override suspend fun getTvShow(id: String): TvShow {
        val tvShow = TMDb3.TvSeries.details(
            seriesId = id.toInt(),
            appendToResponse = listOf(
                TMDb3.Params.AppendToResponse.Tv.CREDITS,
                TMDb3.Params.AppendToResponse.Tv.RECOMMENDATIONS,
                TMDb3.Params.AppendToResponse.Tv.VIDEOS,
                TMDb3.Params.AppendToResponse.Tv.EXTERNAL_IDS,
            ),
            language = "fr-FR"
        ).let { tv ->
            TvShow(
                id = tv.id.toString(),
                title = tv.name,
                overview = tv.overview,
                released = tv.firstAirDate,
                trailer = tv.videos?.results
                    ?.sortedBy { it.publishedAt ?: "" }
                    ?.firstOrNull { it.site == TMDb3.Video.VideoSite.YOUTUBE }
                    ?.let { "https://www.youtube.com/watch?v=${it.key}" },
                rating = tv.voteAverage.toDouble(),
                poster = tv.posterPath?.original,
                banner = tv.backdropPath?.original,
                imdbId = tv.externalIds?.imdbId,

                seasons = tv.seasons.map { season ->
                    Season(
                        id = "${tv.id}-${season.seasonNumber}",
                        number = season.seasonNumber,
                        title = season.name,
                        poster = season.posterPath?.w500,
                    )
                },
                genres = tv.genres.map { genre ->
                    Genre(
                        genre.id.toString(),
                        genre.name,
                    )
                },
                cast = tv.credits?.cast?.map { cast ->
                    People(
                        id = cast.id.toString(),
                        name = cast.name,
                        image = cast.profilePath?.w500,
                    )
                } ?: listOf(),
                recommendations = tv.recommendations?.results?.mapNotNull { multi ->
                    when (multi) {
                        is TMDb3.Movie -> Movie(
                            id = multi.id.toString(),
                            title = multi.title,
                            overview = multi.overview,
                            released = multi.releaseDate,
                            rating = multi.voteAverage.toDouble(),
                            poster = multi.posterPath?.w500,
                            banner = multi.backdropPath?.original,
                        )

                        is TMDb3.Tv -> TvShow(
                            id = multi.id.toString(),
                            title = multi.name,
                            overview = multi.overview,
                            released = multi.firstAirDate,
                            rating = multi.voteAverage.toDouble(),
                            poster = multi.posterPath?.w500,
                            banner = multi.backdropPath?.original,
                        )

                        else -> null
                    }
                } ?: listOf(),
            )
        }

        return tvShow
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val (tvShowId, seasonNumber) = seasonId.split("-")

        val episodes = TMDb3.TvSeasons.details(
            seriesId = tvShowId.toInt(),
            seasonNumber = seasonNumber.toInt(),
            language = "fr-FR"
        ).episodes?.map {
            Episode(
                id = it.id.toString(),
                number = it.episodeNumber,
                title = it.name ?: "",
                released = it.airDate,
                poster = it.stillPath?.w500,
            )
        } ?: listOf()

        return episodes
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        initializeService()

        val (movies, shows, title) = id.split("/")

        var list = mutableListOf<Show>()
        if (movies.isNotBlank()) {
            list.addAll(TMDb3.Discover.movie(withGenres = TMDb3.Params.WithBuilder(movies),
                includeAdult = false, language = "fr-FR", sortBy = TMDb3.Params.SortBy.Movie.POPULARITY_DESC,
                withWatchMonetizationTypes = TMDb3.Params.WithBuilder(TMDb3.Provider.WatchMonetizationType.FLATRATE), region = "FR",
                voteAverage =  TMDb3.Params.Range<Float>( gte = 6f ) , page = page,
                withoutGenres = TMDb3.Params.WithBuilder(TMDb3.Genre.Movie.ANIMATION)).results.map { it.toAppItem() as Show },
                )
        }
        if (shows.isNotBlank()) {
            list.addAll(TMDb3.Discover.tv(withGenres = TMDb3.Params.WithBuilder(shows),
                includeAdult = false, language = "fr-FR", sortBy = TMDb3.Params.SortBy.Tv.POPULARITY_DESC,
                withWatchMonetizationTypes = TMDb3.Params.WithBuilder(TMDb3.Provider.WatchMonetizationType.FLATRATE),
                voteAverage =  TMDb3.Params.Range<Float>( gte = 6f ) , page = page,
                withoutGenres = TMDb3.Params.WithBuilder(TMDb3.Genre.Tv.ANIMATION)).results.map { it.toAppItem() as Show },)
        }

        val genre = Genre(id=movies+"/"+shows,
                          name=title,
                          shows = list)
        return genre
    }

    override suspend fun getPeople(id: String, page: Int): People {
        val people = TMDb3.People.details(
            personId = id.toInt(),
            appendToResponse = listOfNotNull(
                if (page > 1) null else TMDb3.Params.AppendToResponse.Person.COMBINED_CREDITS,
            ),
            language = "fr-FR"
        ).let { person ->
            People(
                id = person.id.toString(),
                name = person.name,
                image = person.profilePath?.w500,
                biography = person.biography,
                placeOfBirth = person.placeOfBirth,
                birthday = person.birthday,
                deathday = person.deathday,

                filmography = person.combinedCredits?.cast
                    ?.mapNotNull { multi ->
                        when (multi) {
                            is TMDb3.Movie -> Movie(
                                id = multi.id.toString(),
                                title = multi.title,
                                overview = multi.overview,
                                released = multi.releaseDate,
                                rating = multi.voteAverage.toDouble(),
                                poster = multi.posterPath?.w500,
                                banner = multi.backdropPath?.original,
                            )

                            is TMDb3.Tv -> TvShow(
                                id = multi.id.toString(),
                                title = multi.name,
                                overview = multi.overview,
                                released = multi.firstAirDate,
                                rating = multi.voteAverage.toDouble(),
                                poster = multi.posterPath?.w500,
                                banner = multi.backdropPath?.original,
                            )

                            else -> null
                        }
                    }
                    ?.sortedBy {
                        when (it) {
                            is Movie -> it.released
                            is TvShow -> it.released
                        }
                    }
                    ?.reversed()
                    ?: listOf()
            )
        }

        return people
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return server.video!!
    }

    data class QueryCall(
        val endpoint: String,
        val paramsRaw: String?
    )

    private val queryRegex = Regex("""queryFn\s*:\s*\(\)\s*=>\s*e\("([^"]+)"(?:,\{([^}]*)\})?\)""")

    fun extractQueries(js: String): List<QueryCall> {
        return queryRegex.findAll(js).map {
            QueryCall(
                endpoint = it.groupValues[1],
                paramsRaw = it.groupValues.getOrNull(2)
            )
        }.toList()
    }


    fun parseParams(paramsRaw: String?): Map<String, String> {
        if (paramsRaw.isNullOrBlank()) return emptyMap()

        val paramRegex = Regex("""["]?([\w.]+)["]?:["]([^"]*)["]""")

        return paramRegex.findAll(paramsRaw)
            .associate {
                it.groupValues[1] to it.groupValues[2]
            }
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return AfterDarkExtractor(baseUrl).servers(videoType)
    }

    suspend fun getRealUrlFor(section: String): String {
        val regex = Regex("""<script\s+type="module".*?src="(/assets/index-[^"]+\.js)"""")
        val regexIndex = Regex(""""(assets/index-.*?\.js)"""")
        val regexSearch = Regex(""""(assets/search-.*?\.js)"""")

        val homedoc = service.loadPage(section)
        if (! homedoc.isSuccessful)
            return ""
        var html = homedoc.body()

        var match = regex.find(html ?: "")
        val url = match?.groupValues?.get(1)

        if (url.isNullOrEmpty())
            return ""

        val mainPage = service.loadPage(url)
        if (! mainPage.isSuccessful)
            return ""
        html = mainPage.body()

        if (!::searchUrl.isInitialized  || searchUrl.isBlank()) {
            /* update searchUrl if blank */
            match = regexSearch.find(html ?: "")
            searchUrl = match?.groupValues?.get(1) ?: ""
        }

        match = regexIndex.find(html ?: "")

        return match?.groupValues?.get(1) ?: ""
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
                    val document = addressService.getPortalHome()
                    var html = document.body()

                    val urlRegex = Regex("""slug:"afterdark".*?domain:"([^"]+)"""")
                    var matchUrl = urlRegex.find(html ?: "")
                    val newUrl = matchUrl?.groupValues?.get(1)

                    if (!newUrl.isNullOrEmpty()) {
                        val newUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
                        UserPreferences.setProviderCache(this,UserPreferences.PROVIDER_URL, newUrl)
                        UserPreferences.setProviderCache(
                            this,
                            UserPreferences.PROVIDER_LOGO,
                            newUrl + "logo.png"
                        )
                    }
                } catch (e: Exception) {
                    // In case of failure, we'll use the default URL
                    // No need to throw as we already have a fallback URL
                }
            }
            service = Service.build(baseUrl)

            try {
                homeUrl = getRealUrlFor("")
                movieUrl = getRealUrlFor("movies")
                tvUrl = getRealUrlFor("series")
                serviceInitialized = true
            } catch (e: Exception) {
                Log.e("ERROR", "CANNOT INITIALIZE")
            }
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
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .client(client)

                    .build()

                return addressRetrofit.create(Service::class.java)
            }


            fun build(baseUrl: String): Service {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET("{url}")
        suspend fun loadPage(
            @Path("url") url: String,
            @Header("user-agent") user_agent: String = "Mozilla"
        ): Response<String>

        @GET(".")
        suspend fun getPortalHome(
            @Header("user-agent") user_agent: String = "Mozilla"
        ): Response<String>

        @GET("api/carousel/{section}")
        suspend fun getCarousel(
            @Path("section") section: String,
            @Header("user-agent") user_agent: String = "Mozilla"
        ): AfterDarkResponse

        @GET("api/feat/{section}")
        suspend fun getFeat(
            @Path("section") section: String,
            @Header("user-agent") user_agent: String = "Mozilla"
        ): AfterDarkItem
    }
}