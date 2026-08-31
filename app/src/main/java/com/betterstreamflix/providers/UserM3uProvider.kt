package com.betterstreamflix.providers

import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.models.Category
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Genre
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.People
import com.betterstreamflix.models.Season
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.models.Video
import com.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * User-supplied M3U / IPTV playlist provider (Megaplan P3).
 *
 * Reads [UserPreferences.userM3uUrl]. Not registered in [Provider.providers] until
 * the URL is configured and smoke-tested — instantiate manually or via settings later.
 */
object UserM3uProvider : Provider, IptvProvider {

    override val baseUrl: String
        get() = UserPreferences.userM3uUrl.ifBlank { "https://example.invalid/playlist.m3u" }
    override val name: String = "User M3U"
    override val logo: String = "https://raw.githubusercontent.com/media-icons/iptv/main/icons/iptv.png"
    override val language: String = "en"

    private val client by lazy { OkHttpClient() }

    data class M3uEntry(val name: String, val logo: String?, val url: String)

    private suspend fun loadEntries(): List<M3uEntry> = withContext(Dispatchers.IO) {
        val url = UserPreferences.userM3uUrl
        if (url.isBlank()) return@withContext emptyList()
        val body = client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string().orEmpty() }
        parseM3u(body)
    }

    fun parseM3u(body: String): List<M3uEntry> {
        val entries = mutableListOf<M3uEntry>()
        var pendingName = "Channel"
        var pendingLogo: String? = null
        body.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingLogo = Regex("tvg-logo=\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)
                    pendingName = line.substringAfter(",").trim().ifBlank { "Channel" }
                }
                line.isNotBlank() && !line.startsWith("#") -> {
                    entries += M3uEntry(pendingName, pendingLogo, line)
                    pendingName = "Channel"
                    pendingLogo = null
                }
            }
        }
        return entries
    }

    override suspend fun getHome(): List<Category> {
        val shows = loadEntries().map {
            TvShow(id = it.url, title = it.name, poster = it.logo)
        }
        return listOf(Category(name = name, list = shows))
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (page > 1) return emptyList()
        val q = query.trim().lowercase()
        return loadEntries()
            .filter { it.name.lowercase().contains(q) }
            .map { TvShow(id = it.url, title = it.name, poster = it.logo) }
    }

    override suspend fun getMovies(page: Int): List<Movie> = emptyList()
    override suspend fun getTvShows(page: Int): List<TvShow> =
        loadEntries().map { TvShow(id = it.url, title = it.name, poster = it.logo) }

    override suspend fun getMovie(id: String): Movie = Movie(id = id, title = "")
    override suspend fun getTvShow(id: String): TvShow {
        val entry = loadEntries().find { it.url == id }
        return TvShow(
            id = id,
            title = entry?.name ?: "Channel",
            poster = entry?.logo,
            seasons = listOf(Season(id = id, number = 1, title = "Live")),
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> =
        listOf(Episode(id = seasonId, number = 1, title = "Live"))

    override suspend fun getGenre(id: String, page: Int): Genre =
        Genre(id = id, name = id, shows = emptyList())

    override suspend fun getPeople(id: String, page: Int): People =
        People(id = id, name = "", filmography = emptyList())

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> =
        listOf(Video.Server(id = id, name = "M3U", src = id))

    override suspend fun getVideo(server: Video.Server): Video =
        Video(source = server.src.ifBlank { server.id })
}
