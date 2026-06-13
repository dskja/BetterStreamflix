package com.streamflixreborn.streamflix.fragments.providers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixreborn.streamflix.models.Provider as ModelProvider
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.providers.TmdbProvider
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class ProvidersViewModel : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: Flow<State> = _state

    sealed class State {
        data object Loading : State()
        data class SuccessLoading(val providers: List<ModelProvider>) : State()
        data class FailedLoading(val error: Exception) : State()
    }

    init {
        getProviders(UserPreferences.providerLanguage)
    }

    fun getProviders(language: String? = null) = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.Loading)

        try {
            val isFavoritesFilter = language == "favorites"
            val favorites = UserPreferences.favoriteProviders

            val providers = Provider.providers.keys
                .filter { 
                    if (isFavoritesFilter) {
                        favorites.contains(it.name)
                    } else {
                        language == null || it.language == language 
                    }
                }
                .sortedBy { it.name }
                .toMutableList()

            if (language == null || isFavoritesFilter) {
                val availableLanguages = Provider.providers.keys.map { it.language }.distinct()
                availableLanguages.forEach { lang ->
                    if (lang != "pl") {
                        val tmdbName = "TMDb (${getLanguageDisplayName(lang)})"
                        if (!isFavoritesFilter || favorites.contains(tmdbName)) {
                            providers.add(TmdbProvider(lang))
                        }
                    }
                }
            } else {
                if (language != "pl") {
                    providers.add(TmdbProvider(language))
                }
            }

            val modelProviders = providers.map {
                val name = if (it is TmdbProvider) {
                    "TMDb (${getLanguageDisplayName(it.language)})"
                } else {
                    it.name
                }
                ModelProvider(
                    name = name,
                    logo = it.logo,
                    language = it.language,
                    provider = it,
                    isFavorite = favorites.contains(name)
                )
            }.sortedWith(
                compareBy<ModelProvider> { it.provider is TmdbProvider }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )

            _state.emit(State.SuccessLoading(modelProviders))
        } catch (e: Exception) {
            Log.e("ProvidersViewModel", "getProviders: ", e)
            _state.emit(State.FailedLoading(e))
        }
    }

    private fun getLanguageDisplayName(languageCode: String): String {
        return Locale.forLanguageTag(languageCode).displayLanguage
    }
}
