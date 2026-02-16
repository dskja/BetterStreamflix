package com.streamflixreborn.streamflix.providers

import android.text.Html
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.ApiVoirFilmExtractor
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
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.UserPreferences
import org.json.JSONObject
import org.jsoup.nodes.Element
import retrofit2.http.Header
import retrofit2.http.Query
import kotlin.collections.map
import kotlin.collections.mapNotNull
import kotlin.collections.mapIndexed
import kotlin.math.round
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

object UnJourUnFilmProvider : Provider, ProviderPortalUrl, ProviderConfigUrl {
    override val name = "1Jour1Film"

    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
    override val defaultPortalUrl: String = "https://1jour1film-officiel.site/"

    override val portalUrl: String = defaultPortalUrl
        get() {
            val cachePortalURL = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_PORTAL_URL)
            return cachePortalURL.ifEmpty { field }
        }

    override val defaultBaseUrl: String = "https://1jour1film0126b.site/"
    override val baseUrl: String = defaultBaseUrl
        get() {
            val cacheURL = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL)
            return cacheURL.ifEmpty { field }
        }

    override val logo: String
        get() {
            val cacheLogo = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_LOGO)
            return cacheLogo.ifEmpty { portalUrl + "wp-content/uploads/2025/07/1J1F-150x150.jpg" }
        }
    override val language = "fr"
    override val changeUrlMutex = Mutex()

    private lateinit var service: Service
    private var serviceInitialized = false
    private val initializationMutex = Mutex()

    fun ignoreSource(source: String, href: String): Boolean {
        if (arrayOf("youtube.").any {
                href.contains(
                    it,
                    true
                )
            })
            return true
        return false
    }

    override suspend fun getHome(): List<Category> {
        initializeService()

        val document = service.getHome()
        val categories = mutableListOf<Category>()

        categories.add(
            Category(
                name = Category.FEATURED,
                list = document.select("div#slider-movies-tvshows").getOrNull(0)?.select("article.item")
                    ?.map {
                        if ((it.selectFirst("span.item_type")?.text()?:"").contains("TV"))
                            TvShow(
                                id = it.selectFirst("a")
                                    ?.attr("href")?.substringBeforeLast("/")?.substringAfterLast("/")
                                    ?: "",
                                title = it.selectFirst("h3.title")
                                    ?.text()
                                    ?: "",
                                banner = it.selectFirst("img")?.let { img ->
                                    img.attr("src").ifBlank { img.attr("data-src") }
                                } ?: "",
                            )
                        else
                            Movie(
                                id = it.selectFirst("a")
                                    ?.attr("href")?.substringBeforeLast("/")?.substringAfterLast("/")
                                    ?: "",
                                title = it.selectFirst("h3.title")
                                    ?.text()
                                    ?: "",
                                banner = it.selectFirst("img")?.let { img ->
                                    img.attr("src").ifBlank { img.attr("data-src") }
                                } ?: "",
                            )
                    } ?: emptyList(),
            )
        )

        val regex_episode = Regex("^(.*?)-s\\d+-episode-\\d+")
        val regex_saison = Regex("^(.*?)-saison-\\d+")

        document.select("header")
            .filter { it.children().firstOrNull()?.tagName() == "h2" }
            .mapNotNull { part ->

                var sibling: Element? = part.nextElementSibling()

                while (sibling != null) {
                    if (sibling.tagName() == "header" && sibling.selectFirst("h2") != null) {
                        break
                    }

                    val items = sibling.select("article.item")
                        .mapNotNull { item ->
                            val link = item.selectFirst("a") ?: return@mapNotNull null
                            val img = item.selectFirst("img")

                            if (item.hasClass("movies"))
                                Movie(
                                    id = link.attr("href").substringBeforeLast("/").substringAfterLast("/"),
                                    title = img?.attr("alt") ?: "",
                                    poster = img?.let { img ->
                                        img.attr("src").ifBlank { img.attr("data-src") }
                                    } ?: ""
                                )
                            else {
                                var id = link.attr("href").substringBeforeLast("/")
                                    .substringAfterLast("/")
                                id = regex_episode.find(id)?.groupValues?.get(1)
                                        ?: regex_saison.find(id)?.groupValues?.get(1)
                                                ?: id
                                TvShow(
                                    id = id,
                                    title = img?.attr("alt") ?: "",
                                    poster = img?.let { img ->
                                        img.attr("src").ifBlank { img.attr("data-src") }
                                    } ?: "",
                                )
                            }
                        }
                    if (items.isNotEmpty()) {
                        categories.add(
                            Category(
                                name = part.selectFirst("h2")?.text() ?: "",
                                list = items
                            )
                        )
                    }
                    sibling = sibling.nextElementSibling()
                }
            }


        return categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (page > 1) return emptyList()
        initializeService()
        if (query.isEmpty()) {
            val document = service.getHome()

            val genres = document.selectFirst("ul.mega-sub-menu:has(li.mega-menu-item-object-genres)")
                    ?.select("li.mega-menu-item-object-genres")
                    ?.mapNotNull {
                        val a = it.selectFirst(">a")
                        if (a == null) return@mapNotNull null

                        Genre(
                            id = a.attr("href").substringBeforeLast("/").substringAfterLast("/"),
                            name = a.text(),
                        )
                    } ?: emptyList()

            return genres
        }

        val document = service.search( query )

        val results = document.select("div.result-item > article")
            .mapNotNull {
                val link = it.selectFirst("div.title")?.selectFirst("a")
                val id = link
                    ?.attr("href")
                    ?: "";
                if (id.contains("/films/")) {
                    Movie(
                        id = id.substringBeforeLast("/").substringAfterLast("/"),
                        title = link
                            ?.text()
                            ?: "",
                        poster = it.selectFirst("img.lazyload")?.attr("data-src")
                    )
                } else if (id.contains("/tvshows/")) {
                    TvShow(
                        id = id.substringBeforeLast("/").substringAfterLast("/"),
                        title = link
                            ?.text()
                            ?: "",
                        poster = it.selectFirst("img.lazyload")?.attr("data-src")
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

        var movies: List<Movie> = emptyList()
        if (page == 1) {
            movies = document.select("div#slider-movies").getOrNull(0)?.select("article.item")
                ?.map {
                    Movie(
                        id = it.selectFirst("a")
                            ?.attr("href")?.substringBeforeLast("/")?.substringAfterLast("/")
                            ?: "",
                        title = it.selectFirst("h3.title")
                            ?.text()
                            ?: "",
                        poster = it.selectFirst("img")?.let { img ->
                            img.attr("src").ifBlank { img.attr("data-src") }
                        } ?: "",
                    )
                } ?: emptyList();

            val items = document.selectFirst("div.items.featured")
                ?.select("article.item")
                            ?.mapNotNull { item ->
                                val link = item.selectFirst("a") ?: return@mapNotNull null
                                val img = item.selectFirst("img")

                                    Movie(
                                        id = link.attr("href"),
                                        title = img?.attr("alt") ?: "",
                                        poster = img?.let { img ->
                                            img.attr("src").ifBlank { img.attr("data-src") }
                                        } ?: ""
                                    )
                            }
                ?: emptyList()

            if (items.isNotEmpty()) {
                movies = movies + items
            }
        }

        val itemsRec = document.selectFirst("div.items.full")
            ?.select("article.item")
            ?.mapNotNull { item ->
                val link = item.selectFirst("a") ?: return@mapNotNull null
                val img = item.selectFirst("img")

                Movie(
                    id = link.attr("href"),
                    title = img?.attr("alt") ?: "",
                    poster = img?.let { img ->
                        img.attr("src").ifBlank { img.attr("data-src") }
                    } ?: ""
                )
            }
            ?: emptyList()

        if (itemsRec.isNotEmpty()) {
            movies = movies + itemsRec
        }

        return movies
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        initializeService()
        val document = service.getTvShows(page)

        var tvshows: List<TvShow> = emptyList()

        if (page == 1) {
            tvshows = document.select("div#slider-tvshows").getOrNull(0)?.select("article.item")
                ?.map {
                    TvShow(
                        id = it.selectFirst("a")
                            ?.attr("href")?.substringBeforeLast("/")?.substringAfterLast("/")
                            ?: "",
                        title = it.selectFirst("h3.title")
                            ?.text()
                            ?: "",
                        poster = it.selectFirst("img")?.let { img ->
                            img.attr("src").ifBlank { img.attr("data-src") }
                        } ?: "",
                    )
                } ?: emptyList();

            val items = document.selectFirst("div.items.featured")
                ?.select("article.item")
                ?.mapNotNull { item ->
                    val link = item.selectFirst("a") ?: return@mapNotNull null
                    val img = item.selectFirst("img")

                    TvShow(
                        id = link.attr("href"),
                        title = img?.attr("alt") ?: "",
                        poster = img?.let { img ->
                            img.attr("src").ifBlank { img.attr("data-src") }
                        } ?: ""
                    )
                }
                ?: emptyList()

            if (items.isNotEmpty()) {
                tvshows = tvshows + items
            }
        }

        val itemsRec = document.selectFirst("div.items.full")
            ?.select("article.item")
            ?.mapNotNull { item ->
                val link = item.selectFirst("a") ?: return@mapNotNull null
                val img = item.selectFirst("img")

                TvShow(
                    id = link.attr("href"),
                    title = img?.attr("alt") ?: "",
                    poster = img?.let { img ->
                        img.attr("src").ifBlank { img.attr("data-src") }
                    } ?: ""
                )
            }
            ?: emptyList()

        if (itemsRec.isNotEmpty()) {
            tvshows = tvshows + itemsRec
        }

        return tvshows
    }

    suspend fun getRating(votes: Element): Double {
        val voteplus = votes
            .selectFirst("span.ratingtypeplusminus")
            ?.text()
            ?.toIntOrNull() ?: 0

        val votenum = votes
            .select("span[id]")
            .last()
            ?.text()
            ?.toIntOrNull() ?: 0

        val rating = if (votenum >= voteplus && votenum > 0) {
            round((votenum - (votenum - voteplus) / 2.0) / votenum * 100) / 10
        } else 0.0

        return rating
    }

    fun decodeHtml(s: String): String {
        @Suppress("DEPRECATION")
        val text = Html.fromHtml(s).toString()
        return JSONObject("""{"v":"$text"}""").getString("v")
    }

    data class itemLink(
        val embed_url: String?,
        val type: String?
    )

    override suspend fun getMovie(id: String): Movie {
        initializeService()
        val document = service.getMovie(id)

        val scriptdoc = document.head().selectFirst("script[type=application/ld+json]:not([class])")?.data().orEmpty()

        val title = scriptdoc.substringAfter("name\":\"").substringBefore("\",")
        val overview = decodeHtml(scriptdoc.substringAfter("description\":\"").substringBefore("\",")).substringAfter(": ").substringBeforeLast(" Voir ")
        val released = id.substringAfterLast("-")
        val strTrailerURL = scriptdoc.substringAfter("\"embedUrl\":").substringBefore(",").replace("\"","")

        val trailerURL: String? = strTrailerURL.takeIf { it != "null" && it.isNotEmpty() }
                                    ?.substringBefore("?")
                                    ?.substringAfterLast("/")
                                    ?.let { "https://www.youtube.com/watch?v=$it" }

        val movie = Movie(
            id = id,
            title = decodeHtml(title),
            overview = decodeHtml(overview),
            released = released,
            runtime = document.select("span.runtime")
                .text().substringAfter(" ").let {
                    val hours = it.substringBefore("h").toIntOrNull() ?: 0
                    val minutes =
                        it.substringAfter("h").trim().toIntOrNull() ?: 0
                    hours * 60 + minutes
                }.takeIf { it != 0 },
            quality = document.selectFirst("div.fakeplayer span.quality")
                ?.text(),
            poster = document.selectFirst("div.poster > img.lazyload")
                ?.attr("data-src")
                ?: "",
            trailer = trailerURL,
            genres = document.select("div.sgeneros")
                .select("a").mapNotNull {
                    Genre(
                        id = it.attr("href").substringBeforeLast("/").substringAfterLast("/"),
                        name = it.text(),
                    )
                },
            directors = document.select("div.persons > div.person[itemprop=director]").map { it ->
                val id = it.selectFirst("a[itemprop=url]")
                    People(
                        id = id?.attr("href")?:"",
                        name = id?.text()?:"",
                    )
                },
            cast = document.select("div.persons > div.person[itemprop=actor]").map { it ->
                val id = it.selectFirst("a[itemprop=url]")
                People(
                    id = id?.attr("href")?.substringBeforeLast("/")?.substringAfterLast("/")?:"",
                    name = id?.text()?:"",
                    image = it.selectFirst("div.img > a > img")?.attr("data-src")?:""
                )
            },
            rating = document.selectFirst("span.dt_rating_vgs[itemprop=ratingValue]")?.text()?.toDoubleOrNull(),
            recommendations = document.select("div#single_relacionados > article").map {
                val id = it.selectFirst("a")
                val img = it.selectFirst("img.lazyload")
                val href = id?.attr("href")?:""
                if (href.contains("/films/")) {
                    Movie(
                        id = href.substringBeforeLast("/").substringAfterLast("/"),
                        poster = img?.attr("data-src") ?: "",
                        title = img?.attr("alt") ?: ""
                    )
                } else {
                    TvShow(
                        id = href.substringBeforeLast("/").substringAfterLast("/"),
                        poster = img?.attr("data-src") ?: "",
                        title = img?.attr("alt") ?: ""
                    )
                }
            }

        )

        return movie
    }

    override suspend fun getTvShow(id: String): TvShow {
        initializeService()

        val document = service.getTvShow(id)

        val scriptdoc = document.head().selectFirst("script[type=application/ld+json]:not([class])")?.data().orEmpty()

        val title = scriptdoc.substringAfter("name\":\"").substringBefore("\",")
        var overview = decodeHtml(document.selectFirst("div.wp-content")?.text()?:"")
        if (overview.startsWith("Regarder ")) {
            overview = overview.substringAfter(": ")
        }

        val trailerURL = document.selectFirst("div#trailer div.embed iframe")?.attr("src")
            ?.substringBefore("?")
            ?.substringAfterLast("/")
            ?.let { "https://www.youtube.com/watch?v=$it" }

        var releaseFirst = ""
        var releaseLast = ""

        val seasons = document.selectFirst("div#seasons")
            ?.select("div.se-c")
            ?.mapIndexed { idx, season ->
                val release = (season.selectFirst("span.title")?.selectFirst("i")?.text()?:"").substringAfterLast(", ")
                if (releaseFirst.isBlank()) releaseFirst = release
                releaseLast = release
                val title = (season.selectFirst("span.title")?.text()?:"Saison $idx").replaceAfterLast(")","")
                val number = title.substringAfter("Saison ").substringBefore(" ").toInt()

                Season(
                    id = "$id/$idx",
                    number = number,
                    title = title,
                    poster = season.selectFirst("img.lazyload")?.attr("data-src")
                )
            } ?: emptyList()

        val released = if (releaseFirst != releaseLast) "$releaseLast-$releaseFirst" else releaseFirst
        val tvShow = TvShow(
            id = id,
            title = decodeHtml(title),
            overview = decodeHtml(overview),
            released = released,
            poster = document.selectFirst("div.poster > img.lazyload")
                ?.attr("data-src")
                ?: "",

            seasons = seasons,
            trailer = trailerURL,
            genres = document.select("div.sgeneros")
                .select("a").mapNotNull {
                    Genre(
                        id = it.attr("href").substringBeforeLast("/").substringAfterLast("/"),
                        name = it.text(),
                    )
                },
            directors = document.select("div.persons > div.person[itemprop=director]").map { it ->
                val id = it.selectFirst("a[itemprop=url]")
                People(
                    id = id?.attr("href")?:"",
                    name = id?.text()?:"",
                )
            },
            cast = document.select("div.persons > div.person[itemprop=actor]").map { it ->
                val id = it.selectFirst("a[itemprop=url]")
                People(
                    id = id?.attr("href")?.substringBeforeLast("/")?.substringAfterLast("/")?:"",
                    name = id?.text()?:"",
                    image = it.selectFirst("div.img > a > img")?.attr("data-src")?:""
                )
            },
            rating = document.selectFirst("span.dt_rating_vgs[itemprop=ratingValue]")?.text()?.toDoubleOrNull(),
            recommendations = document.select("div#single_relacionados > article").map {
                val id = it.selectFirst("a")
                val img = it.selectFirst("img.lazyload")
                val href = id?.attr("href") ?: ""
                if (href.contains("/films/")) {
                    Movie(
                        id = href.substringBeforeLast("/").substringAfterLast("/"),
                        poster = img?.attr("data-src") ?: "",
                        title = img?.attr("alt") ?: ""
                    )
                } else {
                    TvShow(
                        id = href.substringBeforeLast("/").substringAfterLast("/"),
                        poster = img?.attr("data-src") ?: "",
                        title = img?.attr("alt") ?: ""
                    )
                }
            }
        )

        return tvShow
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        initializeService()
        val (tvShowId, seasonNum) = seasonId.split("/")
        val document = service.getTvShow(tvShowId)

        val season = document.selectFirst("div#seasons")
            ?.select("div.se-c")[seasonNum.toInt()]

        val defaultPoster = document.selectFirst("div.poster > img.lazyload")
            ?.attr("data-src")
            ?: ""

        val episodes =
            season?.select("ul.episodios > li")?.mapIndexed { idx, ep ->
                val number = idx + 1
                val link = ep.selectFirst("div.episodiotitle > a")
                val url = link?.attr("href") ?: ""

                val id = url.substringBeforeLast("/").substringAfterLast("/")
                val episode = service.getEpisode(id)

                val overview = decodeHtml(episode.selectFirst("div.wp-content > p")?.text() ?: "")
                Episode(
                    id = id,
                    number = number,
                    poster = ep.selectFirst("img.lazyload")?.attr("data-src") ?: defaultPoster,
                    title = link?.text() ?: "",
                    overview = overview
                )
            } ?: emptyList()

        return episodes
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        initializeService()

        val document = try {
            service.getGenre(id, page)
        } catch (e: HttpException) {
            when (e.code()) {
                   404 -> return Genre(id, "")
                else -> throw e
            }
        }

        val genre = Genre(
            id = id,
            name = "",
            shows = document.select("div.items.full > article.item.movies, article.item.tvshows")
                .mapNotNull {
                    val link = it.selectFirst("div.data")?.selectFirst("a")
                    val fhref = link
                        ?.attr("href")?:""
                    val href = fhref.substringBeforeLast("/").substringAfterLast("/")
                    if (fhref.contains("films/")) {
                        Movie(
                            href,
                            title = link
                                ?.text()
                                ?: "",
                            poster = it.selectFirst("img")?.attr("src")
                        )
                    } else if (fhref.contains("tvshows/")) {
                        TvShow(
                            id = href,
                            title = link
                                ?.text()
                                ?: "",
                            poster = it.selectFirst("img")?.attr("src")
                        )
                    } else {
                        null
                    }
                }
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
            filmography = document.select("div.items.full > article.item.movies, article.item.tvshows")
                .mapNotNull {
                    val link = it.selectFirst("div.data")?.selectFirst("a")
                    val fhref = link
                        ?.attr("href")?:""
                    val href = fhref.substringBeforeLast("/").substringAfterLast("/")
                    if (fhref.contains("/films/")) {
                        Movie(
                            href,
                            title = link
                                ?.text()
                                ?: "",
                            poster = it.selectFirst("img")?.attr("src")
                        )
                    } else if (fhref.contains("/tvshows/")) {
                        TvShow(
                            id = href,
                            title = link
                                ?.text()
                                ?: "",
                            poster = it.selectFirst("img")?.attr("src")
                        )
                    } else {
                        null
                    }
                }
        )

        return people
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        initializeService()

        val apivoirfilm = ApiVoirFilmExtractor()
        var apiUrl = ""

        val servers = when (videoType) {
            is Video.Type.Episode -> {
                val document = service.getEpisode(id)

                document.selectFirst("ul#playeroptionsul")?.select("li.dooplay_player_option")
                    ?.mapIndexedNotNull { idx, it ->
                        val nume = it.attr("data-nume")
                        val post = it.attr("data-post")
                        val link = service.getServers(num=nume, post=post, type="tv")

                        if (link.embed_url.isNullOrEmpty() ||
                            ignoreSource("", link.embed_url))
                            return@mapIndexedNotNull null

                        if (link.embed_url.startsWith(apivoirfilm.mainUrl)) {
                            apiUrl = link.embed_url
                            return@mapIndexedNotNull null
                        }

                        val title = it.selectFirst("span.title")?.text()?:"Server $idx"

                            Video.Server(
                            id = "srv$idx",
                            name = title,
                            src = link.embed_url
                        )}
                    ?: emptyList()

            }

            is Video.Type.Movie -> {
                val document = service.getMovie(id)

                document.selectFirst("ul#playeroptionsul")?.select("li.dooplay_player_option")
                    ?.mapIndexedNotNull { idx, it ->
                        val nume = it.attr("data-nume")
                        val post = it.attr("data-post")
                        val link = service.getServers(num=nume, post=post)

                        if (link.embed_url.isNullOrEmpty() ||
                            ignoreSource("", link.embed_url))
                                return@mapIndexedNotNull null

                        if (link.embed_url.startsWith(apivoirfilm.mainUrl)) {
                            apiUrl = link.embed_url
                            return@mapIndexedNotNull null
                        }

                        val title = it.selectFirst("span.title")?.text()?:"Server $idx"

                        Video.Server(
                            id = "srv$idx",
                            name = title,
                            src = link.embed_url
                        )}
                    ?: emptyList()
            }
        }

        val other = if (apiUrl.isNotEmpty())
                        apivoirfilm.expand(apiUrl, baseUrl, "FR ")
                    else emptyList()

        return servers + other
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.src)
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

                    val newUrl = document.html().substringAfter("window.location.href = \"").substringBefore("\"")
                        .trim()
                    if (!newUrl.isNullOrEmpty()) {
                        val newIcon = document.selectFirst("link[rel=apple-touch-icon]")
                                                ?.attr("href") ?:
                                                "$defaultPortalUrl/wp-content/uploads/2025/07/1J1F-150x150.jpg"
                        val newUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
                        UserPreferences.setProviderCache(this,UserPreferences.PROVIDER_URL, newUrl)
                        UserPreferences.setProviderCache(
                            this,
                            UserPreferences.PROVIDER_LOGO,
                            newIcon
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
            @Header("User-agent") user_agent: String = USER_AGENT
        ): Document

        @GET("/")
        suspend fun search(
            @Query("s") query: String,
            @Header("User-agent") user_agent: String = USER_AGENT
        ): Document

        @GET("films/page/{page}/")
        suspend fun getMovies(
            @Path("page") page: Int,
            @Header("User-agent") user_agent: String = USER_AGENT
        ): Document

        @POST("wp-admin/admin-ajax.php")
        @FormUrlEncoded
        suspend fun getServers(
            @Field("action") action: String = "doo_player_ajax",
            @Field("post") post: String,
            @Field("nume") num: String,
            @Field("type") type: String = "movie",
            @Header("User-agent") user_agent: String = USER_AGENT
        ): itemLink

        @GET("tvshows/page/{page}/")
        suspend fun getTvShows(
            @Path("page") page: Int,
            @Header("User-agent") user_agent: String = USER_AGENT
        ): Document

        @GET("episodes/{id}/")
        suspend fun getEpisode(
            @Path("id") id: String,
            @Header("User-agent") user_agent: String = USER_AGENT
        ): Document

        @GET("films/{id}/")
        suspend fun getMovie(
            @Path("id") id: String,
            @Header("User-agent") user_agent: String = USER_AGENT,
        ): Document

        @GET("tvshows/{id}/")
        suspend fun getTvShow(
            @Path("id") id: String,
            @Header("User-agent") user_agent: String = USER_AGENT
        ): Document


        @GET("genre/{genre}/page/{page}/")
        suspend fun getGenre(
            @Path("genre") genre: String,
            @Path("page") page: Int,
            @Header("User-agent") cookie: String = USER_AGENT
        ): Document

        @GET("cast/{id}/page/{page}")
        suspend fun getPeople(
            @Path("id") id: String,
            @Path("page") page: Int,
            @Header("User-agent") cookie: String = USER_AGENT
        ): Document
    }
}