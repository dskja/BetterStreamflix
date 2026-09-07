package com.betterstreamflix.providers

import android.util.Log
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.extractors.Extractor
import com.betterstreamflix.models.*
import com.betterstreamflix.utils.NetworkClient
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONArray

object KinogerProvider : Provider {

    override val name = "Kinoger"
    override val baseUrl = "https://kinoger.to"
    override val language = "de"
    override val logo = "https://www.google.com/s2/favicons?domain=kinoger.to&sz=256"

    private const val TAG = "KinogerProvider"
    private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val mainPageCategories = listOf(
        "" to "Alle Filme",
        "stream/action" to "Action",
        "stream/fantasy" to "Fantasy",
        "stream/drama" to "Drama",
        "stream/mystery" to "Mystery",
        "stream/romance" to "Romance",
        "stream/animation" to "Animation",
        "stream/horror" to "Horror",
        "stream/familie" to "Familie",
        "stream/komdie" to "Komödie",
    )

    private suspend fun getDocument(url: String): Document = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Referer", baseUrl)
            .build()

        val body = try {
            NetworkClient.default.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() ?: "" else ""
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "HTTP error for $url", e)
            ""
        }

        Jsoup.parse(body).apply { setBaseUri(baseUrl) }
    }

    private fun fixUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> baseUrl + url
            url.startsWith("http", ignoreCase = true) -> url
            else -> "$baseUrl/$url"
        }
    }

    private fun extractServerName(url: String): String {
        return runCatching { java.net.URL(url).host }.getOrNull()?.removePrefix("www.")
            ?: "Kinoger"
    }

    private fun Element.getImageAttr(): String? {
        val attr = when {
            hasAttr("data-src") -> attr("data-src")
            hasAttr("data-lazy-src") -> attr("data-lazy-src")
            hasAttr("srcset") -> attr("srcset").substringBefore(" ")
            else -> attr("src")
        }
        return if (attr.isNotBlank()) attr else null
    }

    private fun getProperLink(uri: String): String {
        return if (uri.contains("-episode-")) {
            val match = Regex("$baseUrl/(.+)-ep.+").find(uri)
            if (match != null) {
                "$baseUrl/series/${match.groupValues[1]}"
            } else {
                uri
            }
        } else {
            uri
        }
    }

    private fun Element.toAppItem(): Show? {
        val href = getProperLink(this.selectFirst("a")?.attr("href") ?: return null)
        val title = this.selectFirst("a")?.text()
            ?: this.selectFirst("img")?.attr("alt")
            ?: this.selectFirst("a")?.attr("title")
            ?: return null
        val poster = fixUrl(
            (this.selectFirst("div.content_text img")
                ?: this.nextElementSibling()?.selectFirst("div.content_text img")
                ?: this.selectFirst("img"))?.getImageAttr()
        ) ?: ""

        val id = getProperLink(href)
        val isMovie = title.contains("\\((\\d{4})\\)".toRegex())

        return if (isMovie) {
            Movie(id = id, title = title, poster = poster).apply {
                itemType = AppAdapter.Type.MOVIE_MOBILE_ITEM
            }
        } else {
            TvShow(id = id, title = title, poster = poster).apply {
                itemType = AppAdapter.Type.TV_SHOW_MOBILE_ITEM
            }
        }
    }

    private fun parseShorts(doc: Document): List<Show> {
        return doc.select("div#dle-content div.short").mapNotNull {
            runCatching { it.toAppItem() }.getOrNull()
        }
    }

    private fun parseSearchResults(doc: Document): List<Show> {
        return doc.select("div#dle-content div.titlecontrol").mapNotNull {
            runCatching { it.toAppItem() }.getOrNull()
        }
    }

    override suspend fun getHome(): List<Category> {
        return try {
            val doc = getDocument("$baseUrl/page/1")
            val shows = parseShorts(doc)
            val (movies, tv) = shows.partition { it is Movie }
            buildList {
                if (movies.isNotEmpty()) {
                    add(Category("Beliebte Filme", movies).apply {
                        itemType = AppAdapter.Type.CATEGORY_MOBILE_ITEM
                    })
                }
                if (tv.isNotEmpty()) {
                    add(Category("Beliebte Serien", tv).apply {
                        itemType = AppAdapter.Type.CATEGORY_MOBILE_ITEM
                    })
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Error loading home", e)
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> = try {
        parseShorts(getDocument("$baseUrl/page/$page")).filterIsInstance<Movie>()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        emptyList()
    }

    override suspend fun getTvShows(page: Int): List<TvShow> = try {
        parseShorts(getDocument("$baseUrl/page/$page")).filterIsInstance<TvShow>()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        emptyList()
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            return mainPageCategories.map { (path, name) ->
                Genre(id = if (path.isEmpty()) "" else "$path/", name = name).apply {
                    itemType = AppAdapter.Type.GENRE_GRID_MOBILE_ITEM
                }
            }
        }
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$baseUrl/?do=search&subaction=search&titleonly=3&story=$encoded&x=0&y=0&submit=submit"
            parseSearchResults(getDocument(url))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptyList()
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val path = id.removePrefix("/").removeSuffix("/")
        val url = if (path.isEmpty()) {
            "$baseUrl/page/$page"
        } else {
            "$baseUrl/$path/page/$page"
        }
        return try {
            val shows = parseShorts(getDocument(url))
            val name = mainPageCategories.find { it.first == path || it.first == path.removeSuffix("/") }?.second
                ?: path.replace("stream/", "").replaceFirstChar { it.uppercase() }
            Genre(id = id, name = name, shows = shows).apply {
                itemType = AppAdapter.Type.GENRE_GRID_MOBILE_ITEM
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Genre(id = id, name = "Fehler").apply {
                itemType = AppAdapter.Type.GENRE_GRID_MOBILE_ITEM
            }
        }
    }

    private data class KinogerDetails(
        val title: String,
        val poster: String?,
        val overview: String?,
        val year: Int?,
        val tags: List<String>,
        val recommendations: List<Show>,
        val isMovie: Boolean,
        val seasons: List<Season>,
    )

    private suspend fun loadDetails(url: String): KinogerDetails? {
        Log.d(TAG, "loadDetails called for $url", Throwable("call-trace"))
        val doc = try {
            getDocument(url)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return null
        }

        val title = doc.selectFirst("h1#news-title")?.text()?.trim() ?: ""
        val poster = fixUrl(doc.selectFirst("div.images-border img")?.getImageAttr())
        val overview = doc.select("div.images-border").text()?.trim()
        val year = "\\((\\d{4})\\)".toRegex().find(title)?.groupValues?.get(1)?.toIntOrNull()
        val tags = doc.select("li.category a").map { it.text().trim() }
        val recommendations = doc.select("ul.ul_related li").mapNotNull {
            runCatching { it.toAppItem() }.getOrNull()
        }

        val allScripts = doc.select("script").joinToString("\n") { it.html() }
        val showRegex = """(\w+)\s*\.\s*show\s*\(([\s\S]*?)\);""".toRegex()

        data class Candidate(
            val provider: String,
            val isMovie: Boolean,
            val seasons: List<Season>,
        )

        val candidates = mutableListOf<Candidate>()
        for (match in showRegex.findAll(allScripts)) {
            val providerName = match.groupValues[1]
            val argsText = match.groupValues[2].replace("'", "\"").trim()
            val json = runCatching { JSONArray("[$argsText]") }.getOrNull() ?: continue
            if (json.length() < 2) continue

            val listArg = json.optJSONArray(1) ?: continue
            val type = if (json.length() > 2) json.optDouble(2, -1.0) else null
            val isMovie = type == 0.2

            val seasons = if (isMovie) {
                val urls = mutableListOf<String>()
                for (i in 0 until listArg.length()) {
                    val arr = listArg.optJSONArray(i) ?: continue
                    for (j in 0 until arr.length()) {
                        val raw = arr.optString(j, "").trim()
                        val fixed = fixUrl(raw) ?: continue
                        if (fixed.startsWith("http")) urls.add(fixed)
                    }
                }
                if (urls.isNotEmpty()) {
                    listOf(
                        Season(
                            id = "$url#1",
                            number = 1,
                            title = "Film",
                            episodes = urls.mapIndexed { index, iframe ->
                                Episode(
                                    id = iframe,
                                    number = index + 1,
                                    title = "Episode ${index + 1}",
                                ).apply {
                                    itemType = AppAdapter.Type.EPISODE_MOBILE_ITEM
                                }
                            },
                        ).apply { itemType = AppAdapter.Type.SEASON_MOBILE_ITEM }
                    )
                } else {
                    emptyList()
                }
            } else {
                val seasonsList = mutableListOf<Season>()
                for (seasonIndex in 0 until listArg.length()) {
                    val seasonArray = listArg.optJSONArray(seasonIndex) ?: continue
                    val episodes = mutableListOf<Episode>()
                    for (episodeIndex in 0 until seasonArray.length()) {
                        val raw = seasonArray.optString(episodeIndex, "").trim()
                        val iframe = fixUrl(raw) ?: continue
                        if (!iframe.startsWith("http")) continue
                        episodes.add(
                            Episode(
                                id = iframe,
                                number = episodeIndex + 1,
                                title = "Episode ${episodeIndex + 1}",
                            ).apply {
                                itemType = AppAdapter.Type.EPISODE_MOBILE_ITEM
                            }
                        )
                    }
                    if (episodes.isNotEmpty()) {
                        seasonsList.add(
                            Season(
                                id = "$url#${seasonIndex + 1}",
                                number = seasonIndex + 1,
                                title = "Staffel ${seasonIndex + 1}",
                                episodes = episodes,
                            ).apply {
                                itemType = AppAdapter.Type.SEASON_MOBILE_ITEM
                            }
                        )
                    }
                }
                seasonsList
            }

            if (seasons.isNotEmpty()) {
                candidates.add(Candidate(providerName, isMovie, seasons))
            }
        }

        fun Candidate.hasPreferredHost(): Boolean {
            return seasons.any { season ->
                season.episodes.any { ep ->
                    ep.id.contains("kinoger.pw") || ep.id.contains("mountainpages.fit")
                }
            }
        }

        fun Candidate.hasKinogerHost(): Boolean {
            return seasons.any { season ->
                season.episodes.any { ep ->
                    ep.id.contains("kinoger.")
                }
            }
        }

        val selected = candidates.firstOrNull { it.hasPreferredHost() }
            ?: candidates.firstOrNull { it.provider == "fsst" }
            ?: candidates.firstOrNull { it.hasKinogerHost() }
            ?: candidates.firstOrNull()
            ?: run {
                Log.d(TAG, "No show() candidates found in $url")
                return null
            }

        return KinogerDetails(
            title = title,
            poster = poster,
            overview = overview,
            year = year,
            tags = tags,
            recommendations = recommendations,
            isMovie = selected.isMovie,
            seasons = selected.seasons,
        )
    }

    override suspend fun getMovie(id: String): Movie {
        Log.d(TAG, "getMovie called for $id", Throwable("call-trace"))
        val details = loadDetails(id) ?: return Movie(id = id, title = "")
        return Movie(
            id = id,
            title = details.title,
            overview = details.overview,
            poster = details.poster,
            released = details.year?.toString(),
            rating = null,
            genres = details.tags.map { Genre(id = it, name = it).apply { itemType = AppAdapter.Type.GENRE_GRID_MOBILE_ITEM } },
            recommendations = details.recommendations,
        ).apply {
            itemType = AppAdapter.Type.MOVIE_MOBILE
        }
    }

    override suspend fun getTvShow(id: String): TvShow {
        Log.d(TAG, "getTvShow called for $id", Throwable("call-trace"))
        val details = loadDetails(id) ?: return TvShow(id = id, title = "")
        val tvShow = TvShow(
            id = id,
            title = details.title,
            overview = details.overview,
            poster = details.poster,
            released = details.year?.toString(),
            rating = null,
            genres = details.tags.map { Genre(id = it, name = it).apply { itemType = AppAdapter.Type.GENRE_GRID_MOBILE_ITEM } },
            recommendations = details.recommendations,
            seasons = details.seasons,
        ).apply {
            itemType = AppAdapter.Type.TV_SHOW_MOBILE
        }
        tvShow.seasons.forEach { season ->
            season.tvShow = tvShow
            season.itemType = AppAdapter.Type.SEASON_MOBILE_ITEM
            season.episodes.forEach { episode ->
                episode.tvShow = tvShow
                episode.season = season
                episode.itemType = AppAdapter.Type.EPISODE_MOBILE_ITEM
            }
        }
        return tvShow
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val (url, seasonNumber) = try {
            val parts = seasonId.split("#")
            parts[0] to parts.getOrNull(1)?.toIntOrNull()
        } catch (e: Exception) {
            return emptyList()
        }
        val seasonNum = seasonNumber ?: 1
        val show = getTvShow(url)
        val episodes = show.seasons.find { it.number == seasonNum }?.episodes
            ?: show.seasons.getOrNull(seasonNum - 1)?.episodes
            ?: emptyList()
        return episodes.onEach { it.itemType = AppAdapter.Type.EPISODE_MOBILE_ITEM }
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        Log.d(TAG, "getServers called for $id type=$videoType", Throwable("call-trace"))
        return when (videoType) {
            is Video.Type.Movie -> {
                val details = loadDetails(id) ?: return emptyList()
                val servers = details.seasons.firstOrNull()?.episodes
                    ?.map { episode ->
                        Video.Server(
                            id = episode.id,
                            name = extractServerName(episode.id),
                            src = episode.id,
                        )
                    }
                    ?.sortedWith(
                        compareByDescending {
                            it.id.contains("kinoger.pw") || it.id.contains("mountainpages.fit")
                        }
                    )
                    ?: emptyList()
                servers
            }
            is Video.Type.Episode -> {
                listOf(
                    Video.Server(
                        id = id,
                        name = extractServerName(id),
                        src = id,
                    )
                )
            }
        }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        Log.d(TAG, "getVideo called for ${server.src}", Throwable("call-trace"))
        return Extractor.extract(server.src, server)
    }

    override suspend fun getPeople(id: String, page: Int): People = throw Exception("Not yet implemented")
}
