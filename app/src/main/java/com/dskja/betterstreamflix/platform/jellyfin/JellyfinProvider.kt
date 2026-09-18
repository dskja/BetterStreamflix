package com.dskja.betterstreamflix.platform.jellyfin

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

object JellyfinProvider : Provider {
    override val name = "Jellyfin"
    override val logo =
        "https://raw.githubusercontent.com/jellyfin/jellyfin-ux/master/branding/SVG/icon-transparent.svg"
    override val language = "en"
    override val baseUrl: String
        get() = UserPreferences.jellyfinBaseUrl.trim().trimEnd('/')

    private val api = JellyfinApi()

    private fun requireConfigured() {
        check(api.configured()) {
            "Configure Jellyfin URL, user id and access token in Settings → Platform"
        }
    }

    private fun imageUrl(itemId: String, tag: String?, type: String = "Primary"): String? {
        if (tag.isNullOrBlank() || baseUrl.isBlank()) return null
        return "$baseUrl/Items/$itemId/Images/$type?tag=$tag&quality=90"
    }

    private fun toMovie(item: JSONObject): Movie {
        val id = item.optString("Id")
        val imageTags = item.optJSONObject("ImageTags")
        val primary = imageTags?.optString("Primary")
        return Movie(
            id = id,
            title = item.optString("Name"),
            overview = item.optString("Overview").ifBlank { null },
            released = item.optString("PremiereDate").take(10).ifBlank { null },
            poster = imageUrl(id, primary),
            banner = imageUrl(id, item.optJSONObject("BackdropImageTags")?.optString("0"), "Backdrop")
                ?: imageUrl(id, primary),
            rating = item.optDouble("CommunityRating").takeIf { !it.isNaN() && it > 0 },
        )
    }

    private fun toTvShow(item: JSONObject): TvShow {
        val id = item.optString("Id")
        val imageTags = item.optJSONObject("ImageTags")
        val primary = imageTags?.optString("Primary")
        return TvShow(
            id = id,
            title = item.optString("Name"),
            overview = item.optString("Overview").ifBlank { null },
            released = item.optString("PremiereDate").take(10).ifBlank { null },
            poster = imageUrl(id, primary),
            banner = imageUrl(id, primary),
            rating = item.optDouble("CommunityRating").takeIf { !it.isNaN() && it > 0 },
        )
    }

    override suspend fun getHome(): List<Category> {
        requireConfigured()
        val resume = buildList {
            val items = api.resumeItems()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                when (item.optString("Type")) {
                    "Movie" -> add(toMovie(item))
                    "Episode" -> {
                        // Surface episode as part of continue via movie-like card of series if present
                        val seriesId = item.optString("SeriesId")
                        if (seriesId.isNotBlank()) {
                            add(
                                TvShow(
                                    id = seriesId,
                                    title = item.optString("SeriesName").ifBlank { item.optString("Name") },
                                    poster = imageUrl(
                                        seriesId,
                                        item.optJSONObject("ImageTags")?.optString("Primary"),
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }.distinctBy {
            when (it) {
                is Movie -> it.id
                is TvShow -> it.id
                else -> it.hashCode().toString()
            }
        }
        val latest = buildList {
            val items = api.latestMovies()
            for (i in 0 until items.length()) {
                add(toMovie(items.getJSONObject(i)))
            }
        }
        return buildList {
            if (resume.isNotEmpty()) add(Category(Category.CONTINUE_WATCHING, resume))
            if (latest.isNotEmpty()) add(Category("Latest on Jellyfin", latest))
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) return emptyList()
        requireConfigured()
        if (page > 1) return emptyList()
        val items = api.search(query)
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                when (item.optString("Type")) {
                    "Movie" -> add(toMovie(item))
                    "Series" -> add(toTvShow(item))
                }
            }
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        requireConfigured()
        if (page > 1) return emptyList()
        val items = api.latestMovies(40)
        return buildList {
            for (i in 0 until items.length()) add(toMovie(items.getJSONObject(i)))
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> = emptyList()

    override suspend fun getMovie(id: String): Movie {
        requireConfigured()
        return toMovie(api.item(id))
    }

    override suspend fun getTvShow(id: String): TvShow {
        requireConfigured()
        val show = toTvShow(api.item(id))
        val seasonsJson = api.seasons(id)
        val seasons = buildList {
            for (i in 0 until seasonsJson.length()) {
                val s = seasonsJson.getJSONObject(i)
                add(
                    Season(
                        id = "${id}|${s.optString("Id")}",
                        number = s.optInt("IndexNumber", i + 1),
                        title = s.optString("Name").ifBlank { null },
                    ),
                )
            }
        }
        return show.copy(seasons = seasons)
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        requireConfigured()
        // seasonId encodes "seriesId|seasonId" when needed; plain season id also works via parent.
        val parts = seasonId.split("|", limit = 2)
        val seriesId = parts.getOrNull(0).orEmpty()
        val realSeason = parts.getOrNull(1) ?: seasonId
        val items = if (seriesId.isNotBlank() && parts.size == 2) {
            api.episodesForSeries(seriesId, realSeason)
        } else {
            // Fallback: treat seasonId as series and load season 1 via seasons list
            val seasons = api.seasons(seasonId)
            if (seasons.length() == 0) return emptyList()
            val first = seasons.getJSONObject(0)
            api.episodesForSeries(seasonId, first.optString("Id"))
        }
        return buildList {
            for (i in 0 until items.length()) {
                val e = items.getJSONObject(i)
                val eid = e.optString("Id")
                add(
                    Episode(
                        id = eid,
                        number = e.optInt("IndexNumber", i + 1),
                        title = e.optString("Name").ifBlank { null },
                        poster = imageUrl(eid, e.optJSONObject("ImageTags")?.optString("Primary")),
                        overview = e.optString("Overview").ifBlank { null },
                    ),
                )
            }
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre =
        Genre(id = id, name = id)

    override suspend fun getPeople(id: String, page: Int): People =
        People(id = id, name = id)

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        requireConfigured()
        return listOf(Video.Server(id = id, name = "Jellyfin Direct", src = id))
    }

    override suspend fun getVideo(server: Video.Server): Video {
        requireConfigured()
        val url = api.playbackUrl(server.src.ifBlank { server.id })
            ?: error("Jellyfin playback URL unavailable")
        return Video(
            source = url,
            headers = mapOf(
                "X-Emby-Token" to UserPreferences.jellyfinAccessToken,
            ),
        )
    }
}
