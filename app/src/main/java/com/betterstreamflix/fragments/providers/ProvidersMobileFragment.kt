package com.betterstreamflix.fragments.providers

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.betterstreamflix.R
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.ProvidersScreen
import com.betterstreamflix.models.Provider as ModelProvider
import com.betterstreamflix.providers.Provider
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.toActivity
import java.util.Locale

class ProvidersMobileFragment : ComposeHostFragment() {

    private val viewModel by viewModels<ProvidersViewModel>()

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle(
            initialValue = ProvidersViewModel.State.Loading,
        )
        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle(initialValue = "")
        val languageFilter by viewModel.languageFilter.collectAsStateWithLifecycle(
            initialValue = UserPreferences.providerLanguage,
        )

        var languageChips by remember(languageFilter) {
            mutableStateOf(buildLanguageChips(languageFilter))
        }

        val providers = when (val current = state) {
            is ProvidersViewModel.State.SuccessLoading -> current.providers
            else -> emptyList()
        }

        ProvidersScreen(
            providers = providers,
            languageChips = languageChips,
            searchQuery = searchQuery,
            isLoading = state is ProvidersViewModel.State.Loading,
            errorMessage = (state as? ProvidersViewModel.State.FailedLoading)?.error?.message,
            onSearchQueryChange = viewModel::setSearchQuery,
            onLanguageChipSelected = { chip ->
                languageChips = buildLanguageChips(chip.code)
                viewModel.setLanguageFilter(chip.code)
            },
            onProviderSelected = ::selectProvider,
            onOpenMarketplace = {
                findNavController().navigate(R.id.provider_marketplace)
            },
            onRetry = { viewModel.setLanguageFilter(UserPreferences.providerLanguage) },
        )
    }

    private fun buildLanguageChips(selectedLanguage: String?): List<LanguageChip> {
        val languages = Provider.providers.keys
            .distinctBy { it.language }
            .map {
                val locale = Locale.forLanguageTag(it.language)
                LanguageChip(
                    code = it.language,
                    name = locale.getDisplayLanguage(locale)
                        .replaceFirstChar { char -> char.titlecase() },
                )
            }
            .sortedBy { it.name.lowercase() }

        val allChips = mutableListOf<LanguageChip>()
        allChips.add(LanguageChip(null, getString(R.string.providers_all_languages)))
        allChips.add(LanguageChip("favorites", getString(R.string.providers_favorites)))
        allChips.addAll(languages)

        allChips.forEach { chip ->
            chip.isSelected = when {
                chip.code == null && selectedLanguage == null -> true
                chip.code == "favorites" && selectedLanguage == "favorites" -> true
                chip.code == selectedLanguage -> true
                else -> false
            }
        }

        return allChips
    }

    private fun selectProvider(provider: ModelProvider) {
        UserPreferences.currentProvider = provider.provider
        context?.toActivity()?.apply {
            startActivity(
                Intent(this, this::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
            finish()
        }
    }
}
