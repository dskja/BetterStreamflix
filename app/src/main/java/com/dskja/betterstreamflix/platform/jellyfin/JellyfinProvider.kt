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
import com.dskja.betterstreamflix.models.WatchItem
import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.utils.UserPreferences
import org.json.JSONArray
import org.json.JSONObject

object JellyfinProvider : Provider {
    override val name = "Jellyfin"
    override val logo =
        "https://raw.githubusercontent.com/jellyfin/jellyfin-ux/master/branding/SVG/icon-transparent.svg"
    override val language = "en"
    override val baseUrl: String
        get() = UserPreferences.jellyfinBaseUrl.trim().trimEnd('/')

    private val api = JellyfinApi()

    fun isConfigured(): Boolean = api.configured()

    private fun requireConfigured() {
        if (!api.configured()) {
            error("Configure Jellyfin in Settings → Platform (URL, user, token)")
        }
    }

    private fun imageUrl(itemId: String, tag: String?, type: String = "Primary"): String? {
        if (tag.isNullOrBlank() || baseUrl.isBlank()) return null
        return "$baseUrl/Items/$itemId/Images/$type?tag=$tag&quality=90"
    }

    private fun backdropTag(item: JSONObject): String? {
        val tags = item.optJSONArray("BackdropImageTags") ?: return null
        return tags.optString(0).takeIf { it.isNotBlank() }
    }

    private fun providerIds(item: JSONObject): Pair<String?, String?> {
        val ids = item.optJSONObject("ProviderIds") ?: return null to null
        val imdb = ids.optString("Imdb").ifBlank { ids.optString("IMDB") }.ifBlank { null }
        val tmdb = ids.optString("Tmdb").ifBlank { ids.optString("TmdbId") }.ifBlank { null }
        return imdb to tmdb
    }

    private fun applyUserData(item: JSONObject, watchItem: WatchItem) {
        val userData = item.optJSONObject("UserData") ?: return
        val ticks = userData.optLong("PlaybackPositionTicks", 0L)
        val runtimeTicks = item.optLong("RunTimeTicks", 0L)
        if (ticks > 0 && runtimeTicks > 0) {
            watchItem.watchHistory = WatchItem.WatchHistory(
                lastEngagementTimeUtcMillis = System.currentTimeMillis(),
                lastPlaybackPositionMillis = ticks / 10_000L,
                durationMillis = runtimeTicks / 10_000L,
            )
        }
        if (userData.optBoolean("Played")) {
            watchItem.isWatched = true
        }
    }

    private fun toMovie(item: JSONObject): Movie {
        val id = item.optString("Id")
        val imageTags = item.optJSONObject("ImageTags")
        val primary = imageTags?.optString("Primary")
        val (imdb, _) = providerIds(item)
        return Movie(
            id = id,
            title = item.optString("Name"),
            overview = item.optString("Overview").ifBlank { null },
            released = item.optString("PremiereDate").take(10).ifBlank { null },
            poster = imageUrl(id, primary),
            banner = imageUrl(id, backdropTag(item), "Backdrop") ?: imageUrl(id, primary),
            rating = item.optDouble("CommunityRating").takeIf { !it.isNaN() && it > 0 },
            imdbId = imdb,
            providerName = name,
        ).also { applyUserData(item, it) }
    }

    private fun toTvShow(item: JSONObject): TvShow {
        val id = item.optString("Id")
        val imageTags = item.optJSONObject("ImageTags")
        val primary = imageTags?.optString("Primary")
        val (imdb, _) = providerIds(item)
        return TvShow(
            id = id,
            title = item.optString("Name"),
            overview = item.optString("Overview").ifBlank { null },
            released = item.optString("PremiereDate").take(10).ifBlank { null },
            poster = imageUrl(id, primary),
            banner = imageUrl(id, backdropTag(item), "Backdrop") ?: imageUrl(id, primary),
            rating = item.optDouble("CommunityRating").takeIf { !it.isNaN() && it > 0 },
            imdbId = imdb,
            providerName = name,
        )
    }

    private fun toResumeEpisode(item: JSONObject): Episode {
        val eid = item.optString("Id")
        val seriesId = item.optString("SeriesId")
        val (imdb, _) = providerIds(item)
        val showImdb = item.optJSONObject("SeriesProviderIds")?.optString("Imdb")
            ?: imdb
        return Episode(
            id = eid,
            number = item.optInt("IndexNumber", 0),
            title = item.optString("Name").ifBlank { null },
            poster = imageUrl(eid, item.optJSONObject("ImageTags")?.optString("Primary"))
                ?: imageUrl(seriesId, item.optJSONObject("ImageTags")?.optString("Primary")),
            overview = item.optString("Overview").ifBlank { null },
            tvShow = TvShow(
                id = seriesId.ifBlank { eid },
                title = item.optString("SeriesName").ifBlank { item.optString("Name") },
                poster = imageUrl(seriesId, item.optString("SeriesPrimaryImageTag").ifBlank { null }),
                imdbId = showImdb,
                providerName = name,
            ),
            season = Season(
                id = item.optString("SeasonId").ifBlank { "${seriesId}|${item.optInt("ParentIndexNumber")}" },
                number = item.optInt("ParentIndexNumber", 1),
            ),
        ).also { applyUserData(item, it) }
    }

    override suspend fun getHome(): List<Category> {
        requireConfigured()
        val resume = buildList {
            val items = api.resumeItems()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                when (item.optString("Type")) {
                    "Movie" -> add(toMovie(item))
                    "Episode" -> add(toResumeEpisode(item))
                }
            }
        }
        val latestMovies = mapMovies(api.latestMovies())
        val latestShows = mapShows(api.latestSeries())
        val genreRows = runCatching {
            val genres = api.genres()
            buildList {
                for (i in 0 until minOf(genres.length(), 6)) {
                    val g = genres.getJSONObject(i)
                    val name = g.optString("Name").ifBlank { continue }
                    val items = api.libraryItems("Movie,Series", 0, 16, genres = name)
                    val shows = buildList {
                        for (j in 0 until items.length()) {
                            val item = items.getJSONObject(j)
                            when (item.optString("Type")) {
                                "Movie" -> add(toMovie(item))
                                "Series" -> add(toTvShow(item))
                            }
                        }
                    }
                    if (shows.isNotEmpty()) add(Category("Jellyfin · $name", shows))
                }
            }
        }.getOrDefault(emptyList())
        return buildList {
            // Named distinctly so HomeViewModel can merge without duplicating local CW.
            if (resume.isNotEmpty()) add(Category("Jellyfin · Continue", resume))
            if (latestMovies.isNotEmpty()) add(Category("Jellyfin · Movies", latestMovies))
            if (latestShows.isNotEmpty()) add(Category("Jellyfin · Series", latestShows))
            addAll(genreRows)
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
        val start = ((page - 1).coerceAtLeast(0)) * 40
        return mapMovies(api.libraryItems("Movie", start, 40))
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        requireConfigured()
        val start = ((page - 1).coerceAtLeast(0)) * 40
        return mapShows(api.libraryItems("Series", start, 40))
    }

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
        val parts = seasonId.split("|", limit = 2)
        val seriesId = parts.getOrNull(0).orEmpty()
        val realSeason = parts.getOrNull(1) ?: seasonId
        val items = if (seriesId.isNotBlank() && parts.size == 2) {
            api.episodesForSeries(seriesId, realSeason)
        } else {
            val seasons = api.seasons(seasonId)
            if (seasons.length() == 0) return emptyList()
            api.episodesForSeries(seasonId, seasons.getJSONObject(0).optString("Id"))
        }
        return buildList {
            for (i in 0 until items.length()) {
                add(toResumeEpisode(items.getJSONObject(i)))
            }
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        requireConfigured()
        val limit = 40
        val start = (page - 1).coerceAtLeast(0) * limit
        val movies = mapMovies(
            api.libraryItems("Movie", start, limit, genres = id),
        )
        val shows = mapShows(
            api.libraryItems("Series", start, limit, genres = id),
        )
        return Genre(
            id = id,
            name = id,
            shows = movies + shows,
        )
    }

    override suspend fun getPeople(id: String, page: Int): People {
        requireConfigured()
        val person = runCatching { api.person(id) }.getOrNull()
        val limit = 40
        val start = (page - 1).coerceAtLeast(0) * limit
        val items = api.itemsByPerson(id, start, limit)
        val filmography = buildList {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                when (item.optString("Type")) {
                    "Movie" -> add(toMovie(item))
                    "Series" -> add(toTvShow(item))
                }
            }
        }
        val imageTags = person?.optJSONObject("ImageTags")
        val primary = imageTags?.optString("Primary")
        return People(
            id = id,
            name = person?.optString("Name")?.ifBlank { id } ?: id,
            image = person?.let { imageUrl(it.optString("Id").ifBlank { id }, primary) },
            biography = person?.optString("Overview")?.ifBlank { null },
            placeOfBirth = null,
            birthday = person?.optString("PremiereDate")?.take(10)?.ifBlank { null },
            filmography = filmography,
        )
    }

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
            headers = mapOf("X-Emby-Token" to UserPreferences.jellyfinAccessToken),
        )
    }

    private fun mapMovies(items: JSONArray): List<Movie> = buildList {
        for (i in 0 until items.length()) add(toMovie(items.getJSONObject(i)))
    }

    private fun mapShows(items: JSONArray): List<TvShow> = buildList {
        for (i in 0 until items.length()) add(toTvShow(items.getJSONObject(i)))
    }
}
