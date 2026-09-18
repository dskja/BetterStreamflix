package com.dskja.betterstreamflix.platform.plex

import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Genre
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.People
import com.dskja.betterstreamflix.models.Season
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.utils.UserPreferences
import org.json.JSONObject

object PlexProvider : Provider {
    override val name = "Plex"
    override val logo = "https://www.plex.tv/wp-content/themes/plex/assets/img/plex-logo.svg"
    override val language = "en"
    override val baseUrl: String
        get() = UserPreferences.plexBaseUrl.trim().trimEnd('/')

    private val api = PlexApi()

    private fun requireConfigured() {
        check(api.configured()) {
            "Configure Plex URL and token in Settings → Platform"
        }
    }

    private fun toMovie(meta: JSONObject): Movie {
        val key = meta.optString("ratingKey")
        return Movie(
            id = key,
            title = meta.optString("title"),
            overview = meta.optString("summary").ifBlank { null },
            released = meta.optString("originallyAvailableAt").ifBlank { null },
            poster = api.thumbUrl(meta.optString("thumb").ifBlank { null }),
            banner = api.thumbUrl(meta.optString("art").ifBlank { null }),
            rating = meta.optDouble("rating").takeIf { !it.isNaN() && it > 0 },
        )
    }

    private fun toTvShow(meta: JSONObject): TvShow {
        val key = meta.optString("ratingKey")
        return TvShow(
            id = key,
            title = meta.optString("title"),
            overview = meta.optString("summary").ifBlank { null },
            released = meta.optString("originallyAvailableAt").ifBlank { null },
            poster = api.thumbUrl(meta.optString("thumb").ifBlank { null }),
            banner = api.thumbUrl(meta.optString("art").ifBlank { null }),
            rating = meta.optDouble("rating").takeIf { !it.isNaN() && it > 0 },
        )
    }

    override suspend fun getHome(): List<Category> {
        requireConfigured()
        val sections = api.librarySections()
        val categories = mutableListOf<Category>()
        for (i in 0 until minOf(sections.length(), 4)) {
            val section = sections.getJSONObject(i)
            val key = section.optString("key")
            val title = section.optString("title").ifBlank { "Library" }
            val type = section.optString("type")
            val items = api.sectionItems(key, size = 24)
            val list = buildList<AppAdapter.Item> {
                for (j in 0 until items.length()) {
                    val meta = items.getJSONObject(j)
                    when (type) {
                        "movie" -> add(toMovie(meta))
                        "show" -> add(toTvShow(meta))
                        else -> when (meta.optString("type")) {
                            "movie" -> add(toMovie(meta))
                            "show" -> add(toTvShow(meta))
                        }
                    }
                }
            }
            if (list.isNotEmpty()) categories.add(Category(title, list))
        }
        return categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank() || page > 1) return emptyList()
        requireConfigured()
        val hubs = api.search(query)
        return buildList {
            for (i in 0 until hubs.length()) {
                val hub = hubs.getJSONObject(i)
                val metas = hub.optJSONArray("Metadata") ?: continue
                for (j in 0 until metas.length()) {
                    val meta = metas.getJSONObject(j)
                    when (meta.optString("type")) {
                        "movie" -> add(toMovie(meta))
                        "show" -> add(toTvShow(meta))
                    }
                }
            }
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        requireConfigured()
        if (page > 1) return emptyList()
        val sections = api.librarySections()
        val movieSection = (0 until sections.length())
            .map { sections.getJSONObject(it) }
            .firstOrNull { it.optString("type") == "movie" }
            ?: return emptyList()
        val items = api.sectionItems(movieSection.optString("key"))
        return buildList {
            for (i in 0 until items.length()) add(toMovie(items.getJSONObject(i)))
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        requireConfigured()
        if (page > 1) return emptyList()
        val sections = api.librarySections()
        val showSection = (0 until sections.length())
            .map { sections.getJSONObject(it) }
            .firstOrNull { it.optString("type") == "show" }
            ?: return emptyList()
        val items = api.sectionItems(showSection.optString("key"))
        return buildList {
            for (i in 0 until items.length()) add(toTvShow(items.getJSONObject(i)))
        }
    }

    override suspend fun getMovie(id: String): Movie {
        requireConfigured()
        return toMovie(api.metadata(id))
    }

    override suspend fun getTvShow(id: String): TvShow {
        requireConfigured()
        val show = toTvShow(api.metadata(id))
        val children = api.children(id)
        val seasons = buildList {
            for (i in 0 until children.length()) {
                val s = children.getJSONObject(i)
                if (s.optString("type") != "season") continue
                add(
                    Season(
                        id = s.optString("ratingKey"),
                        number = s.optInt("index", i + 1),
                        title = s.optString("title").ifBlank { null },
                        poster = api.thumbUrl(s.optString("thumb").ifBlank { null }),
                    ),
                )
            }
        }
        return show.copy(seasons = seasons)
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        requireConfigured()
        val children = api.children(seasonId)
        return buildList {
            for (i in 0 until children.length()) {
                val e = children.getJSONObject(i)
                if (e.optString("type") != "episode") continue
                add(
                    Episode(
                        id = e.optString("ratingKey"),
                        number = e.optInt("index", i + 1),
                        title = e.optString("title").ifBlank { null },
                        poster = api.thumbUrl(e.optString("thumb").ifBlank { null }),
                        overview = e.optString("summary").ifBlank { null },
                    ),
                )
            }
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre = Genre(id = id, name = id)

    override suspend fun getPeople(id: String, page: Int): People = People(id = id, name = id)

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        requireConfigured()
        return listOf(Video.Server(id = id, name = "Plex Direct", src = id))
    }

    override suspend fun getVideo(server: Video.Server): Video {
        requireConfigured()
        val meta = api.metadata(server.src.ifBlank { server.id })
        val part = meta.optJSONArray("Media")
            ?.optJSONObject(0)
            ?.optJSONArray("Part")
            ?.optJSONObject(0)
            ?: error("Plex media part missing")
        val key = part.optString("key")
        if (key.isBlank()) error("Plex stream key missing")
        return Video(
            source = api.streamUrl(key),
            headers = mapOf("X-Plex-Token" to UserPreferences.plexToken),
        )
    }
}
