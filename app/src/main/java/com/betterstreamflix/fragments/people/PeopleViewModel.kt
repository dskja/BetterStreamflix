package com.betterstreamflix.fragments.people

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.People
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.utils.TmdbUtils
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class PeopleViewModel(private val id: String, database: AppDatabase) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<State> = combine(
        _state,
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoading -> {
                    val movies = state.people.filmography
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
                    val tvShows = state.people.filmography
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
    ) { state, moviesDb, tvShowsDb ->
        when (state) {
            is State.SuccessLoading -> {
                val moviesById = moviesDb.associateBy { it.id }
                val tvShowsById = tvShowsDb.associateBy { it.id }
                State.SuccessLoading(
                    people = state.people.copy(
                        filmography = state.people.filmography.map { item ->
                            when (item) {
                                is Movie -> moviesById[item.id]
                                    ?.takeIf { !item.isSame(it) }
                                    ?.let { item.copy().merge(it) }
                                    ?: item
                                is TvShow -> tvShowsById[item.id]
                                    ?.takeIf { !item.isSame(it) }
                                    ?.let { item.copy().merge(it) }
                                    ?: item
                            }
                        }
                    ),
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
        data class SuccessLoading(val people: People, val hasMore: Boolean) : State()
        data class FailedLoading(val error: Exception) : State()
    }

    init {
        getPeople(id)
    }


    fun getPeople(id: String) = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.Loading)

        try {
            val people = UserPreferences.currentProvider?.getPeople(id) ?: run { _state.emit(State.FailedLoading(NullPointerException("Provider returned null"))); return@launch }

            page = 1

            _state.emit(State.SuccessLoading(enrichPeopleProfile(people), true))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("PeopleViewModel", "getPeople: ", e)
            _state.emit(State.FailedLoading(e))
        }
    }

    fun loadMorePeopleFilmography() = viewModelScope.launch(Dispatchers.IO) {
        val currentState = _state.value
        if (currentState is State.SuccessLoading) {
            _state.emit(State.LoadingMore)

            try {
                val people = UserPreferences.currentProvider?.getPeople(id, page + 1) ?: return@launch

                page += 1

                _state.emit(
                    State.SuccessLoading(
                        people = currentState.people.copy(
                            filmography = currentState.people.filmography + people.filmography
                        ),
                        hasMore = people.filmography.isNotEmpty(),
                    )
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("PeopleViewModel", "loadMorePeopleFilmography: ", e)
                _state.emit(State.FailedLoading(e))
            }
        }
    }

    private suspend fun enrichPeopleProfile(people: People): People {
        if (!UserPreferences.enableTmdb) return people
        if (!people.image.isNullOrBlank() && !people.biography.isNullOrBlank()) return people
        val enriched = TmdbUtils.enrichPersonByName(people.name) ?: return people
        return people.copy(
            image = people.image ?: enriched.image,
            biography = people.biography ?: enriched.biography,
            placeOfBirth = people.placeOfBirth ?: enriched.placeOfBirth,
            birthday = people.birthday?.format("yyyy-MM-dd") ?: enriched.birthday?.format("yyyy-MM-dd"),
            deathday = people.deathday?.format("yyyy-MM-dd") ?: enriched.deathday?.format("yyyy-MM-dd"),
        )
    }
}
