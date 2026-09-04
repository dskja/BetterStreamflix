package com.betterstreamflix.fragments.search

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.betterstreamflix.R
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.SearchScreen
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Genre
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.providers.Provider
import com.betterstreamflix.ui.ShowOptionsDialog
import com.betterstreamflix.utils.CacheUtils
import com.betterstreamflix.utils.DeepLinkHandler
import com.betterstreamflix.utils.LoggingUtils
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.viewModelsFactory
import retrofit2.HttpException

class SearchTvFragment : ComposeHostFragment() {

    private var hasAutoCleared409: Boolean = false

    private val database by lazy { AppDatabase.getInstanceOrNull(requireContext()) }
    private val viewModel by viewModelsFactory {
        SearchViewModel(requireNotNull(database) { "Current provider is not set" })
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (database == null) return

        DeepLinkHandler.pendingSearchQuery?.let { pending ->
            DeepLinkHandler.pendingSearchQuery = null
            if (pending.isNotBlank() && pending != "_") {
                viewModel.search(pending)
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        if (database == null || UserPreferences.currentProvider == null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    com.betterstreamflix.compose.components.BsEmptyState(
                        message = stringResource(R.string.providers_choose_title),
                    )
                    com.betterstreamflix.compose.components.BsPrimaryButton(
                        text = stringResource(R.string.main_menu_change_provider),
                        onClick = { findNavController().navigate(R.id.providers) },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            return
        }

        val state by viewModel.state.collectAsStateWithLifecycle(initialValue = State.Searching)
        var query by remember { mutableStateOf(viewModel.query) }

        LaunchedEffect(state) {
            if (state is State.SuccessSearching || state is State.FailedSearching) {
                query = viewModel.query
            }
        }

        val isLoading = state is State.Searching || state is State.GlobalSearching
        val results = (state as? State.SuccessSearching)?.results.orEmpty()
        val isEmpty = state is State.SuccessSearching && results.isEmpty() && query.isNotBlank()
        var recentQueries by remember {
            mutableStateOf(
                com.betterstreamflix.search.SearchHistoryManager.getHistory(requireContext())
                    .map { it.query }
                    .take(8),
            )
        }
        LaunchedEffect(state) {
            if (state is State.SuccessSearching) {
                recentQueries = com.betterstreamflix.search.SearchHistoryManager
                    .getHistory(requireContext())
                    .map { it.query }
                    .take(8)
            }
        }

        if (state is State.FailedSearching) {
            val failed = state as State.FailedSearching
            val code = (failed.error as? HttpException)?.code()
            if (code == 409 && !hasAutoCleared409) {
                LaunchedEffect(failed.error) {
                    hasAutoCleared409 = true
                    CacheUtils.clearAppCache(requireContext())
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.clear_cache_done_409),
                        Toast.LENGTH_SHORT,
                    ).show()
                    viewModel.search(viewModel.query)
                }
            } else {
                LaunchedEffect(failed.error) {
                    val message = failed.error.message?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.loading_error_generic)
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    LoggingUtils.showErrorDialog(requireContext(), failed.error)
                }
            }
        }

        SearchScreen(
            query = query,
            onQueryChange = { newQuery ->
                query = newQuery
                if (newQuery.isBlank()) {
                    viewModel.search("")
                } else if (newQuery.length >= 2) {
                    viewModel.searchDebounced(newQuery)
                }
            },
            isLoading = isLoading,
            isEmpty = isEmpty,
            results = results,
            recentQueries = recentQueries,
            isTvLayout = true,
            onResultClick = ::onResultClick,
            onResultLongClick = ::onResultLongClick,
            onBrowseGenres = { findNavController().navigate(R.id.genres_hub) },
            onRecentClick = { recent ->
                query = recent
                viewModel.search(recent)
            },
            onClearHistory = {
                com.betterstreamflix.search.SearchHistoryManager.clearHistory(requireContext())
                recentQueries = emptyList()
            },
        )
    }

    private fun onResultClick(item: AppAdapter.Item) {
        when (item) {
            is Genre -> {
                findNavController().navigate(
                    R.id.genre,
                    android.os.Bundle().apply {
                        putString("id", item.id)
                        putString("name", item.name)
                    },
                )
            }
            is Movie -> {
                switchProviderIfNeeded(item.providerName)
                findNavController().navigate(
                    SearchTvFragmentDirections.actionSearchToMovie(id = item.id),
                )
            }
            is TvShow -> {
                switchProviderIfNeeded(item.providerName)
                findNavController().navigate(
                    SearchTvFragmentDirections.actionSearchToTvShow(
                        id = item.id,
                        poster = item.poster,
                        banner = item.banner,
                    ),
                )
            }
        }
    }

    private fun onResultLongClick(item: AppAdapter.Item) {
        when (item) {
            is Movie, is TvShow ->
                ShowOptionsDialog(requireContext(), item, isTv = true).show()
            else -> Unit
        }
    }

    private fun switchProviderIfNeeded(providerName: String?) {
        val targetName = providerName?.takeIf { it.isNotBlank() } ?: return
        if (targetName == UserPreferences.currentProvider?.name) return

        val targetProvider = Provider.providers.keys.find { it.name == targetName } ?: return
        UserPreferences.currentProvider = targetProvider
        Toast.makeText(
            requireContext(),
            getString(R.string.switching_to_provider, targetName),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
