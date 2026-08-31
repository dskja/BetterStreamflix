package com.betterstreamflix.fragments.home

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.betterstreamflix.R
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.HomeScreen
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Category
import com.betterstreamflix.utils.CacheUtils
import com.betterstreamflix.utils.DeepLinkHandler
import com.betterstreamflix.utils.ProviderChangeNotifier
import com.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.launch
import retrofit2.HttpException

class HomeTvFragment : ComposeHostFragment() {

    private var hasAutoCleared409: Boolean = false

    private val viewModel: HomeViewModel by lazy {
        val providerKey = UserPreferences.currentProvider?.name ?: "default"
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(AppDatabase.getInstance(requireContext())) as T
            }
        }
        ViewModelProvider(this, factory).get(providerKey, HomeViewModel::class.java)
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            ProviderChangeNotifier.providerChangeFlow
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { viewModel.getHome() }
        }
        viewModel.getHome()
    }

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsStateWithLifecycle(initialValue = HomeViewModel.State.Loading)
        var scrollToCategory by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(state) {
            if (state is HomeViewModel.State.SuccessLoading && DeepLinkHandler.pendingOpenContinueWatching) {
                DeepLinkHandler.pendingOpenContinueWatching = false
                scrollToCategory = getString(R.string.home_continue_watching)
            }
        }

        when (val current = state) {
            HomeViewModel.State.Loading -> {
                HomeScreen(isLoading = true)
            }
            is HomeViewModel.State.SuccessLoading -> {
                if (current.isStaleCache) {
                    LaunchedEffect(Unit) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.home_cached_content_banner),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                HomeScreen(
                    categories = localizeCategories(current.categories),
                    scrollToCategoryName = scrollToCategory,
                    onProviderClick = { findNavController().navigate(R.id.providers) },
                )
            }
            is HomeViewModel.State.FailedLoading -> {
                val code = (current.error as? HttpException)?.code()
                if (code == 409 && !hasAutoCleared409) {
                    LaunchedEffect(Unit) {
                        hasAutoCleared409 = true
                        CacheUtils.clearAppCache(requireContext())
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.clear_cache_done_409),
                            Toast.LENGTH_SHORT,
                        ).show()
                        viewModel.getHome()
                    }
                } else {
                    val message = current.error.message?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.loading_error_generic)
                    LaunchedEffect(current.error) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                    HomeScreen(
                        isLoading = false,
                        errorMessage = message,
                        onRetry = { viewModel.getHome() },
                    )
                }
            }
        }
    }

    /** No-op stubs kept for legacy TV view holders that still type-check against [HomeTvFragment]. */
    fun updateBackground(uri: String?, swiperHasFocus: Boolean? = false) = Unit

    fun pinBackground(uri: String?) = Unit

    fun releasePinnedBackground() = Unit

    fun resetSwiperSchedule() = Unit

    private fun localizeCategories(categories: List<Category>): List<Category> = categories.map { category ->
        val localizedName = when (category.name) {
            Category.CONTINUE_WATCHING -> getString(R.string.home_continue_watching)
            Category.RECENTLY_WATCHED -> getString(R.string.home_recently_watched)
            Category.RECOMMENDED_FOR_YOU -> getString(R.string.home_recommended_for_you)
            Category.FAVORITE_MOVIES -> getString(R.string.home_favorite_movies)
            Category.FAVORITE_TV_SHOWS -> getString(R.string.home_favorite_tv_shows)
            else -> category.name
        }
        category.copy(name = localizedName)
    }
}
