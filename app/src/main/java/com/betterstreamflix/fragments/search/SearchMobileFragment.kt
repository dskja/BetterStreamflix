package com.betterstreamflix.fragments.search

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.betterstreamflix.R
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.SearchScreen
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.providers.Provider
import com.betterstreamflix.utils.CacheUtils
import com.betterstreamflix.utils.DeepLinkHandler
import com.betterstreamflix.utils.LoggingUtils
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.viewModelsFactory
import retrofit2.HttpException

class SearchMobileFragment : ComposeHostFragment() {

    private var hasAutoCleared409: Boolean = false

    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { SearchViewModel(database) }

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        DeepLinkHandler.pendingSearchQuery?.let { pending ->
            DeepLinkHandler.pendingSearchQuery = null
            if (pending.isNotBlank() && pending != "_") {
                viewModel.search(pending)
            }
        }
    }

    @Composable
    override fun Content() {
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
            onBack = { findNavController().popBackStack() },
            onBrowseGenres = {
                findNavController().navigate(R.id.genres_hub)
            },
            onResultClick = ::onResultClick,
        )
    }

    private fun onResultClick(item: AppAdapter.Item) {
        when (item) {
            is Movie -> {
                switchProviderIfNeeded(item.providerName)
                findNavController().navigate(
                    SearchMobileFragmentDirections.actionSearchToMovie(id = item.id),
                )
            }
            is TvShow -> {
                switchProviderIfNeeded(item.providerName)
                findNavController().navigate(
                    SearchMobileFragmentDirections.actionSearchToTvShow(
                        id = item.id,
                        poster = item.poster,
                        banner = item.banner,
                    ),
                )
            }
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
