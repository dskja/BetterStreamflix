package com.betterstreamflix.fragments.movie

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.utils.EpisodeManager
import com.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class MovieViewModel(id: String, private val database: AppDatabase) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<State> = combine(
        _state,
        database.movieDao().getByIdAsFlow(id),
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoading -> {
                    val movies = state.movie.recommendations
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
                    val tvShows = state.movie.recommendations
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
    ) { state, movieDb, moviesDb, tvShowsDb ->
        when (state) {
            is State.SuccessLoading -> {
                val moviesById = moviesDb.associateBy { it.id }
                val tvShowsById = tvShowsDb.associateBy { it.id }
                State.SuccessLoading(
                    movie = state.movie.copy(
                        recommendations = state.movie.recommendations.map { show ->
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
                    ).also { movie ->
                        movieDb?.let { movie.merge(it) }
                    }
                )
            }
            else -> state
        }
    }.flowOn(Dispatchers.IO)

    sealed class State {
        data object Loading : State()
        data class SuccessLoading(val movie: Movie) : State()
        data class FailedLoading(val error: Exception) : State()
    }

    init {
        EpisodeManager.clearEpisodes()
        getMovie(id)
    }


    fun getMovie(id: String) = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.Loading)

        try {
            val movie = UserPreferences.currentProvider?.getMovie(id) ?: run { _state.emit(State.FailedLoading(NullPointerException("Provider returned null"))); return@launch }

            database.movieDao().getById(id)?.let { movieDb ->
                movie.merge(movieDb)
            }
            database.movieDao().insert(movie)

            _state.emit(State.SuccessLoading(movie))
        } catch (e: Exception) {
            Log.e("MovieViewModel", "getMovie: ", e)
            _state.emit(State.FailedLoading(e))
        }
    }
}
