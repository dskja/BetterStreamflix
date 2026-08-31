package com.betterstreamflix.fragments.movie

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.models.Video
import com.betterstreamflix.utils.EpisodeManager
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class MovieViewModel(id: String, private val database: AppDatabase) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl: StateFlow<String?> = _streamUrl.asStateFlow()
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
            resolveStreamUrl(movie.id)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("MovieViewModel", "getMovie: ", e)
            _state.emit(State.FailedLoading(e))
        }
    }

    suspend fun resolveStreamUrl(movieId: String = ""): String? {
        _streamUrl.value?.let { return it }
        val movie = (_state.value as? State.SuccessLoading)?.movie
        val id = movieId.ifBlank { movie?.id ?: return null }
        try {
            val provider = UserPreferences.currentProvider ?: return null
            val videoType = movie?.let {
                Video.Type.Movie(
                    id = it.id,
                    title = it.title,
                    releaseDate = it.released?.format("yyyy-MM-dd") ?: "",
                    poster = it.poster ?: it.banner ?: "",
                    imdbId = it.imdbId,
                )
            } ?: Video.Type.Movie(
                id = id,
                title = "",
                releaseDate = "",
                poster = "",
                imdbId = null,
            )
            val servers = provider.getServers(id, videoType)
            val server = servers.firstOrNull() ?: return null
            val video = provider.getVideo(server)
            val url = video.source.takeIf { it.isNotBlank() }
            _streamUrl.value = url
            return url
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("MovieViewModel", "resolveStreamUrl failed", e)
            return null
        }
    }
}
