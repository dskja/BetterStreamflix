package com.streamflixreborn.streamflix.fragments.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.utils.UserPreferences
import com.streamflixreborn.streamflix.utils.combine
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

class HomeViewModel(database: AppDatabase) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<State> = combine(
        _state,

        // CONTINUE WATCHING
        combine(
            database.movieDao().getWatchingMovies(),
            database.episodeDao().getWatchingEpisodes(),
            database.episodeDao().getNextEpisodesToWatch(),
        ) { watchingMovies, watchingEpisodes, watchNextEpisodes ->

            val allEpisodes = (watchingEpisodes + watchNextEpisodes)
                .distinctBy { it.id }

            val tvShowIds = allEpisodes.mapNotNull { it.tvShow?.id }.distinct()
            val seasonIds = allEpisodes.mapNotNull { it.season?.id }.distinct()

            val tvShowsMap = if (tvShowIds.isEmpty()) {
                emptyMap()
            } else {
                database.tvShowDao()
                    .getByIds(tvShowIds)
                    .first()
                    .associateBy { it.id }
            }

            val seasonsMap = if (seasonIds.isEmpty()) {
                emptyMap()
            } else {
                database.seasonDao()
                    .getByIds(seasonIds)
                    .associateBy { it.id }
            }

            val enrichedEpisodes = allEpisodes.onEach { episode ->
                episode.tvShow = episode.tvShow?.id?.let { tvShowsMap[it] }
                episode.season = episode.season?.id?.let { seasonsMap[it] }
            }

            (watchingMovies + enrichedEpisodes) as List<AppAdapter.Item>
        },

        database.movieDao().getFavorites(),
        database.tvShowDao().getFavorites(),

        // MOVIES DB
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoading -> {
                    val movies = state.categories
                        .flatMap { it.list }
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

        // TV SHOWS DB
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoading -> {
                    val tvShows = state.categories
                        .flatMap { it.list }
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

        ) {
            state: State,
            continueWatching: List<AppAdapter.Item>,
            favoritesMovies: List<Movie>,
            favoriteTvShows: List<TvShow>,
            moviesDb: List<Movie>,
            tvShowsDb: List<TvShow>
        ->

        when (state) {
            is State.SuccessLoading -> {

                val moviesMap = moviesDb.associateBy { it.id }
                val tvShowsMap = tvShowsDb.associateBy { it.id }

                fun mergeItem(item: AppAdapter.Item): AppAdapter.Item {
                    return when (item) {
                        is Movie -> moviesMap[item.id]
                            ?.takeIf { !item.isSame(it) }
                            ?.let { item.copy().merge(it) }
                            ?: item

                        is TvShow -> tvShowsMap[item.id]
                            ?.takeIf { !item.isSame(it) }
                            ?.let { item.copy().merge(it) }
                            ?: item

                        else -> item
                    }
                }

                val categories = listOfNotNull(

                    // FEATURED
                    state.categories
                        .find { it.name == Category.FEATURED }
                        ?.let { category ->
                            category.copy(
                                list = category.list.map(::mergeItem)
                            )
                        },

                    // CONTINUE WATCHING
                    Category(
                        name = Category.CONTINUE_WATCHING,
                        list = continueWatching
                            .sortedByDescending {
                                when (it) {
                                    is Episode -> it.watchHistory?.lastEngagementTimeUtcMillis
                                        ?: it.watchedDate?.timeInMillis
                                        ?: 0L

                                    is Movie -> it.watchHistory?.lastEngagementTimeUtcMillis
                                        ?: it.watchedDate?.timeInMillis
                                        ?: 0L

                                    else -> 0L
                                }
                            }
                            .distinctBy {
                                when (it) {
                                    is Episode -> it.tvShow?.id
                                    is Movie -> it.id
                                    else -> null
                                }
                            },
                    ),

                    // FAVORITES
                    Category(
                        name = Category.FAVORITE_MOVIES,
                        list = favoritesMovies.reversed(),
                    ),
                    Category(
                        name = Category.FAVORITE_TV_SHOWS,
                        list = favoriteTvShows.reversed(),
                    ),
                ) + state.categories
                    .filter { it.name != Category.FEATURED }
                    .map { category ->
                        category.copy(
                            list = category.list.map(::mergeItem)
                        )
                    }

                State.SuccessLoading(categories)
            }

            else -> state
        }
    }.flowOn(Dispatchers.IO)

    sealed class State {
        data object Loading : State()
        data class SuccessLoading(val categories: List<Category>) : State()
        data class FailedLoading(val error: Exception) : State()
    }

    init {
        getHome()
    }

    fun getHome() = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.Loading)

        try {
            val categories = UserPreferences.currentProvider!!.getHome()
            _state.emit(State.SuccessLoading(categories))
        } catch (e: Exception) {
            Log.e("HomeViewModel", "getHome: ", e)
            _state.emit(State.FailedLoading(e))
        }
    }
}
