package com.streamflixreborn.streamflix.fragments.tv_show

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.utils.ArtworkRepair
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class TvShowViewModel(
    id: String,
    private val database: AppDatabase,
    private val fallbackPoster: String? = null,
    private val fallbackBanner: String? = null,
) : ViewModel() {

    private fun episodesForSeason(episodes: List<Episode>, season: Season): List<Episode> {
        return episodes
            .filter { episode ->
                val episodeSeason = episode.season
                episodeSeason?.id == season.id ||
                    (episodeSeason?.number != null && episodeSeason.number == season.number)
            }
            .sortedBy { it.number }
            .onEach { episode ->
                episode.season = season
            }
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<State> = combine(
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoading -> {
                    val episodes = database.episodeDao().getByTvShowIdAsFlow(id).first()
                    state.tvShow.seasons.onEach { season ->
                        season.episodes = episodesForSeason(episodes, season)
                    }

                    if (episodes.isEmpty() && state.tvShow.seasons.isNotEmpty()) {
                        val firstSeason = state.tvShow.seasons.firstOrNull { it.number != 0 }
                            ?: state.tvShow.seasons.first()
                        getSeason(TvShow(id, ""), firstSeason)
                    } else {
                        val season = state.tvShow.seasons.let { seasons ->
                            seasons
                                .lastOrNull { season ->
                                    season.episodes.lastOrNull()?.isWatched == true ||
                                            season.episodes.any { it.isWatched }
                                }?.let { season ->
                                    if (season.episodes.lastOrNull()?.isWatched == true) {
                                        val next = seasons.getOrNull(seasons.indexOf(season) + 1)
                                        next ?: season
                                    } else season
                                }
                                ?: seasons.firstOrNull { season ->
                                    season.episodes.isEmpty() ||
                                            season.episodes.lastOrNull()?.isWatched == false
                                }
                        }

                        val episodeIndex = episodes
                            .filter { it.watchHistory != null }
                            .sortedByDescending { it.watchHistory?.lastEngagementTimeUtcMillis }
                            .indexOfFirst { it.watchHistory != null }.takeIf { it != -1 }
                            ?: season?.episodes?.indexOfLast { it.isWatched }
                                ?.takeIf { it != -1 && it + 1 < episodes.size }
                                ?.let { it + 1 }

                        if (
                            episodeIndex == null &&
                            season != null &&
                            (season.episodes.isEmpty() || state.tvShow.seasons.lastOrNull() == season)
                        ) {
                            getSeason(state.tvShow, season)
                        }
                    }
                }
                else -> {}
            }
            emit(state)
        },
        database.tvShowDao().getByIdAsFlow(id),
        database.episodeDao().getByTvShowIdAsFlow(id),
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoading -> {
                    val movies = state.tvShow.recommendations
                        .filterIsInstance<Movie>()
                    if (movies.isEmpty()) {
                        emit(emptyList())
                    } else {
                        emitAll(database.movieDao().getByIds(movies.map { it.id }))
                    }
                }
                else -> emit(emptyList<Movie>())
            }
        },
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoading -> {
                    val tvShows = state.tvShow.recommendations
                        .filterIsInstance<TvShow>()
                    if (tvShows.isEmpty()) {
                        emit(emptyList())
                    } else {
                        emitAll(database.tvShowDao().getByIds(tvShows.map { it.id }))
                    }
                }
                else -> emit(emptyList<TvShow>())
            }
        },
    ) { state, tvShowDb, episodesDb, moviesDb, tvShowsDb ->
        when (state) {
            is State.SuccessLoading -> {
                val moviesById = moviesDb.associateBy { it.id }
                val tvShowsById = tvShowsDb.associateBy { it.id }
                State.SuccessLoading(
                    tvShow = state.tvShow.copy(
                        seasons = (state.tvShow.seasons
                            .takeIf { seasons -> seasons.flatMap { it.episodes } != episodesDb }
                            ?.map { season ->
                                season.copy(
                                    episodes = episodesForSeason(episodesDb, season)
                                )
                            }
                            ?: state.tvShow.seasons)
                            .sortedWith { season1, season2 ->
                                when {
                                    season1.number == 0 && season2.number == 0 -> 0
                                    season1.number == 0 -> 1
                                    season2.number == 0 -> -1
                                    else -> season1.number.compareTo(season2.number)
                                }
                            },
                        recommendations = state.tvShow.recommendations.map { show ->
                            when (show) {
                                is Movie -> moviesById[show.id]
                                    ?.takeIf { !show.isSame(it) }
                                    ?.let { show.copy().merge(it) }
                                    ?: show
                                is TvShow -> tvShowsById[show.id]
                                    ?.takeIf { !show.isSame(it) }
                                    ?.let { show.copy().merge(it) }
                                    ?: show
                            }
                        },
                    ).also { tvShow ->
                        tvShowDb?.let { tvShow.merge(it) }
                    }
                )
            }
            else -> state
        }
    }.flowOn(Dispatchers.IO)

    sealed class State {
        data object Loading : State()
        data class SuccessLoading(val tvShow: TvShow) : State()
        data class FailedLoading(val error: Exception) : State()
    }

    private val _seasonState = MutableStateFlow<SeasonState>(SeasonState.Loading)

    sealed class SeasonState {
        data object Loading :  SeasonState()
        data class SuccessLoading(
            val tvShow: TvShow,
            val season: Season,
            val episodes: List<Episode>,
        ) : SeasonState()
        data class FailedLoading(val error: Exception) : SeasonState()
    }

    init {
        getTvShow(id)
    }


    fun getTvShow(id: String) = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.Loading)

        try {
            val tvShow = UserPreferences.currentProvider!!.getTvShow(id)

            if (!ArtworkRepair.isRemoteArtworkUrl(tvShow.poster) && ArtworkRepair.isRemoteArtworkUrl(fallbackPoster)) {
                tvShow.poster = fallbackPoster
            }
            if (!ArtworkRepair.isRemoteArtworkUrl(tvShow.banner) && ArtworkRepair.isRemoteArtworkUrl(fallbackBanner)) {
                tvShow.banner = fallbackBanner
            }
            if (!ArtworkRepair.isRemoteArtworkUrl(tvShow.banner) && ArtworkRepair.isRemoteArtworkUrl(tvShow.poster)) {
                tvShow.banner = tvShow.poster
            }

            database.tvShowDao().getById(tvShow.id)?.let { tvShowDb ->
                tvShow.merge(tvShowDb)
            }
            database.tvShowDao().insert(tvShow)

            val tvShowCopy = tvShow.copy()
            tvShow.seasons.forEach { season ->
                season.tvShow = tvShowCopy
            }
            database.seasonDao().insertAll(tvShow.seasons)

            _state.emit(State.SuccessLoading(tvShow))
        } catch (e: Exception) {
            Log.e("TvShowViewModel", "getTvShow: ", e)
            _state.emit(State.FailedLoading(e))
        }
    }

    private fun getSeason(tvShow: TvShow, season: Season) = viewModelScope.launch(Dispatchers.IO) {
        _seasonState.emit(SeasonState.Loading)

        try {
            val episodes = UserPreferences.currentProvider!!.getEpisodesBySeason(season.id)
            val ids = episodes.map { it.id }
            val episodeMap = episodes.associateBy { it.id }

            ids.chunked(400).forEach { chunk ->
                database.episodeDao()
                    .getByIds(chunk)
                    .forEach { episodeDb ->
                        episodeMap[episodeDb.id]?.merge(episodeDb)
                    }
            }

            episodes.forEach { episode ->
                episode.tvShow = tvShow
                episode.season = season
            }

            database.episodeDao().insertAll(episodes)

            _seasonState.emit(SeasonState.SuccessLoading(tvShow, season, episodes))
        } catch (e: Exception) {
            Log.e("TvShowViewModel", "getSeason: ", e)
            _seasonState.emit(SeasonState.FailedLoading(e))
        }
    }
}
