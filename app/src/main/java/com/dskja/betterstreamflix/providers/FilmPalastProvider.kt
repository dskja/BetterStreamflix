package com.dskja.betterstreamflix.providers

import android.util.Log
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.extractors.Extractor
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Genre
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.People
import com.dskja.betterstreamflix.models.Season
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.TmdbUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Url
import java.security.cert.CertPathValidatorException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

object FilmPalastProvider : Provider {

    private const val TAG = "FilmPalastProvider"
    private val BASE_URL = "https://filmpalast.to/"
    override val baseUrl = BASE_URL
    override val name = "Filmpalast"
    override val logo = "$BASE_URL/themes/downloadarchive/images/logo.png"
    override val language = "de"

    @Volatile
    private var service = FilmpalastService.build()
    @Volatile
    private var usingUnsafeSsl = false

    private fun isSslFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            when (current) {
                is SSLHandshakeException,
                is SSLPeerUnverifiedException,
                is CertPathValidatorException -> return true
            }
            val message = current.message.orEmpty()
            if (
                message.contains("certificate", ignoreCase = true) ||
                message.contains("CertPath", ignoreCase = true) ||
                message.contains("SSL", ignoreCase = true) ||
                message.contains("pretending to be", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private suspend fun <T> withSslFallback(block: suspend (FilmpalastService) -> T): T {
        return try {
            block(service)
        } catch (e: Exception) {
            if (usingUnsafeSsl || !isSslFailure(e)) throw e
            Log.w(TAG, "SSL failure talking to filmpalast.to; retrying with permissive TLS", e)
            service = FilmpalastService.buildUnsafe()
            usingUnsafeSsl = true
            block(service)
        }
    }

    override suspend fun getHome(): List<Category> {
        val document = withSslFallback { it.getHome() }
        val featured = coroutineScope {
            document.select("div.headerslider ul#sliderDla li").map { li ->
                async {
                    val title = li.select("span.title.rb").text()
                    val href = li.select("a.moviSliderPlay").attr("href")
                    val id = href.substringAfterLast("/")
                    val posterSrc = li.select("a img").attr("src")
                    val fullPosterUrl = if (posterSrc.startsWith("/")) {
                        "https://filmpalast.to$posterSrc"
                    } else {
                        posterSrc
                    }
                    val overview = li.select("div.moviedescription").text()

                    val releaseYearText = li.select("span.releasedate b").text()

                    val imdbRatingText =
                        li.select("span.views b").lastOrNull()?.text()?.split("/")?.get(1)?.trim()
                    val rating = imdbRatingText?.toDoubleOrNull() ?: 0.0

                    val tmdbMovie = TmdbUtils.getMovie(title, language = language)

                    Movie(
                        id = id,
                        title = title,
                        overview = overview,
                        released = releaseYearText,
                        rating = rating,
                        poster = fullPosterUrl,
                        banner = tmdbMovie?.banner
                    )
                }
            }.awaitAll()
        }


        val main_content = document.select("div#content article").map { article ->
            val href = article.selectFirst("h2 a")?.attr("href") ?: ""
            val title = article.selectFirst("h2 a")?.text() ?: ""
            val posterSrc = article.selectFirst("a img")?.attr("src") ?: ""

            val fullPosterUrl = if (posterSrc.startsWith("/")) {
                "https://filmpalast.to$posterSrc"
            } else {
                posterSrc
            }

            val info = article.select("*").toInfo()

            Movie(
                id = href.substringAfterLast("/"),
                title = title,
                released = info.released,
                quality = info.quality,
                rating = info.rating ?: 0.0,
                poster = fullPosterUrl
            )
        }
        val tvShowsDocument = withSslFallback { it.getTvShowsHome() }
        val tvShows = tvShowsDocument.select("div#content article").map { article ->
            val href = article.selectFirst("h2 a")?.attr("href") ?: ""
            val title = article.selectFirst("h2 a")?.text() ?: ""
            val posterSrc = article.selectFirst("a img")?.attr("src") ?: ""

            val fullPosterUrl = if (posterSrc.startsWith("/")) {
                "https://filmpalast.to$posterSrc"
            } else {
                posterSrc
            }

            val info = article.select("*").toInfo()                
                TvShow(
                id = href.substringAfterLast("/"),
                    title = title,
                    released = info.released,
                    quality = info.quality,
                    rating = info.rating ?: 0.0,
                    poster = fullPosterUrl
                )
        }

        return listOf(
            Category(name = "Featured", list = featured),
            Category(name = "Filme", list = main_content),
            Category(name = "Serien", list = tvShows)
        )
    }


    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isEmpty()) {
            val document = withSslFallback { it.getHome() }
            val genres = document.select("aside#sidebar section#genre ul li a").map { element ->
                val text = element.text()
                Genre(text, text)
            }
            return genres
        }
        var document = withSslFallback { it.searchNoPage(query) }

        if (page > 1){
            val paging = document.selectFirst("div#paging a.pageing.button-small.rb")
            if (paging != null){
                document = withSslFallback { it.search(query, page) }
            } else {
                return emptyList()
            }
        }

        val results = document.select("div#content article").map { article ->
            val href = article.selectFirst("h2 a")?.attr("href") ?: ""
            val title = article.selectFirst("h2 a")?.text() ?: ""
            val posterSrc = article.selectFirst("a img")?.attr("src") ?: ""

            val fullPosterUrl = if (posterSrc.startsWith("/")) {
                "https://filmpalast.to$posterSrc"
            } else {
                posterSrc
            }

            val info = article.select("*").toInfo()

            // Determine if it's a movie or TV show based on season/episode info in title
            val isTvShow = title.matches(Regex(".*S\\d+E\\d+.*"))
            
            if (isTvShow) {
                TvShow(
                    id = href.substringAfterLast("/"),
                    title = title,
                    released = info.released,
                    quality = info.quality,
                    rating = info.rating ?: 0.0,
                    poster = fullPosterUrl
                )
            } else {
            Movie(
                id = href.substringAfterLast("/"),
                title = title,
                released = info.released,
                quality = info.quality,
                rating = info.rating ?: 0.0,
                poster = fullPosterUrl
            )
            }
        }.distinctBy { 
            when (it) {
                is Movie -> it.id
                is TvShow -> it.id
            }
        }

        return results

    }



    override suspend fun getMovie(id: String): Movie {
        val relativeId = BASE_URL + "stream/" + id;
        val document = withSslFallback { it.getMoviePage(relativeId) }
        val title = document.selectFirst("h2")?.text() ?: ""
        val poster = document.selectFirst("img.cover2")?.attr("src")?.let {
            if (it.startsWith("http")) it else "${BASE_URL.removeSuffix("/")}$it"
        }
        val description = document.selectFirst("span[itemprop=description]")?.text()
        val rating = document.selectFirst("div#star-rate")?.attr("data-rating")?.toDoubleOrNull()
        val genres =
            document.select("ul#detail-content-list > li:has(p:matchesOwn(Kategorien, Genre)) a")
                .map { Genre(id = it.text().trim(), name = it.text().trim()) }
        val directors = document.select("ul#detail-content-list > li:has(p:matchesOwn(Regie)) a")
            .map { People(id = it.text().trim(), name = it.text().trim()) }
        val actors =
            document.select("ul#detail-content-list > li:has(p:matchesOwn(Schauspieler)) a")
                .map { People(id = it.text().trim(), name = it.text().trim()) }

        val tmdbMovie = TmdbUtils.getMovie(title, language = language)

        return Movie(
            id = id,
            title = title,
            poster = poster,
            banner = tmdbMovie?.banner,
            genres = tmdbMovie?.genres ?: genres,
            directors = directors,
            cast = actors.map { person ->
                val tmdbPerson = tmdbMovie?.cast?.find { it.name.equals(person.name, ignoreCase = true) }
                person.copy(image = tmdbPerson?.image)
            },
            rating = tmdbMovie?.rating ?: rating,
            overview = tmdbMovie?.overview ?: description,
            released = tmdbMovie?.released?.let { "${it.get(java.util.Calendar.YEAR)}" } ?: document.selectFirst("ul#detail-content-list > li:has(p:matchesOwn(Release)) a")?.text()?.trim(),
            runtime = tmdbMovie?.runtime,
            trailer = tmdbMovie?.trailer,
            imdbId = tmdbMovie?.imdbId
        )
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val relativeId = BASE_URL + "stream/" + id;
        val document = withSslFallback { it.getMoviePage(relativeId) }
        val servers = mutableListOf<Video.Server>()

        val serverBlocks = document.select("ul.currentStreamLinks")
        val keywords = listOf("bigwarp", "vinovo")
        for (block in serverBlocks) {
            val name = block.selectFirst("li.hostBg p.hostName")?.text()?.trim() ?: "Unbekannt"
            var linkElement = block.selectFirst("a[href]")
            var url = linkElement?.attr("href")?.trim()
            if (linkElement == null){
                linkElement = block.selectFirst("a[data-player-url]")
                url = linkElement?.attr("data-player-url")?.trim();
            }


            if (!url.isNullOrEmpty()) {
                val displayName = if (keywords.none { name.lowercase().contains(it) }) {
                    name
                } else {
                    "$name (VLC Only)"
                }
                servers.add(
                    Video.Server(
                        id = name.split(" ")[0],
                        name = displayName,
                        src = url
                    )
                )
            }
        }

        return servers
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val response = withSslFallback { it.getRedirectLink(server.src) }
            .let { response -> response.raw() as okhttp3.Response }


        val videoUrl = response.request.url
        val link = when (server.name) {
            "VOE" -> {
                val baseUrl = "https://voe.sx"
                val path = videoUrl.encodedPath.trimStart('/')
                "$baseUrl/e/$path?"
            }

            else -> videoUrl.toString()
        }
        return Extractor.extract(link, server)
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val document = withSslFallback { it.getMovies(page) }
        val movies = document.select("div#content article").map { article ->
            val href = article.selectFirst("h2 a")?.attr("href") ?: ""
            val title = article.selectFirst("h2 a")?.text() ?: ""
            val posterSrc = article.selectFirst("a img")?.attr("src") ?: ""

            val fullPosterUrl = if (posterSrc.startsWith("/")) {
                "https://filmpalast.to$posterSrc"
            } else {
                posterSrc
            }

            val info = article.select("*").toInfo()

            Movie(
                id = href.substringAfterLast("/"),
                title = title,
                released = info.released,
                quality = info.quality,
                rating = info.rating ?: 0.0,
                poster = fullPosterUrl
            )
        }
        return movies
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val document = withSslFallback { it.getTvShows(page) }
        val shows = document.select("div#content article").map { article ->
            val href = article.selectFirst("h2 a")?.attr("href") ?: ""
            val title = article.selectFirst("h2 a")?.text() ?: ""
            val posterSrc = article.selectFirst("a img")?.attr("src") ?: ""

            val fullPosterUrl = if (posterSrc.startsWith("/")) {
                "https://filmpalast.to$posterSrc"
            } else {
                posterSrc
            }

            val info = article.select("*").toInfo()
                
                TvShow(
                id = href.substringAfterLast("/"),
                    title = title,
                    released = info.released,
                    quality = info.quality,
                    rating = info.rating ?: 0.0,
                    poster = fullPosterUrl
                )
            }
        return shows
    }

    override suspend fun getTvShow(id: String): TvShow {
        val relativeId = BASE_URL + "stream/" + id
        val document = withSslFallback { it.getTvShow(relativeId) }
        val title = document.selectFirst("h2")?.text() ?: ""
        val poster = document.selectFirst("img.cover2")?.attr("src")?.let {
            if (it.startsWith("http")) it else "${BASE_URL.removeSuffix("/")}$it"
        }
        val description = document.selectFirst("span[itemprop=description]")?.text()
        val rating = document.selectFirst("div#star-rate")?.attr("data-rating")?.toDoubleOrNull()
        val genres = document.select("ul#detail-content-list > li:has(p:matchesOwn(Kategorien, Genre)) a")
            .map { Genre(id = it.text().trim(), name = it.text().trim()) }
        val directors = document.select("ul#detail-content-list > li:has(p:matchesOwn(Regie)) a")
            .map { People(id = it.text().trim(), name = it.text().trim()) }
        val actors = document.select("ul#detail-content-list > li:has(p:matchesOwn(Schauspieler)) a")
            .map { People(id = it.text().trim(), name = it.text().trim()) }

        // Parse seasons and episodes
        val seasons = mutableListOf<Season>()
        
        val cleanedTitle = title.replace(Regex("""\s+S\d+E\d+.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+S\d+.*""", RegexOption.IGNORE_CASE), "")
            .trim()
            
        val tmdbTvShow = TmdbUtils.getTvShow(cleanedTitle, language = language)

        document.select("div#staffelWrapper div.staffelWrapperLoop").forEachIndexed { seasonIndex, seasonBlock ->
            val seasonNumber = seasonIndex + 1
            val episodes = mutableListOf<Episode>()
            
            seasonBlock.select("ul.staffelEpisodenList li a.getStaffelStream").forEachIndexed { episodeIndex, episodeLink ->
                val epTitle = episodeLink.ownText().trim()
                val epHref = episodeLink.attr("href")
                val fullEpHref = if (epHref.startsWith("//")) {
                    "https:$epHref"
                } else {
                    epHref
                }
                val epId = fullEpHref.substringAfterLast("/")
                
                episodes.add(
                    Episode(
                        id = epId,
                        number = episodeIndex + 1,
                        title = epTitle
                    )
                )
            }
            
            seasons.add(
                Season(
                    id = "${id}_$seasonNumber",
                    number = seasonNumber,
                    episodes = episodes,
                    poster = tmdbTvShow?.seasons?.find { it.number == seasonNumber }?.poster
                )
            )
        }

        return TvShow(
            id = id,
            title = title,
            poster = poster,
            banner = tmdbTvShow?.banner,
            genres = tmdbTvShow?.genres ?: genres,
            directors = directors,
            cast = actors.map { person ->
                val tmdbPerson = tmdbTvShow?.cast?.find { it.name.equals(person.name, ignoreCase = true) }
                person.copy(image = tmdbPerson?.image)
            },
            rating = tmdbTvShow?.rating ?: rating,
            overview = tmdbTvShow?.overview ?: description,
            released = tmdbTvShow?.released?.let { "${it.get(java.util.Calendar.YEAR)}" } ?: document.selectFirst("ul#detail-content-list > li:has(p:matchesOwn(Release)) a")?.text()?.trim(),
            runtime = tmdbTvShow?.runtime,
            trailer = tmdbTvShow?.trailer,
            imdbId = tmdbTvShow?.imdbId,
            seasons = seasons
        )
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val document = withSslFallback { it.getGenre(id, page) }

        val shows = document.select("div#content article").map { article ->
            val aTag = article.selectFirst("h2 a")
            val href = aTag?.attr("href").orEmpty()
            val title = aTag?.text().orEmpty()

            val posterSrc = article.selectFirst("a img")?.attr("src").orEmpty()
            val fullPosterUrl = if (posterSrc.startsWith("/")) {
                "https://filmpalast.to$posterSrc"
            } else {
                posterSrc
            }

            val info = article.select("*").toInfo()

            Movie(
                id = href.substringAfterLast("/"),
                title = title,
                released = info.released,
                quality = info.quality,
                rating = info.rating ?: 0.0,
                poster = fullPosterUrl
            )
        }

        return Genre(
            id = id,
            name = id.replaceFirstChar { it.uppercase() },
            shows = shows
        )
    }


    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val parts = seasonId.split("_")
        if (parts.size != 2) return emptyList()
        
        val showId = parts[0]
        val seasonNumber = parts[1].toIntOrNull() ?: return emptyList()
        
        val relativeId = BASE_URL + "stream/" + showId
        val document = withSslFallback { it.getTvShow(relativeId) }
        val title = document.selectFirst("h2")?.text() ?: ""

        val cleanedTitle = title.replace(Regex("""\s+S\d+E\d+.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+S\d+.*""", RegexOption.IGNORE_CASE), "")
            .trim()

        val tmdbTvShow = TmdbUtils.getTvShow(cleanedTitle, language = language)
        val tmdbEpisodes = tmdbTvShow?.let { TmdbUtils.getEpisodesBySeason(it.id, seasonNumber, language = language) } ?: emptyList()
        
        val episodes = mutableListOf<Episode>()
        
        document.select("div#staffelWrapper div.staffelWrapperLoop").forEachIndexed { index, seasonBlock ->
            if (index + 1 == seasonNumber) {
                seasonBlock.select("ul.staffelEpisodenList li a.getStaffelStream").forEachIndexed { episodeIndex, episodeLink ->
                    val epTitle = episodeLink.ownText().trim()
                    val epHref = episodeLink.attr("href")
                    val fullEpHref = if (epHref.startsWith("//")) {
                        "https:$epHref"
                    } else {
                        epHref
                    }
                    val epId = fullEpHref.substringAfterLast("/")
                    val epNumber = episodeIndex + 1

                    val tmdbEp = tmdbEpisodes.find { it.number == epNumber }
                    
                    episodes.add(
                        Episode(
                            id = epId,
                            number = epNumber,
                            title = tmdbEp?.title ?: epTitle,
                            poster = tmdbEp?.poster,
                            overview = tmdbEp?.overview
                        )
                    )
                }
            }
        }
        
        return episodes
    }
    override suspend fun getPeople(id: String, page: Int): People {
        val url = "$BASE_URL/search/title/$id"
        val document = withSslFallback { it.getPeoplePage(url) }
        val name = document.selectFirst("h1")?.text() ?: ""
        val image = document.selectFirst("img.cover2")?.attr("src")?.let {
            if (it.startsWith("http")) it else "${BASE_URL.removeSuffix("/")}$it"
        }
        
        // Parse filmography (same structure as movies/series)
        val filmography = document.select("div#content article").map { article ->
            val href = article.selectFirst("h2 a")?.attr("href") ?: ""
            val title = article.selectFirst("h2 a")?.text() ?: ""
            val posterSrc = article.selectFirst("a img")?.attr("src") ?: ""

            val fullPosterUrl = if (posterSrc.startsWith("/")) {
                "https://filmpalast.to$posterSrc"
            } else {
                posterSrc
            }

            val info = article.select("*").toInfo()

            // Determine if it's a movie or TV show based on season/episode info in title
            val isTvShow = title.matches(Regex(".*S\\d+E\\d+.*"))
            
            if (isTvShow) {
                TvShow(
                    id = href.substringAfterLast("/"),
                    title = title,
                    released = info.released,
                    quality = info.quality,
                    rating = info.rating ?: 0.0,
                    poster = fullPosterUrl
                )
            } else {
                Movie(
                    id = href.substringAfterLast("/"),
                    title = title,
                    released = info.released,
                    quality = info.quality,
                    rating = info.rating ?: 0.0,
                    poster = fullPosterUrl
                )
            }
        }
        
        return People(
            id = id,
            name = name,
            image = image,
            filmography = filmography
        )
    }


    private fun Elements.toInfo() =
        this.mapNotNull { it.text().trim().takeIf { it.isNotEmpty() } }.let { textList ->
            val starCount = this.select("img[src*=star_on]").size
            val rating = starCount / 10.0

            object {
                val rating =
                    rating.takeIf { starCount > 0 }

                val quality = textList.find { it in listOf("HD", "SD", "CAM", "TS", "HDRip") }

                val released = textList.find { it.matches(Regex("\\d{4}")) }

            }
        }


    interface FilmpalastService {

        companion object {
            private fun client(trustAll: Boolean): OkHttpClient {
                val base = if (trustAll) NetworkClient.trustAll else NetworkClient.default
                return base.newBuilder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .build()
            }

            fun build(): FilmpalastService = create(client(trustAll = false))

            fun buildUnsafe(): FilmpalastService = create(client(trustAll = true))

            private fun create(client: OkHttpClient): FilmpalastService {
                val retrofit = Retrofit.Builder().baseUrl(BASE_URL)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()
                return retrofit.create(FilmpalastService::class.java)
            }
        }

        @GET("movies/new/page/1")
        suspend fun getHome(): Document

        @GET("serien/view/page/1")
        suspend fun getTvShowsHome(): Document

        @GET
        suspend fun getMoviePage(@Url url: String): Document

        @GET
        suspend fun getTvShow(@Url url: String): Document

        @GET("movies/new/page/{page}")
        suspend fun getMovies(@Path("page") page: Int): Document

        @GET("serien/view/page/{page}")
        suspend fun getTvShows(@Path("page") page: Int): Document

        @GET("search/title/{query}/{page}")
        suspend fun search(@Path("query") query: String,@Path("page") page: Int): Document
        @GET("search/title/{query}")
        suspend fun searchNoPage(@Path("query") query: String): Document

        @GET
        @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        suspend fun getRedirectLink(@Url url: String): Response<ResponseBody>

        @GET("search/genre/{genre}/{page}")
        suspend fun getGenre(@Path("genre") genre: String, @Path("page") page: Int): Document

        @GET
        suspend fun getPeoplePage(@Url url: String): Document

    }

}
