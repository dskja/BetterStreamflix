package com.streamflixreborn.streamflix.utils

import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.models.Video.Type.Episode

object EpisodeManager {
    private val episodes = mutableListOf<Episode>()
    var currentIndex = 0
        private set

    fun addEpisodes(list: List<Episode>) {
        episodes.clear()
        episodes.addAll(list)
        currentIndex = 0
    }

    private fun mergeEpisodes(list: List<Episode>) {
        if (list.isEmpty()) return

        val currentEpisodeId = getCurrentEpisode()?.id
        val merged = (episodes + list)
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.season.number }, { it.number }))

        episodes.clear()
        episodes.addAll(merged)

        currentIndex = currentEpisodeId
            ?.let { id -> episodes.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            ?: 0
    }

    suspend fun addEpisodesFromDb(type: Video.Type.Episode, database: AppDatabase) {
        val tvShowId = type.tvShow.id
        val seasonNumber = type.season.number
        var episodesFromDb = database.episodeDao().getByTvShowIdAndSeasonNumber(tvShowId, seasonNumber)
        val tvShowContext = database.tvShowDao().getById(tvShowId)?.let { storedTvShow ->
            TvShow(
                id = storedTvShow.id,
                title = storedTvShow.title,
                poster = storedTvShow.poster,
                banner = storedTvShow.banner,
                released = storedTvShow.released?.format("yyyy-MM-dd"),
                imdbId = storedTvShow.imdbId
            )
        } ?: TvShow(
            id = type.tvShow.id,
            title = type.tvShow.title,
            poster = type.tvShow.poster,
            banner = type.tvShow.banner,
            imdbId = type.tvShow.imdbId
        )
        val seasonContext = Season(id = "", number = seasonNumber, title = type.season.title).apply {
            tvShow = tvShowContext
        }
        val provider = UserPreferences.currentProvider
        if (provider != null) {
            try {
                val tvShow = provider.getTvShow(tvShowId)
                val season = tvShow.seasons.find { it.number == seasonNumber }
                if (season != null) {
                    val fetchedEpisodes = provider.getEpisodesBySeason(season.id)
                    if (fetchedEpisodes.isNotEmpty()) {
                        fetchedEpisodes.forEach { episode ->
                            episode.tvShow = episode.tvShow ?: tvShow
                            episode.season = episode.season ?: season
                        }
                        database.episodeDao().insertAll(fetchedEpisodes)
                        episodesFromDb = fetchedEpisodes
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (episodesFromDb.isNotEmpty()) {
            episodesFromDb.forEach { episode ->
                episode.tvShow = episode.tvShow ?: tvShowContext
                episode.season = episode.season ?: seasonContext
            }
            addEpisodes(convertToVideoTypeEpisodes(episodesFromDb, database, seasonNumber))
        }
    }

    suspend fun ensureNextEpisodeAvailable(type: Video.Type.Episode, database: AppDatabase): Boolean {
        if (hasNextEpisode()) return true

        val currentEpisode = getCurrentEpisode()
            ?.takeIf { current -> current.id == type.id }
            ?: type
        val provider = UserPreferences.currentProvider ?: return false
        val tvShowId = currentEpisode.tvShow.id
        val currentSeasonNumber = currentEpisode.season.number

        fun nextSeasonFrom(seasons: List<Season>): Season? =
            seasons
                .filter { season -> season.number > currentSeasonNumber }
                .sortedBy { season -> season.number }
                .firstOrNull()

        var nextSeason = nextSeasonFrom(database.seasonDao().getByTvShowId(tvShowId))

        if (nextSeason == null) {
            runCatching { provider.getTvShow(tvShowId) }
                .getOrNull()
                ?.also { tvShow ->
                    database.tvShowDao().save(tvShow)
                    tvShow.seasons.forEach { season ->
                        season.tvShow = tvShow
                    }
                    database.seasonDao().insertAll(tvShow.seasons)
                    nextSeason = nextSeasonFrom(tvShow.seasons)
                }
        }

        val seasonToLoad = nextSeason ?: return false
        var nextSeasonEpisodes = database.episodeDao()
            .getByTvShowIdAndSeasonNumber(tvShowId, seasonToLoad.number)

        if (nextSeasonEpisodes.isEmpty() && seasonToLoad.id.isNotBlank()) {
            nextSeasonEpisodes = runCatching {
                provider.getEpisodesBySeason(seasonToLoad.id)
            }.getOrDefault(emptyList()).also { fetchedEpisodes ->
                if (fetchedEpisodes.isNotEmpty()) {
                    fetchedEpisodes.forEach { episode ->
                        episode.tvShow = episode.tvShow ?: seasonToLoad.tvShow
                        episode.season = episode.season ?: seasonToLoad
                    }
                    database.episodeDao().insertAll(fetchedEpisodes)
                }
            }
        }

        if (nextSeasonEpisodes.isEmpty()) return false

        nextSeasonEpisodes.forEach { episode ->
            episode.tvShow = episode.tvShow ?: seasonToLoad.tvShow
            episode.season = episode.season ?: seasonToLoad
        }

        mergeEpisodes(convertToVideoTypeEpisodes(nextSeasonEpisodes, database, seasonToLoad.number))
        return hasNextEpisode()
    }
    fun clearEpisodes(){
        episodes.clear()
        currentIndex = 0
    }
    fun setCurrentEpisode(episode: Episode) {
        val index = episodes.indexOfFirst { it.id == episode.id }
        if (index >= 0) {
            currentIndex = index
        }
    }

    fun getCurrentEpisode(): Episode? =
        episodes.getOrNull(currentIndex)

    fun peekNextEpisode(): Episode? =
        episodes.getOrNull(currentIndex + 1)

    fun getNextEpisode(): Episode? {
        if (currentIndex + 1 < episodes.size) {
            currentIndex++
            return episodes[currentIndex]
        }
        return null
    }
    fun getPreviousEpisode(): Episode? {
        if (currentIndex -1 >= 0){
            currentIndex--
            return episodes[currentIndex]
        }
        return null
    }
    fun hasPreviousEpisode(): Boolean {
        return currentIndex > 0
    }

    fun hasNextEpisode(): Boolean {
        return currentIndex < episodes.size - 1
    }

    fun listIsEmpty(episode: Episode): Boolean{
        return episodes.isEmpty() || episodes.none { it.id == episode.id }
    }

    fun convertToVideoTypeEpisodes(episodes: List<com.streamflixreborn.streamflix.models.Episode>, database: AppDatabase, seasonNumber: Int): List<Episode> {
        val videoEpisodes = episodes.map { ep ->
            val seasonId = ep.season?.id ?: ""
            val tvShowId = ep.tvShow?.id ?: ""
            val seasonFromDb = database.seasonDao().getById(seasonId)
            val tvShowFromDb = database.tvShowDao().getById(tvShowId)
            val tvShowTitle = tvShowFromDb?.title?.takeUnless { it.isBlank() }
                ?: ep.tvShow?.title
                ?: ""
            Episode(
                id = ep.id,
                number = ep.number,
                title = ep.title,
                poster = ep.poster,
                overview = ep.overview,
                tvShow = Episode.TvShow(
                    id = tvShowId,
                    title = tvShowTitle,
                    poster = tvShowFromDb?.poster ?: ep.tvShow?.poster,
                    banner = tvShowFromDb?.banner ?: ep.tvShow?.banner,
                    releaseDate = tvShowFromDb?.released?.format("yyyy-MM-dd") ?: ep.tvShow?.released?.format("yyyy-MM-dd"),
                    imdbId = tvShowFromDb?.imdbId ?: ep.tvShow?.imdbId
                ),
                season = Episode.Season(
                    number = seasonFromDb?.number ?: seasonNumber,
                    title = seasonFromDb?.title ?: ep.season?.title
                )
            )
        }
        return videoEpisodes
    }



}
