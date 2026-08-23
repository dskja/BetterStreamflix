package com.betterstreamflix.fragments.providers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betterstreamflix.models.Provider as ModelProvider
import com.betterstreamflix.providers.Provider
import com.betterstreamflix.providers.TmdbProvider
import com.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class ProvidersViewModel : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: Flow<State> = _state

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: Flow<String> = _searchQuery

    private val _languageFilter = MutableStateFlow(UserPreferences.providerLanguage)
    val languageFilter: Flow<String?> = _languageFilter

    private var allProviders: List<ModelProvider> = emptyList()

    sealed class State {
        data object Loading : State()
        data class SuccessLoading(val providers: List<ModelProvider>) : State()
        data class FailedLoading(val error: Exception) : State()
    }

    init {
        loadProviders(UserPreferences.providerLanguage)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch { applyFilters() }
    }

    fun setLanguageFilter(language: String?) {
        _languageFilter.value = language
        UserPreferences.providerLanguage = language
        loadProviders(language)
    }

    private fun loadProviders(language: String?) = viewModelScope.launch(Dispatchers.IO) {
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

            allProviders = providers.map {
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

            applyFilters()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("ProvidersViewModel", "loadProviders: ", e)
            _state.emit(State.FailedLoading(e))
        }
    }

    private suspend fun applyFilters() {
        val query = _searchQuery.value
        val filtered = if (query.isBlank()) {
            allProviders
        } else {
            allProviders.filter { provider ->
                provider.name.contains(query, ignoreCase = true) ||
                provider.language.contains(query, ignoreCase = true)
            }
        }
        _state.emit(State.SuccessLoading(filtered))
    }

    fun toggleFavorite(provider: ModelProvider) {
        val favorites = UserPreferences.favoriteProviders.toMutableSet()
        provider.isFavorite = !provider.isFavorite
        if (provider.isFavorite) {
            favorites.add(provider.name)
        } else {
            favorites.remove(provider.name)
        }
        UserPreferences.favoriteProviders = favorites

        allProviders = allProviders.map { p ->
            if (p.name == provider.name) provider else p
        }

        viewModelScope.launch(Dispatchers.IO) {
            applyFilters()
        }
    }

    private fun getLanguageDisplayName(languageCode: String): String {
        return Locale.forLanguageTag(languageCode).displayLanguage
    }
}
