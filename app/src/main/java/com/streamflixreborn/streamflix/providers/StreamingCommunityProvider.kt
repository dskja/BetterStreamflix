package com.streamflixreborn.streamflix.providers

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.VixcloudExtractor
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.TmdbUtils
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import com.google.gson.Gson
import com.streamflixreborn.streamflix.utils.DnsResolver
import org.jsoup.parser.Parser
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import java.security.cert.CertPathValidatorException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.SSLContext
import java.security.SecureRandom
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import android.os.Looper

class StreamingCommunityProvider(private val _language: String? = null) : Provider {

    override val language: String
        get() = _language ?: UserPreferences.currentLanguage ?: "it"

    private val LANG: String
        get() = if (language == "en") "en" else "it"

    private val TAG: String
        get() = "SCProviderDebug[$LANG]"

    private val DEFAULT_DOMAIN: String = "streamingunity.buzz"
    override val baseUrl = DEFAULT_DOMAIN
    private var _domain: String? = null
    private var domain: String
        get() {
            if (!_domain.isNullOrEmpty())
                return _domain!!

            val storedDomain = UserPreferences.streamingcommunityDomain

            _domain = if (storedDomain.isNullOrEmpty())
                DEFAULT_DOMAIN
            else
                storedDomain

            return _domain!!
        }
        set(value) {
            if (value != domain) {
                Log.d(TAG, "Domain changed via setter from $domain to $value")
                UserPreferences.clearProviderCache(name)
                _domain = value
                UserPreferences.streamingcommunityDomain = value
                rebuildService(value)
            }
        }

    override val name: String
        get() = if (language == "it") "StreamingCommunity" else "StreamingCommunity (EN)"

    override val logo get() = if (domain == DEFAULT_DOMAIN) {
        try {
            // Se siamo sul thread principale, non facciamo chiamate di rete sincrone
            if (Looper.myLooper() == Looper.getMainLooper()) {
                "https://$DEFAULT_DOMAIN/apple-touch-icon.png"
            } else {
                val resolvedBase = resolveFinalBaseUrl("https://$DEFAULT_DOMAIN/")
                val host = resolvedBase.substringAfter("https://").substringBefore("/")
                if (host.isNotEmpty() && host != domain) {
                    rebuildService(host)
                    "https://$host/apple-touch-icon.png"
                } else if (host.isNotEmpty() && host == domain) {
                    "https://$domain/apple-touch-icon.png"
                } else {
                    "https://$DEFAULT_DOMAIN/apple-touch-icon.png"
                }
            }
        } catch (_: Exception) {
            "https://$DEFAULT_DOMAIN/apple-touch-icon.png"
        }
    } else {
        "https://$domain/apple-touch-icon.png"
    }
    
    private val MAX_SEARCH_RESULTS = 60

    private var _service: StreamingCommunityService? = null
    private var _serviceLanguage: String? = null
    private var _serviceDomain: String? = null

    private fun getService(): StreamingCommunityService {
        val currentLang = language
        val currentDom = domain
        if (_service == null || _serviceLanguage != currentLang || _serviceDomain != currentDom) {
            Log.d(TAG, "Building service for: https://$currentDom/ with lang $currentLang")
            val finalBase = resolveFinalBaseUrl("https://$currentDom/")
            val host = finalBase.substringAfter("https://").substringBefore("/")
            
            _serviceLanguage = currentLang
            _serviceDomain = host
            _domain = host
            currentBaseUrl = finalBase
            _service = StreamingCommunityService.build(finalBase, currentLang, { domain }, { nd ->
                _domain = nd
                UserPreferences.streamingcommunityDomain = nd
            }, LANG)
        }
        return _service!!
    }

    private var currentBaseUrl: String = "https://$domain/"

    fun rebuildService(newDomain: String = domain) {
        val prefsDomain = UserPreferences.streamingcommunityDomain
        val desiredDomain = when {
            !prefsDomain.isNullOrEmpty() && prefsDomain != domain && prefsDomain != newDomain -> prefsDomain
            else -> newDomain
        }
        
        Log.d(TAG, "Forcing service rebuild for: https://$desiredDomain/")
        val finalBase = resolveFinalBaseUrl("https://$desiredDomain/")
        val host = finalBase.substringAfter("https://").substringBefore("/")
        
        Log.d(TAG, "Service rebuilt with final base: $finalBase (host: $host)")
        _domain = host
        UserPreferences.streamingcommunityDomain = host
        currentBaseUrl = finalBase
        _serviceLanguage = language
        _serviceDomain = host
        _service = StreamingCommunityService.build(finalBase, language, { domain }, { nd ->
            _domain = nd
            UserPreferences.streamingcommunityDomain = nd
        }, LANG)
    }

    private fun rebuildServiceUnsafe(newDomain: String = domain) {
        val finalBase = resolveFinalBaseUrl("https://$newDomain/")
        val host = finalBase.substringAfter("https://").substringBefore("/")
        _domain = host
        UserPreferences.streamingcommunityDomain = host
        currentBaseUrl = finalBase
        _serviceLanguage = language
        _serviceDomain = host
        _service = StreamingCommunityService.buildUnsafe(finalBase, language, LANG)
    }

    private fun resolveFinalBaseUrl(startBaseUrl: String): String {
        Log.d(TAG, "Resolving final URL for: $startBaseUrl")
        
        // Fix per evitare NetworkOnMainThreadException
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "resolveFinalBaseUrl chiamata sul thread principale! Ritorno URL iniziale per evitare crash.")
            return startBaseUrl
        }

        return try {
            val client = StreamingCommunityService.buildBaseClient(startBaseUrl, language)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            
            val req = okhttp3.Request.Builder()
                .url(startBaseUrl)
                .header("User-Agent", StreamingCommunityService.USER_AGENT)
                .header("Referer", startBaseUrl)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                Log.d(TAG, "HTTP redirect final URL: $finalUrl")
                val finalUri = resp.request.url
                finalUri.scheme + "://" + finalUri.host + "/"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving final URL: ${e.message}", e)
            startBaseUrl
        }
    }

    private suspend fun <T> withSslFallback(block: suspend (StreamingCommunityService) -> T): T {
        return try {
            block(getService())
        } catch (e: Exception) {
            val isSsl = e is SSLHandshakeException || e is CertPathValidatorException
            if (!isSsl) throw e
            rebuildServiceUnsafe(domain)
            block(getService())
        }
    }

    private var version: String = ""
        get() {
            if (field != "") return field
            synchronized(this) {
                if (field != "") return field
                Log.d(TAG, "Fetching Home HTML for Inertia version, using path /$LANG/")
                var document = runBlocking { withSslFallback { it.getHome() } }
                var dataAttr = document.selectFirst("#app")?.attr("data-page") ?: ""
                if (dataAttr.isBlank()) {
                    Log.w(TAG, "No data-page attribute found in #app element")
                    field = ""
                    return field
                }
                val decoded = Parser.unescapeEntities(dataAttr, false)
                val dataJson = JSONObject(decoded)
                field = dataJson.getString("version")
                Log.d(TAG, "Extracted Inertia version: $field")
                return field
            }
        }

    private fun getImageLink(filename: String?): String? {
        if (filename.isNullOrEmpty()) return null
        return "https://cdn.$domain/images/$filename"
    }

    private fun parseInertiaData(doc: Document): JSONObject {
        val dataAttr = doc.selectFirst("#app")?.attr("data-page") ?: ""
        if (dataAttr.isBlank()) {
            Log.e(TAG, "parseInertiaData: data-page is empty!")
            return JSONObject()
        }
        val decoded = Parser.unescapeEntities(dataAttr, false)
        val json = JSONObject(decoded)
        
        val pageUrl = json.optString("url")
        Log.d(TAG, "Successfully loaded fresh data for URL: $pageUrl (Inertia Version: ${json.optString("version")})")
        
        return json
    }

    override suspend fun getHome(): List<Category> {
        Log.d(TAG, "getHome called. Using base URL /.../$LANG/")
        val res: StreamingCommunityService.HomeRes = try {
            if (version.isEmpty()) {
                Log.d(TAG, "Version empty, parsing HTML for home")
                val json = parseInertiaData(withSslFallback { it.getHome() })
                Gson().fromJson(json.toString(), StreamingCommunityService.HomeRes::class.java).also {
                    if (version != it.version) version = it.version
                }
            } else {
                try {
                    withSslFallback { it.getHome(version = version) }.also { fetched ->
                        if (version != fetched.version) version = fetched.version
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "JSON Home failed, falling back to HTML: ${e.message}")
                    val json = parseInertiaData(withSslFallback { it.getHome() })
                    Gson().fromJson(json.toString(), StreamingCommunityService.HomeRes::class.java).also {
                        if (version != it.version) version = it.version
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Final error in getHome: ${e.message}", e)
            throw e
        }

        val sliders = res.props.sliders
        if (sliders.size < 3) return listOf()

        val mainTitles = sliders[2].titles
        val categories = mutableListOf<Category>()
        categories.add(Category(name = Category.FEATURED, list = mainTitles.map {
            if (it.type == "movie") Movie(id = it.id + "-" + it.slug, title = it.name, banner = getImageLink(it.images.find { img -> img.type == "background" }?.filename), rating = it.score)
            else TvShow(id = it.id + "-" + it.slug, title = it.name, banner = getImageLink(it.images.find { img -> img.type == "background" }?.filename), rating = it.score)
        }))
        categories.addAll(listOf(0, 1).mapNotNull { index ->
            if (index >= sliders.size) return@mapNotNull null
            val slider = sliders[index]
            Category(name = slider.label, list = slider.titles.map {
                if (it.type == "movie") Movie(id = it.id + "-" + it.slug, title = it.name, released = it.lastAirDate, rating = it.score, poster = getImageLink(it.images.find { img -> img.type == "poster" }?.filename), banner = getImageLink(it.images.find { img -> img.type == "background" }?.filename))
                else TvShow(id = it.id + "-" + it.slug, title = it.name, released = it.lastAirDate, rating = it.score, poster = getImageLink(it.images.find { img -> img.type == "poster" }?.filename), banner = getImageLink(it.images.find { img -> img.type == "background" }?.filename))
            })
        })
        return categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        Log.d(TAG, "search called. Using language: $LANG")
        if (query.isEmpty()) {
            val res = try {
                withSslFallback { it.getHome(version = version) }
            } catch (e: Exception) {
                val json = parseInertiaData(withSslFallback { it.getHome() })
                Gson().fromJson(json.toString(), StreamingCommunityService.HomeRes::class.java)
            }
            if (version != res.version) version = res.version
            return res.props.genres.map { Genre(id = it.id, name = it.name) }.sortedBy { it.name }
        }
        val res = withSslFallback { it.search(query, (page - 1) * MAX_SEARCH_RESULTS, LANG) }
        if (res.currentPage == null || res.lastPage == null || res.currentPage > res.lastPage) return listOf()
        return res.data.map {
            val poster = getImageLink(it.images.find { img -> img.type == "poster" }?.filename)
            if (it.type == "movie") Movie(id = it.id + "-" + it.slug, title = it.name, released = it.lastAirDate, rating = it.score, poster = poster)
            else TvShow(id = it.id + "-" + it.slug, title = it.name, released = it.lastAirDate, rating = it.score, poster = poster)
        }
    }

    private fun getTitlesFromInertiaJson(json: JSONObject): List<StreamingCommunityService.Show> {
        val gson = Gson()
        val showListType: Type = object : TypeToken<List<StreamingCommunityService.Show>>() {}.type
        
        val props = json.optJSONObject("props") ?: return listOf()
        val propsKeys = props.keys().asSequence().toList()
        Log.d(TAG, "getTitlesFromInertiaJson Props Keys: $propsKeys")

        // 1. Tenta di estrarre l'elenco come Array diretto (Pagina 1)
        if (props.has("titles") && props.optJSONArray("titles") != null) {
            val jsonArray = props.optJSONArray("titles")
            val jsonString = jsonArray?.toString()
            Log.d(TAG, "Attempting to map 'titles' as direct array.")
            return jsonString?.let { gson.fromJson<List<StreamingCommunityService.Show>>(it, showListType) } ?: listOf()
        }

        // 2. Tenta di estrarre dall'oggetto ArchiveRes (Paginazione o Pagina 1 alternativa)
        val res: StreamingCommunityService.ArchiveRes? = try {
            gson.fromJson(json.toString(), StreamingCommunityService.ArchiveRes::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to map to ArchiveRes structure: ${e.message}")
            null
        }

        res?.version?.let { version = it }
        
        return res?.props?.let { p ->
            p.archive?.data 
                ?: p.titles?.data 
                ?: p.movies?.data 
                ?: p.tv?.data 
                ?: p.tvShows?.data
        } ?: listOf()
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        Log.d(TAG, "getMovies called. Page: $page, Lang: $LANG")
        val json: JSONObject? = try {
            if (page == 1) {
                parseInertiaData(withSslFallback { it.getMoviesHtml() })
            } else {
                withSslFallback { it.getMoviesJson(version = version, page = page) }.let {
                    JSONObject(Gson().toJson(it)) // Converti l'oggetto ArchiveRes JSON in JSONObject per il parsing
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMovies error: ${e.message}")
            null
        }

        val titles: List<StreamingCommunityService.Show> = json?.let { getTitlesFromInertiaJson(it) } ?: listOf()

        Log.d(TAG, "getMovies found ${titles.size} titles")

        return titles.map { title ->
            Movie(id = title.id + "-" + title.slug, title = title.name, released = title.lastAirDate, rating = title.score, poster = getImageLink(title.images.find { img -> img.type == "poster" }?.filename))
        }.distinctBy { it.id }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        Log.d(TAG, "getTvShows called. Page: $page, Lang: $LANG")
        val json: JSONObject? = try {
            if (page == 1) {
                parseInertiaData(withSslFallback { it.getTvShowsHtml() })
            } else {
                withSslFallback { it.getTvShowsJson(version = version, page = page) }.let {
                    JSONObject(Gson().toJson(it))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTvShows error: ${e.message}")
            null
        }

        val titles: List<StreamingCommunityService.Show> = json?.let { getTitlesFromInertiaJson(it) } ?: listOf()

        Log.d(TAG, "getTvShows found ${titles.size} titles")

        return titles.map { title ->
            TvShow(id = title.id + "-" + title.slug, title = title.name, released = title.lastAirDate, rating = title.score, poster = getImageLink(title.images.find { img -> img.type == "poster" }?.filename))
        }.distinctBy { it.id }
    }

    override suspend fun getMovie(id: String): Movie {
        Log.d(TAG, "getMovie called for id: $id. Using language: $LANG")
        val res: StreamingCommunityService.HomeRes = try {
            withSslFallback { it.getDetails(id, version = version, language = LANG) }.also {
                if (version != it.version) version = it.version
            }
        } catch (e: Exception) {
            Log.w(TAG, "getMovie JSON failed for $id, trying HTML fallback. Error: ${e.message}")
            try {
                val doc = StreamingCommunityService.fetchDocumentWithRedirectsAndSslFallback("https://$domain/$LANG/titles/$id", "https://$domain/", language)
                val json = parseInertiaData(doc)
                Gson().fromJson(json.toString(), StreamingCommunityService.HomeRes::class.java).also {
                    if (version != it.version) version = it.version
                }
            } catch (inner: Exception) {
                Log.e(TAG, "HTML fallback failed for movie $id: ${inner.message}")
                throw inner
            }
        }
        
        val title = res.props.title
        val tmdbMovie = try {
            Log.d(TAG, "Requesting TMDb data for ID ${title.tmdbId} in language: $language")
            title.tmdbId?.let { tmdbId -> TmdbUtils.getMovieById(tmdbId, language = language) }
        } catch (e: Exception) {
            Log.e(TAG, "TMDb request failed (possibly 401): ${e.message}")
            null
        }

        return Movie(id = id, title = tmdbMovie?.title ?: title.name, overview = tmdbMovie?.overview ?: title.plot, released = title.lastAirDate, rating = title.score, quality = title.quality, runtime = title.runtime, poster = getImageLink(title.images.find { img -> img.type == "poster" }?.filename), banner = getImageLink(title.images.find { img -> img.type == "background" }?.filename), genres = title.genres?.map { Genre(id = it.id, name = it.name) } ?: listOf(), cast = title.actors?.map { actor ->
            val tmdbPerson = tmdbMovie?.cast?.find { p -> p.name.equals(actor.name, ignoreCase = true) }
            People(id = actor.name, name = actor.name, image = tmdbPerson?.image)
        } ?: listOf(), trailer = title.trailers?.find { t -> t.youtubeId != "" }?.youtubeId?.let { yid -> "https://youtube.com/watch?v=$yid" }, recommendations = res.props.sliders.firstOrNull()?.titles?.map { it ->
            if (it.type == "movie") Movie(id = it.id + "-" + it.slug, title = it.name, rating = it.score, poster = getImageLink(it.images.find { img -> img.type == "poster" }?.filename))
            else TvShow(id = it.id + "-" + it.slug, title = it.name, rating = it.score, poster = getImageLink(it.images.find { img -> img.type == "poster" }?.filename))
        } ?: listOf())
    }

    override suspend fun getTvShow(id: String): TvShow {
        Log.d(TAG, "getTvShow called for id: $id. Using language: $LANG")
        val res: StreamingCommunityService.HomeRes = try {
            withSslFallback { it.getDetails(id, version = version, language = LANG) }.also {
                if (version != it.version) version = it.version
            }
        } catch (e: Exception) {
            Log.w(TAG, "getTvShow JSON failed for $id, trying HTML fallback. Error: ${e.message}")
            try {
                val doc = StreamingCommunityService.fetchDocumentWithRedirectsAndSslFallback("https://$domain/$LANG/titles/$id", "https://$domain/", language)
                val json = parseInertiaData(doc)
                Gson().fromJson(json.toString(), StreamingCommunityService.HomeRes::class.java).also {
                    if (version != it.version) version = it.version
                }
            } catch (inner: Exception) {
                Log.e(TAG, "HTML fallback failed for show $id: ${inner.message}")
                throw inner
            }
        }

        val title = res.props.title
        val tmdbShow = try {
            Log.d(TAG, "Requesting TMDb data for ID ${title.tmdbId} in language: $language")
            title.tmdbId?.let { tmdbId -> TmdbUtils.getTvShowById(tmdbId, language = language) }
        } catch (e: Exception) {
            Log.e(TAG, "TMDb request failed (possibly 401): ${e.message}")
            null
        }

        return TvShow(id = id, title = tmdbShow?.title ?: title.name, overview = tmdbShow?.overview ?: title.plot, released = title.lastAirDate, rating = title.score, quality = title.quality, poster = getImageLink(title.images.find { img -> img.type == "poster" }?.filename), banner = getImageLink(title.images.find { img -> img.type == "background" }?.filename), genres = title.genres?.map { Genre(id = it.id, name = it.name) } ?: listOf(), cast = title.actors?.map { actor ->
            val tmdbPerson = tmdbShow?.cast?.find { p -> p.name.equals(actor.name, ignoreCase = true) }
            People(id = actor.name, name = actor.name, image = tmdbPerson?.image)
        } ?: listOf(), trailer = title.trailers?.find { t -> t.youtubeId != "" }?.youtubeId?.let { yid -> "https://youtube.com/watch?v=$yid" }, recommendations = res.props.sliders.firstOrNull()?.titles?.map { it ->
            if (it.type == "movie") Movie(id = it.id + "-" + it.slug, title = it.name, rating = it.score, poster = getImageLink(it.images.find { img -> img.type == "poster" }?.filename))
            else TvShow(id = it.id + "-" + it.slug, title = it.name, rating = it.score, poster = getImageLink(it.images.find { img -> img.type == "poster" }?.filename))
        } ?: listOf(), seasons = title.seasons?.map { s ->
            val seasonNumber = s.number.toIntOrNull() ?: (title.seasons.indexOf(s) + 1)
            Season(id = "$id/season-${s.number}", number = seasonNumber, title = s.name, poster = tmdbShow?.seasons?.find { ts -> ts.number == seasonNumber }?.poster)
        } ?: listOf())
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        Log.d(TAG, "getEpisodesBySeason called for $seasonId. Using language: $LANG")
        val res: StreamingCommunityService.SeasonRes = try {
            withSslFallback { it.getSeasonDetails(seasonId, version = version, language = LANG) }.also {
                if (version != it.version) version = it.version
            }
        } catch (e: Exception) {
            Log.w(TAG, "getEpisodesBySeason JSON failed for $seasonId, trying HTML fallback. Error: ${e.message}")
            try {
                val doc = StreamingCommunityService.fetchDocumentWithRedirectsAndSslFallback("https://$domain/$LANG/titles/$seasonId", "https://$domain/", language)
                val json = parseInertiaData(doc)
                Gson().fromJson(json.toString(), StreamingCommunityService.SeasonRes::class.java).also {
                    if (version != it.version) version = it.version
                }
            } catch (inner: Exception) {
                Log.e(TAG, "HTML fallback failed for season $seasonId: ${inner.message}")
                throw inner
            }
        }
        return res.props.loadedSeason.episodes.map {
            Episode(id = "${seasonId.substringBefore("-")}?episode_id=${it.id}", number = it.number.toIntOrNull() ?: (res.props.loadedSeason.episodes.indexOf(it) + 1), title = it.name, poster = getImageLink(it.images.find { img -> img.type == "cover" }?.filename), overview = it.plot)
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        Log.d(TAG, "getGenre called for $id. Page: $page, Using language: $LANG")
        val json: JSONObject? = try {
            if (page == 1) {
                parseInertiaData(withSslFallback { it.getArchiveHtml(genreId = id) })
            } else {
                withSslFallback { it.getArchiveJson(version = version, genreId = id, page = page) }.let {
                    JSONObject(Gson().toJson(it))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getGenre error: ${e.message}")
            null
        }

        val titles: List<StreamingCommunityService.Show> = json?.let { getTitlesFromInertiaJson(it) } ?: listOf()
        
        return Genre(id = id, name = id, shows = titles.map { title ->
            val poster = getImageLink(title.images.find { img -> img.type == "poster" }?.filename) // Corretto: title.images
            if (title.type == "movie") Movie(id = title.id + "-" + title.slug, title = title.name, released = title.lastAirDate, rating = title.score, poster = poster)
            else TvShow(id = title.id + "-" + title.slug, title = title.name, released = title.lastAirDate, rating = title.score, poster = poster)
        })
    }

    override suspend fun getPeople(id: String, page: Int): People {
        Log.d(TAG, "getPeople called for $id. Using language: $LANG")
        val res = withSslFallback { it.search(id, (page - 1) * MAX_SEARCH_RESULTS, LANG) }
        if (res.currentPage == null || res.lastPage == null || res.currentPage > res.lastPage) return People(id = id, name = id)
        return People(id = id, name = id, filmography = res.data.map {
            val poster = getImageLink(it.images.find { img -> img.type == "poster" }?.filename)
            if (it.type == "movie") Movie(id = it.id + "-" + it.slug, title = it.name, released = it.lastAirDate, rating = it.score, poster = poster)
            else TvShow(id = it.id + "-" + it.slug, title = it.name, released = it.lastAirDate, rating = it.score, poster = poster)
        })
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        Log.d(TAG, "getServers called. Base: https://$domain/$LANG/iframe/...")
        val base = "https://$domain/"
        val iframeUrl = when (videoType) {
            is Video.Type.Movie -> base + "$LANG/iframe/" + id.substringBefore("-") + "?language=$LANG"
            is Video.Type.Episode -> base + "$LANG/iframe/" + id.substringBefore("?") + "?episode_id=" + id.substringAfter("=") + "&next_episode=1" + "&language=$LANG"
        }
        val document = StreamingCommunityService.fetchDocumentWithRedirectsAndSslFallback(iframeUrl, base, language)
        val src = document.selectFirst("iframe")?.attr("src") ?: ""
        return listOf(Video.Server(id = id, name = "Vixcloud", src = src))
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return VixcloudExtractor(language).extract(server.src)
    }

    private class UserAgentInterceptor(private val userAgent: String, private val languageProvider: () -> String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val language = languageProvider()
            val requestBuilder = chain.request().newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept-Language", if (language == "en") "en-US,en;q=0.9" else "it-IT,it;q=0.9")
                .header("Cookie", "language=$language")
            
            return chain.proceed(requestBuilder.build())
        }
    }

    private class RefererInterceptor(private val referer: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request().newBuilder().header("Referer", referer).build())
    }

    private class RedirectInterceptor(private val domainProvider: () -> String, private val onDomainChanged: (String) -> Unit) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            var response = chain.proceed(request)
            val visited = mutableSetOf<String>()
            val currentDomain = domainProvider()
            while (response.isRedirect) {
                val location = response.header("Location") ?: break
                val newUrl = if (location.startsWith("http")) location else request.url.resolve(location)?.toString() ?: break
                if (!visited.add(newUrl)) break
                val host = newUrl.substringAfter("https://").substringBefore("/")
                if (host.isNotEmpty() && host != currentDomain) {
                    if (!host.contains("streamingcommunityz.green")) {
                        onDomainChanged(host)
                    }
                }
                response.close()
                request = request.newBuilder().url(newUrl).build()
                response = chain.proceed(request)
            }
            return response
        }
    }

    private interface StreamingCommunityService {
        companion object {
            const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

            fun buildBaseClient(baseUrl: String, language: String, domainProvider: (() -> String)? = null, onDomainChanged: ((String) -> Unit)? = null): OkHttpClient.Builder {
                val logging = HttpLoggingInterceptor { message -> Log.d("SCProviderDebug", "NETWORK: $message") }
                logging.setLevel(HttpLoggingInterceptor.Level.HEADERS)

                val clientBuilder = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .addInterceptor(RefererInterceptor(baseUrl))
                    .addInterceptor(UserAgentInterceptor(USER_AGENT, { language }))
                
                if (domainProvider != null && onDomainChanged != null) {
                    clientBuilder.addInterceptor(RedirectInterceptor(domainProvider, onDomainChanged))
                }

                clientBuilder.addInterceptor(logging)

                val dohProviderUrl = UserPreferences.dohProviderUrl
                if (dohProviderUrl.isNotEmpty() && dohProviderUrl != UserPreferences.DOH_DISABLED_VALUE) {
                    try {
                        val bootstrap = OkHttpClient.Builder().readTimeout(15, TimeUnit.SECONDS).connectTimeout(15, TimeUnit.SECONDS).build()
                        val primaryDoh = DnsOverHttps.Builder().client(bootstrap).url(dohProviderUrl.toHttpUrl()).build()
                        val googleDoh = DnsOverHttps.Builder().client(bootstrap).url("https://dns.google/dns-query".toHttpUrl()).build()
                        clientBuilder.dns(object : Dns {
                            override fun lookup(hostname: String): List<InetAddress> {
                                try { return primaryDoh.lookup(hostname) } catch (_: UnknownHostException) {}
                                try { return googleDoh.lookup(hostname) } catch (_: UnknownHostException) {}
                                throw UnknownHostException("DoH resolution failed for $hostname")
                            }
                        })
                    } catch (_: Exception) { clientBuilder.dns(DnsResolver.doh) }
                } else { clientBuilder.dns(DnsResolver.doh) }
                return clientBuilder
            }

            fun build(baseUrl: String, language: String, domainProvider: () -> String, onDomainChanged: (String) -> Unit, lang: String): StreamingCommunityService {
                val client = buildBaseClient(baseUrl, language, domainProvider, onDomainChanged).build()
                val finalBaseUrl = "$baseUrl$lang/"
                Log.d("SCProviderDebug", "Building service with base URL: $finalBaseUrl")

                return Retrofit.Builder()
                    .baseUrl(finalBaseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()
                    .create(StreamingCommunityService::class.java)
            }

            fun buildUnsafe(baseUrl: String, language: String, lang: String): StreamingCommunityService {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                val client = buildBaseClient(baseUrl, language)
                    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
                    .build()
                val finalBaseUrl = "$baseUrl$lang/"
                Log.d("SCProviderDebug", "Building Unsafe service with base URL: $finalBaseUrl")

                return Retrofit.Builder()
                    .baseUrl(finalBaseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()
                    .create(StreamingCommunityService::class.java)
            }

            fun fetchDocumentWithRedirectsAndSslFallback(url: String, referer: String, language: String): Document {
                val client = buildBaseClient(referer, language).followRedirects(true).followSslRedirects(true).build()
                return try { fetchDocumentWithRedirects(url, referer, client) }
                catch (e: Exception) { 
                    Log.e("SCProviderDebug", "fetchDocument failed: ${e.message}")
                    Jsoup.parse("") 
                }
            }

            private fun fetchDocumentWithRedirects(urlStart: String, referer: String, client: OkHttpClient): Document {
                var currentUrl = urlStart
                val visited = mutableSetOf<String>()
                while (true) {
                    if (!visited.add(currentUrl)) break
                    val req = okhttp3.Request.Builder().url(currentUrl).header("User-Agent", USER_AGENT).header("Referer", referer).get().build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isRedirect) {
                            val loc = resp.header("Location") ?: break
                            currentUrl = resp.request.url.resolve(loc)?.toString() ?: break
                            continue
                        }
                        return Jsoup.parse(resp.body?.string() ?: "")
                    }
                }
                return Jsoup.parse("")
            }
        }

        @GET("./") suspend fun getHome(): Document
        @GET("./") suspend fun getHome(@Header("x-inertia") xInertia: String = "true", @Header("x-inertia-version") version: String): HomeRes

        @GET("archive?type=movie") suspend fun getMoviesHtml(): Document
        @GET("archive?type=movie") suspend fun getMoviesJson(@Header("x-inertia") xInertia: String = "true", @Header("x-inertia-version") version: String, @Query("page") page: Int): ArchiveRes

        @GET("archive?type=tv") suspend fun getTvShowsHtml(): Document
        @GET("archive?type=tv") suspend fun getTvShowsJson(@Header("x-inertia") xInertia: String = "true", @Header("x-inertia-version") version: String, @Query("page") page: Int): ArchiveRes

        @GET("/api/search") suspend fun search(@Query("q", encoded = true) keyword: String, @Query("offset") offset: Int = 0, @Query("lang") language: String): SearchRes
        @GET("archive") suspend fun getArchiveHtml(@Query("genre[]") genreId: String): Document
        @GET("archive") suspend fun getArchiveJson(
            @Header("x-inertia") xInertia: String = "true",
            @Header("x-inertia-version") version: String,
            @Query("genre[]") genreId: String,
            @Query("page") page: Int
        ): ArchiveRes

        @GET("titles/{id}") suspend fun getDetails(@Path("id") id: String, @Header("x-inertia") xInertia: String = "true", @Header("x-inertia-version") version: String, @Query("lang") language: String): HomeRes
        @GET("titles/{id}/") suspend fun getSeasonDetails(@Path("id") id: String, @Header("x-inertia") xInertia: String = "true", @Header("x-inertia-version") version: String, @Query("lang") language: String): SeasonRes
        @GET("iframe/{id}") suspend fun getIframe(@Path("id") id: String, @Query("lang") language: String): Document

        data class Image(val filename: String, val type: String)
        data class Genre(val id: String, val name: String)
        data class Actor(val id: String, val name: String)
        data class Trailer(@SerializedName("youtube_id") val youtubeId: String?)
        data class Season(val number: String, val name: String?)
        data class Show(val id: String, val name: String, val type: String, @SerializedName("tmdb_id") val tmdbId: Int?, val score: Double, val lastAirDate: String, val images: List<Image>, val slug: String, val plot: String?, val genres: List<Genre>?, @SerializedName("main_actors") val actors: List<Actor>?, val trailers: List<Trailer>?, val seasons: List<Season>?, val quality: String?, val runtime: Int?)
        data class Slider(val label: String, val name: String, val titles: List<Show>)
        data class Props(val genres: List<Genre>, val sliders: List<Slider>, val title: Show)
        data class HomeRes(val version: String, val props: Props)
        data class SearchRes(val data: List<Show>, @SerializedName("current_page") val currentPage: Int?, @SerializedName("last_page") val lastPage: Int?)
        data class SeasonPropsEpisodes(val id: String, val images: List<Image>, val name: String, val number: String, val plot: String? = null)
        data class SeasonPropsDetails(val episodes: List<SeasonPropsEpisodes>)
        data class SeasonProps(val loadedSeason: SeasonPropsDetails)
        data class SeasonRes(val version: String, val props: SeasonProps)
        
        // Nuove data class per l'archivio basato su pagine /movies e /tv
        data class ArchivePage(
            val data: List<Show>?, 
            @SerializedName("current_page") val currentPage: Int?, 
            @SerializedName("last_page") val lastPage: Int?
        )
        data class ArchiveProps(
            val archive: ArchivePage?,
            val titles: ArchivePage?,
            val movies: ArchivePage?,
            val tv: ArchivePage?,
            @SerializedName("tv_shows") val tvShows: ArchivePage?
        )
        data class ArchiveRes(val version: String, val props: ArchiveProps?)

        // Vecchia data class per retrocompatibilità con i generi (se /api/archive funziona ancora lì)
        data class ArchiveResOld(val titles: List<Show>)
    }
}