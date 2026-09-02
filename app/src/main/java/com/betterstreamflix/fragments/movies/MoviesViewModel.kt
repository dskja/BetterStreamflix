package com.betterstreamflix.fragments.movies

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Movie
import com.betterstreamflix.utils.ParentalControlUtils
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.ProviderChangeNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class MoviesViewModel(database: AppDatabase) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    
    init {
        // Listen for provider changes and reload data
        viewModelScope.launch {
            ProviderChangeNotifier.providerChangeFlow.collect {
                getMovies()
            }
        }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<State> = combine(
        _state,
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoading -> {
                    if (state.movies.isEmpty()) {
                        emit(emptyList())
                    } else {
                        emitAll(database.movieDao().getByIds(state.movies.map { it.id }))
                    }
                }
                else -> emit(emptyList<Movie>())
            }
        },
    ) { state, moviesDb ->
        when (state) {
            is State.SuccessLoading -> {
                val moviesById = moviesDb.associateBy { it.id }
                State.SuccessLoading(
                    movies = state.movies.map { movie ->
                        moviesById[movie.id]
                            ?.takeIf { !movie.isSame(it) }
                            ?.let { movie.copy().merge(it) }
                            ?: movie
                    },
                    hasMore = state.hasMore
                )
            }
            else -> state
        }
    }.flowOn(Dispatchers.IO)

    private var page = 1

    sealed class State {
        data object Loading : State()
        data object LoadingMore : State()
        data class SuccessLoading(val movies: List<Movie>, val hasMore: Boolean) : State()
        data class FailedLoading(val error: Exception) : State()
    }

    init {
        getMovies()
    }


    fun getMovies() = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.Loading)

        try {
            val movies = ParentalControlUtils.filterItems(
                UserPreferences.currentProvider?.getMovies() ?: run { _state.emit(State.FailedLoading(NullPointerException("Provider returned null"))); return@launch }
            ).filterIsInstance<Movie>()

            page = 1

            _state.emit(State.SuccessLoading(movies, movies.size >= PAGE_SIZE))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("MoviesViewModel", "getMovies: ", e)
            _state.emit(State.FailedLoading(e))
        }
    }

    fun loadMoreMovies() = viewModelScope.launch(Dispatchers.IO) {
        val currentState = _state.value
        if (currentState is State.SuccessLoading) {
            _state.emit(State.LoadingMore)

            try {
                val movies = ParentalControlUtils.filterItems(
                    UserPreferences.currentProvider?.getMovies(page + 1) ?: return@launch
                ).filterIsInstance<Movie>()

                page += 1

                _state.emit(
                    State.SuccessLoading(
                        movies = currentState.movies + movies,
                        hasMore = movies.size >= PAGE_SIZE,
                    )
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("MoviesViewModel", "loadMoreMovies: ", e)
                _state.emit(State.FailedLoading(e))
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 24
    }
}
