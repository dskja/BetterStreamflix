package com.streamflixreborn.streamflix.providers

import android.util.Log
import androidx.core.net.toUri
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.models.*
import com.streamflixreborn.streamflix.utils.NetworkClient
import com.streamflixreborn.streamflix.utils.UserPreferences
import com.streamflixreborn.streamflix.utils.TMDb3
import com.streamflixreborn.streamflix.utils.TMDb3.original
import com.streamflixreborn.streamflix.utils.TMDb3.w500
import com.streamflixreborn.streamflix.utils.WebViewResolver
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

object PoseidonHD2Provider : Provider {

    override val baseUrl: String get() = "https://${UserPreferences.poseidonDomain}"
    override val name = "Poseidonhd2"
    override val logo: String get() = "$baseUrl/_next/image?url=%2F_next%2Fstatic%2Fmedia%2Fposeidonhd2.86e0c298.png&w=640&q=75"
    override val language = "es"

    private const val TAG = "PoseidonHD2"

    private suspend fun getDocument(url: String): Document {
        try {
            val client = NetworkClient.default.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    if (response.isRedirect) {
                        val location = response.header("Location")
                        if (!location.isNullOrEmpty()) {
                            val newHost = if (location.startsWith("http")) {
                                java.net.URL(location).host
                            } else {
                                null
                            }
                            if (!newHost.isNullOrEmpty() && newHost != UserPreferences.poseidonDomain) {
                                Log.d(TAG, "Domain changed from ${UserPreferences.poseidonDomain} to $newHost")
                                UserPreferences.poseidonDomain = newHost
                            }
                        }
                    }
                    response
                }
                .build()

            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", NetworkClient.USER_AGENT)
                .header("Referer", baseUrl)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                if (!html.contains("cf-browser-verification") && !html.contains("Checking your browser") && !html.contains("Just a moment...")) {
                    return Jsoup.parse(html).apply { setBaseUri(baseUrl) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OkHttp failed for $url, trying WebView")
        }

        val resolver = WebViewResolver(StreamFlixApp.instance)
        val html = resolver.get(url)
        return Jsoup.parse(html).apply { setBaseUri(baseUrl) }
    }

    override suspend fun getHome(): List<Category> {
        val document = getDocument(baseUrl)
        val jsonData = document.selectFirst("script#__NEXT_DATA__")?.data() ?: return emptyList()
        val json = JSONObject(jsonData).getJSONObject("props").getJSONObject("pageProps")
        val categories = mutableListOf<Category>()

        fun parseNextItem(item: JSONObject): Show? {
            val urlObj = item.optJSONObject("url") ?: return null
            val slug = urlObj.optString("slug") ?: return null
            val id = slug.substringAfter("/")
            val title = item.optJSONObject("titles")?.optString("name") 
                ?: item.optString("title") 
                ?: return null
            
            val images = item.optJSONObject("images")
            val rawPoster = images?.optString("poster") ?: item.optString("image") ?: ""
            val imgUrl = rawPoster.toUri().getQueryParameter("url") ?: rawPoster
            val poster = if (imgUrl.startsWith("http")) imgUrl 
                         else if (imgUrl.isNotEmpty()) "${baseUrl.trimEnd('/')}/${imgUrl.trimStart('/')}" 
                         else null
            
            val banner = images?.optString("backdrop")?.let { 
                if (it.startsWith("http")) it else "${baseUrl.trimEnd('/')}/${it.trimStart('/')}" 
            }
            
            val year = item.optString("releaseDate", "").take(4).ifEmpty {
                item.optString("year").ifEmpty { null }
            }

            return if (slug.contains("movies/") || slug.contains("pelicula/")) {
                Movie(
                    id = id,
                    title = title,
                    poster = poster,
                    banner = banner,
                    released = year
                )
            } else if (slug.contains("series/") || slug.contains("serie/")) {
                TvShow(
                    id = id,
                    title = title,
                    poster = poster,
                    banner = banner,
                    released = year
                )
            } else null
        }

        // 1. FEATURED
        json.optJSONArray("sliderItems")?.let { array ->
            val list = (0 until array.length()).mapNotNull { i -> parseNextItem(array.getJSONObject(i)) }
            if (list.isNotEmpty()) categories.add(Category(Category.FEATURED, list))
        }

        // 2. TABS (Movies)
        val tabMap = listOf(
            "tabLastMovies" to "Últimas películas",
            "tabTopMovies" to "Películas destacadas",
            "tabLastReleasedMovies" to "Estrenos de películas"
        )
        for ((key, name) in tabMap) {
            json.optJSONArray(key)?.let { array ->
                val list = (0 until array.length()).mapNotNull { i -> parseNextItem(array.getJSONObject(i)) }
                if (list.isNotEmpty()) categories.add(Category(name, list))
            }
        }

        // 3. SERIES
        json.optJSONArray("series")?.let { array ->
            val list = (0 until array.length()).mapNotNull { i -> parseNextItem(array.getJSONObject(i)) }
            if (list.isNotEmpty()) categories.add(Category("Últimas series", list))
        }
        
        json.optJSONArray("topSeriesDay")?.let { array ->
            val list = (0 until array.length()).mapNotNull { i -> parseNextItem(array.getJSONObject(i)) }
            if (list.isNotEmpty()) categories.add(Category("Series destacadas (Hoy)", list))
        }

        // 4. EPISODES
        json.optJSONArray("episodes")?.let { array ->
            val list = (0 until array.length()).mapNotNull { i ->
                val item = array.getJSONObject(i)
                val slug = item.optJSONObject("url")?.optString("slug") ?: return@mapNotNull null
                val seriesId = slug.substringAfter("series/").substringBefore("/seasons")
                val title = item.optString("title")
                val rawPoster = item.optString("image")
                val imgUrl = rawPoster.toUri().getQueryParameter("url") ?: rawPoster
                val poster = if (imgUrl.startsWith("http")) imgUrl else "${baseUrl.trimEnd('/')}/${imgUrl.trimStart('/')}"
                TvShow(
                    id = seriesId,
                    title = title,
                    poster = poster
                )
            }
            if (list.isNotEmpty()) categories.add(Category("Últimos episodios", list))
        }

        return categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isEmpty()) {
            return listOf(
                Genre("accion", "Acción"),
                Genre("animacion", "Animación"),
                Genre("crimen", "Crimen"),
                Genre("familia", "Fámilia"),
                Genre("misterio", "Misterio"),
                Genre("suspenso", "Suspenso"),
                Genre("aventura", "Aventura"),
                Genre("ciencia-ficcion", "Ciencia Ficción"),
                Genre("drama", "Drama"),
                Genre("fantasia", "Fantasía"),
                Genre("romance", "Romance"),
                Genre("terror", "Terror")
            )
        }
        if (page > 1) return emptyList()

        val document = getDocument("${baseUrl.trimEnd('/')}/search?q=${URLEncoder.encode(query, "UTF-8")}")
        val items = mutableListOf<AppAdapter.Item>()
        val elements = document.select("li.TPostMv, article.TPost, div.TPost, div.col article")
        for (element in elements) {
            val linkElement = element.selectFirst("a") ?: continue
            val href = linkElement.attr("href")
            val title = element.selectFirst(".Title")?.text() 
                ?: element.selectFirst("h3")?.text() 
                ?: continue
            val year = element.selectFirst(".Year")?.text() 
                ?: element.selectFirst("span")?.text() ?: ""
            
            val rawImgUrl = element.selectFirst("img")?.attr("src") ?: ""
            val imgUrl = rawImgUrl.toUri().getQueryParameter("url") ?: rawImgUrl
            val poster = if (imgUrl.startsWith("http")) imgUrl 
                         else if (imgUrl.isNotEmpty()) "${baseUrl.trimEnd('/')}/${imgUrl.trimStart('/')}" 
                         else null

            when {
                href.contains("/pelicula/") -> {
                    items.add(Movie(id = href.substringAfter("/pelicula/"), title = title, released = year, poster = poster))
                }
                href.contains("/serie/") -> {
                    items.add(TvShow(id = href.substringAfter("/serie/"), title = title, released = year, poster = poster))
                }
            }
        }
        return items.distinctBy { if (it is Movie) it.id else (it as TvShow).id }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val url = if (page == 1) "${baseUrl.trimEnd('/')}/peliculas" else "${baseUrl.trimEnd('/')}/peliculas/page/$page"
        val document = getDocument(url)
        return document.select("li.TPostMv, article.TPost, div.TPost, div.col article").mapNotNull { element ->
            val linkElement = element.selectFirst("a") ?: return@mapNotNull null
            val href = linkElement.attr("href")
            if (!href.contains("/pelicula/")) return@mapNotNull null
            
            val title = element.selectFirst(".Title")?.text() 
                ?: element.selectFirst("h3")?.text() 
                ?: return@mapNotNull null
            
            val rawImgUrl = element.selectFirst("img")?.attr("src") ?: ""
            val imgUrl = rawImgUrl.toUri().getQueryParameter("url") ?: rawImgUrl
            val poster = if (imgUrl.startsWith("http")) imgUrl 
                         else if (imgUrl.isNotEmpty()) "${baseUrl.trimEnd('/')}/${imgUrl.trimStart('/')}" 
                         else null

            Movie(
                id = href.substringAfter("/pelicula/"),
                title = title,
                poster = poster,
                released = element.selectFirst(".Year")?.text()
            )
        }.distinctBy { it.id }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val url = if (page == 1) "${baseUrl.trimEnd('/')}/series" else "${baseUrl.trimEnd('/')}/series/page/$page"
        val document = getDocument(url)
        return document.select("li.TPostMv, article.TPost, div.TPost, div.col article").mapNotNull { element ->
            val linkElement = element.selectFirst("a") ?: return@mapNotNull null
            val href = linkElement.attr("href")
            if (!href.contains("/serie/")) return@mapNotNull null
            
            val title = element.selectFirst(".Title")?.text() 
                ?: element.selectFirst("h3")?.text() 
                ?: return@mapNotNull null
            
            val rawImgUrl = element.selectFirst("img")?.attr("src") ?: ""
            val imgUrl = rawImgUrl.toUri().getQueryParameter("url") ?: rawImgUrl
            val poster = if (imgUrl.startsWith("http")) imgUrl 
                         else if (imgUrl.isNotEmpty()) "${baseUrl.trimEnd('/')}/${imgUrl.trimStart('/')}" 
                         else null

            TvShow(
                id = href.substringAfter("/serie/"),
                title = title,
                poster = poster,
                released = element.selectFirst(".Year")?.text()
            )
        }.distinctBy { it.id }
    }

    override suspend fun getMovie(id: String): Movie {
        val document = getDocument("${baseUrl.trimEnd('/')}/pelicula/$id")
        val jsonData = document.selectFirst("script#__NEXT_DATA__")?.data()
        if (jsonData != null) {
            val json = JSONObject(jsonData).getJSONObject("props").getJSONObject("pageProps").getJSONObject("thisMovie")
            val tmdbId = json.optString("TMDbId")
            if (tmdbId.isNotEmpty()) {
                try {
                    return TMDb3.Movies.details(
                        movieId = tmdbId.toInt(),
                        appendToResponse = listOf(
                            TMDb3.Params.AppendToResponse.Movie.CREDITS,
                            TMDb3.Params.AppendToResponse.Movie.RECOMMENDATIONS,
                            TMDb3.Params.AppendToResponse.Movie.VIDEOS,
                        ),
                        language = language
                    ).let { tmdbMovie ->
                        Movie(
                            id = id,
                            title = tmdbMovie.title,
                            overview = tmdbMovie.overview,
                            released = tmdbMovie.releaseDate,
                            runtime = tmdbMovie.runtime,
                            trailer = tmdbMovie.videos?.results
                                ?.sortedBy { it.publishedAt ?: "" }
                                ?.firstOrNull { it.site == TMDb3.Video.VideoSite.YOUTUBE }
                                ?.let { "https://www.youtube.com/watch?v=${it.key}" },
                            rating = tmdbMovie.voteAverage.toDouble(),
                            poster = tmdbMovie.posterPath?.original,
                            banner = tmdbMovie.backdropPath?.original,
                            genres = tmdbMovie.genres.map { Genre(it.id.toString(), it.name) },
                            cast = tmdbMovie.credits?.cast?.map { People(it.id.toString(), it.name, it.profilePath?.w500) } ?: emptyList(),
                            recommendations = tmdbMovie.recommendations?.results?.mapNotNull { multi ->
                                when (multi) {
                                    is TMDb3.Movie -> Movie(id = multi.id.toString(), title = multi.title, poster = multi.posterPath?.w500)
                                    is TMDb3.Tv -> TvShow(id = multi.id.toString(), title = multi.name, poster = multi.posterPath?.w500)
                                    else -> null
                                }
                            } ?: emptyList(),
                        )
                    }
                } catch (_: Exception) { }
            }

            val titles = json.optJSONObject("titles")
            val title = titles?.optString("name") ?: json.optString("title") ?: ""
            val images = json.optJSONObject("images")
            val rawPoster = images?.optString("poster") ?: ""
            val imgUrl = rawPoster.toUri().getQueryParameter("url") ?: rawPoster
            val poster = if (imgUrl.startsWith("http")) imgUrl 
                         else if (imgUrl.isNotEmpty()) "${baseUrl.trimEnd('/')}/${imgUrl.trimStart('/')}" 
                         else null

            return Movie(
                id = id,
                title = title,
                overview = json.optString("overview", ""),
                released = json.optString("releaseDate", "").take(10),
                rating = json.optJSONObject("rate")?.optDouble("average", 0.0) ?: 0.0,
                poster = poster
            )
        }

        return Movie(
            id = id,
            title = document.selectFirst("h1")?.text() ?: "",
            overview = document.select(".Description p").text(),
            poster = document.select(".Image img").attr("src")
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        val document = getDocument("${baseUrl.trimEnd('/')}/serie/$id")
        val jsonData = document.selectFirst("script#__NEXT_DATA__")?.data() ?: return TvShow(id = id, title = "Unknown")
        val json = JSONObject(jsonData).getJSONObject("props").getJSONObject("pageProps").getJSONObject("thisSerie")
        val tmdbId = json.optString("TMDbId")

        val seasonsJson = json.optJSONArray("seasons") ?: JSONArray()
        val seasons = (0 until seasonsJson.length()).map { i ->
            val num = seasonsJson.getJSONObject(i).getInt("number")
            Season(id = "$id/temporada/$num", number = num, title = "Temporada $num")
        }

        if (tmdbId.isNotEmpty()) {
            try {
                return TMDb3.TvSeries.details(
                    seriesId = tmdbId.toInt(),
                    appendToResponse = listOf(
                        TMDb3.Params.AppendToResponse.Tv.CREDITS,
                        TMDb3.Params.AppendToResponse.Tv.RECOMMENDATIONS,
                        TMDb3.Params.AppendToResponse.Tv.VIDEOS,
                    ),
                    language = language
                ).let { tmdbTv ->
                    TvShow(
                        id = id,
                        title = tmdbTv.name,
                        overview = tmdbTv.overview,
                        released = tmdbTv.firstAirDate,
                        rating = tmdbTv.voteAverage.toDouble(),
                        poster = tmdbTv.posterPath?.original,
                        banner = tmdbTv.backdropPath?.original,
                        seasons = seasons.map { season ->
                            val tmdbSeason = tmdbTv.seasons.find { it.seasonNumber == season.number }
                            season.copy(title = tmdbSeason?.name ?: season.title, poster = tmdbSeason?.posterPath?.w500 ?: season.poster)
                        },
                        genres = tmdbTv.genres.map { Genre(it.id.toString(), it.name) },
                        cast = tmdbTv.credits?.cast?.map { People(it.id.toString(), it.name, it.profilePath?.w500) } ?: emptyList()
                    )
                }
            } catch (_: Exception) { }
        }

        val titles = json.optJSONObject("titles")
        val title = titles?.optString("name") ?: json.optString("title") ?: ""
        val images = json.optJSONObject("images")
        val rawPoster = images?.optString("poster") ?: ""
        val imgUrl = rawPoster.toUri().getQueryParameter("url") ?: rawPoster
        val poster = if (imgUrl.startsWith("http")) imgUrl 
                     else if (imgUrl.isNotEmpty()) "${baseUrl.trimEnd('/')}/${imgUrl.trimStart('/')}"
                     else null

        return TvShow(
            id = id,
            title = title,
            overview = json.optString("overview", ""),
            released = json.optString("releaseDate", "").take(10),
            rating = json.optJSONObject("rate")?.optDouble("average", 0.0) ?: 0.0,
            poster = poster,
            seasons = seasons
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val seriesPath = seasonId.substringBefore("/temporada")
        val seasonNum = seasonId.substringAfterLast("/")
        
        val document = getDocument("$baseUrl/serie/$seriesPath")
        val jsonData = document.selectFirst("script#__NEXT_DATA__")?.data() ?: return emptyList()
        val json = JSONObject(jsonData).getJSONObject("props").getJSONObject("pageProps")
        
        val serieObj = if (json.has("thisSerie")) json.getJSONObject("thisSerie") else json.optJSONObject("serie")
        val seasonsArray = serieObj?.optJSONArray("seasons") ?: return emptyList()

        for (i in 0 until seasonsArray.length()) {
            val seasonObj = seasonsArray.getJSONObject(i)
            if (seasonObj.optInt("number").toString() != seasonNum) continue

            val episodesArray = seasonObj.optJSONArray("episodes") ?: return emptyList()
            return (0 until episodesArray.length()).map { idx ->
                val ep = episodesArray.getJSONObject(idx)
                val slug = ep.optJSONObject("url")?.optString("slug") ?: ""
                
                val rawPoster = ep.optString("image")
                val imgUrl = rawPoster.toUri().getQueryParameter("url") ?: rawPoster
                val poster = if (imgUrl.startsWith("http")) imgUrl 
                             else if (imgUrl.isNotEmpty()) "$baseUrl${imgUrl.trimStart('/')}" 
                             else null

                Episode(
                    id = slug.removePrefix("series/")
                        .replace("/seasons/", "/temporada/")
                        .replace("/episodes/", "/episodio/"),
                    number = ep.optInt("number"),
                    title = ep.optString("title"),
                    poster = poster,
                    released = ep.optString("releaseDate", "").take(10)
                )
            }
        }
        return emptyList()
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val url = if (page == 1) "${baseUrl.trimEnd('/')}/genero/$id" else "${baseUrl.trimEnd('/')}/genero/$id/page/$page"
        val document = getDocument(url)
        val shows = document.select("li.TPostMv, article.TPost, div.TPost, div.col article").mapNotNull { element ->
            val linkElement = element.selectFirst("a") ?: return@mapNotNull null
            val href = linkElement.attr("href")
            
            val title = element.selectFirst(".Title")?.text() 
                ?: element.selectFirst("h3")?.text() 
                ?: return@mapNotNull null
            
            val rawImgUrl = element.selectFirst("img")?.attr("src") ?: ""
            val imgUrl = rawImgUrl.toUri().getQueryParameter("url") ?: rawImgUrl
            val poster = if (imgUrl.startsWith("http")) imgUrl 
                         else if (imgUrl.isNotEmpty()) "${baseUrl.trimEnd('/')}/${imgUrl.trimStart('/')}" 
                         else null

            val showId = href.removePrefix("/pelicula/").removePrefix("/serie/")

            if (href.contains("/pelicula/")) {
                Movie(id = showId, title = title, poster = poster)
            } else if (href.contains("/serie/")) {
                TvShow(id = showId, title = title, poster = poster)
            } else null
        }.distinctBy { if (it is Movie) it.id else (it as TvShow).id }
        
        return Genre(id = id, name = id.replaceFirstChar { it.uppercase() }, shows = shows)
    }

    override suspend fun getPeople(id: String, page: Int): People {
        throw Exception("Function not available for Poseidonhd2")
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val url = when (videoType) {
            is Video.Type.Movie -> "${baseUrl.trimEnd('/')}/pelicula/$id"
            is Video.Type.Episode -> {
                if (id.contains("/episodio/")) {
                    "${baseUrl.trimEnd('/')}/serie/$id"
                } else {
                    "${baseUrl.trimEnd('/')}/serie/$id/temporada/${videoType.season.number}/episodio/${videoType.number}"
                }
            }
        }
        val document = getDocument(url)
        val jsonData = document.selectFirst("script#__NEXT_DATA__")?.data() ?: return emptyList()
        val json = JSONObject(jsonData).getJSONObject("props").getJSONObject("pageProps")
        
        val videoObj = when (videoType) {
            is Video.Type.Movie -> json.optJSONObject("thisMovie")?.optJSONObject("videos")
            is Video.Type.Episode -> {
                (json.optJSONObject("episode") ?: json.optJSONObject("thisEpisode"))?.optJSONObject("videos")
            }
        } ?: return emptyList()

        val servers = mutableListOf<Video.Server>()
        val languages = mapOf("latino" to "[LAT]", "spanish" to "[CAST]", "english" to "[SUB]")
        
        coroutineScope {
            val deferredServers = mutableListOf<Deferred<Video.Server?>>()
            for ((langKey, tag) in languages) {
                val array = videoObj.optJSONArray(langKey) ?: continue
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val cyberlocker = obj.optString("cyberlocker")
                    val embedUrl = obj.optString("result")
                    
                    deferredServers.add(async {
                        try {
                            // Resolve the bridge URL to the real embed URL
                            val realUrl = resolvePlayerUrl(embedUrl) ?: embedUrl
                            if (realUrl.isEmpty()) return@async null
                            
                            Video.Server(
                                id = realUrl,
                                name = "$cyberlocker $tag".trim(),
                                src = realUrl
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error resolving player URL: $embedUrl", e)
                            null
                        }
                    })
                }
            }
            servers.addAll(deferredServers.awaitAll().filterNotNull())
        }
        return servers
    }

    private suspend fun resolvePlayerUrl(playerUrl: String): String? {
        val currentHost = UserPreferences.poseidonDomain.removePrefix("www.")
        if (!playerUrl.contains("player.$currentHost")) return playerUrl
        
        // Try resolving with OkHttp first (fast)
        try {
            val client = NetworkClient.default.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
            
            val request = okhttp3.Request.Builder()
                .url(playerUrl)
                .header("Referer", baseUrl)
                .header("User-Agent", NetworkClient.USER_AGENT)
                .build()
            
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val html = response.body?.string() ?: ""
            
            if (html.contains("cf-browser-verification") || html.contains("Just a moment...")) {
                // Cloudflare detected. We could use WebView here, but not in parallel for 18 servers.
                // We'll return null and let the caller decide (or just return the original URL)
                return null
            }

            val doc = Jsoup.parse(html)
            val iframe = doc.selectFirst("iframe")?.attr("src")
            if (!iframe.isNullOrEmpty() && iframe.startsWith("http")) {
                return iframe
            } else {
                val scriptContent = doc.select("script").firstOrNull { it.data().contains("var url =") }?.data()
                val regex = Regex("var url\\s*=\\s*['\"](https?://[^'\"]+)['\"]")
                val match = regex.find(scriptContent ?: "")
                val foundUrl = match?.groupValues?.get(1)
                if (!foundUrl.isNullOrEmpty()) return foundUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "OkHttp resolution failed for $playerUrl")
        }
        
        return null
    }

    override suspend fun getVideo(server: Video.Server): Video {
        var finalUrl = server.src

        if (finalUrl.contains("voe.sx") || server.name.contains("VOE", ignoreCase = true)) {
            val path = finalUrl.toUri().path?.trimStart('/') ?: ""
            if (!path.startsWith("e/")) {
                finalUrl = "https://voe.sx/e/$path"
            }
        }

        return Extractor.extract(finalUrl, server)
    }
}
