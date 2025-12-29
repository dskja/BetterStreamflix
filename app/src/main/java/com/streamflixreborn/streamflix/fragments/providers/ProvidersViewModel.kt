package com.streamflixreborn.streamflix.fragments.providers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixreborn.streamflix.models.Provider as ModelProvider
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.providers.TmdbProvider // Importa TmdbProvider
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
        getProviders(UserPreferences.currentLanguage)
    }

    fun getProviders(language: String? = null) = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.Loading)

        try {
            val providers = Provider.providers.keys
                .filter { language == null || it.language == language }
                .sortedBy { it.name }
                .toMutableList() // Converti in MutableList per poter aggiungere elementi

            if (language == null) {
                // Se nessuna lingua è selezionata, aggiungi un provider TMDb per ogni lingua disponibile
                val availableLanguages = Provider.providers.keys.map { it.language }.distinct()
                availableLanguages.forEach { lang ->
                    providers.add(TmdbProvider(lang))
                }
            } else {
                // Se è selezionata una lingua, aggiungi solo il provider TMDb per quella lingua
                providers.add(TmdbProvider(language))
            }

            val modelProviders = providers.map {
                ModelProvider(
                    name = if (it is TmdbProvider) {
                        "TMDb (${getLanguageDisplayName(it.language)})"
                    } else {
                        it.name
                    },
                    logo = it.logo,
                    language = it.language,
                    provider = it,
                )
            }.sortedBy { it.name } // Ordina di nuovo dopo aver aggiunto i provider TMDb

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