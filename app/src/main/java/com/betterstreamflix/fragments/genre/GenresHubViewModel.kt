package com.betterstreamflix.fragments.genre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betterstreamflix.metadata.GenreManager
import com.betterstreamflix.models.Genre
import com.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GenresHubViewModel : ViewModel() {

    sealed class State {
        data object Loading : State()
        data class Success(val genres: List<Genre>) : State()
        data class Failed(val error: Exception) : State()
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()

    fun loadGenres() {
        viewModelScope.launch {
            _state.value = State.Loading
            try {
                val provider = UserPreferences.currentProvider
                if (provider == null) {
                    _state.value = State.Success(standardGenres())
                    return@launch
                }
                val fromProvider = withContext(Dispatchers.IO) {
                    provider.search("", 1).filterIsInstance<Genre>()
                }
                val genres = if (fromProvider.isNotEmpty()) {
                    fromProvider.sortedBy { it.name.lowercase() }
                } else {
                    standardGenres()
                }
                _state.value = State.Success(genres)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.value = State.Failed(error)
            }
        }
    }

    private fun standardGenres(): List<Genre> =
        GenreManager.STANDARD_GENRES.map { name ->
            Genre(
                id = name.lowercase().replace(" ", "-"),
                name = name,
            )
        }
}
