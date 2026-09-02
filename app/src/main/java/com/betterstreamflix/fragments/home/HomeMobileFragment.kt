package com.betterstreamflix.fragments.home

import android.os.Bundle
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
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.HomeScreen
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.fragments.home.HomeMobileFragmentDirections
import com.betterstreamflix.fragments.movie.MovieMobileFragmentDirections
import com.betterstreamflix.fragments.tv_show.TvShowMobileFragmentDirections
import com.betterstreamflix.models.Video
import com.betterstreamflix.ui.ShowOptionsMobileDialog
import com.betterstreamflix.models.Category
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.providers.Provider
import com.betterstreamflix.utils.CacheUtils
import com.betterstreamflix.utils.DeepLinkHandler
import com.betterstreamflix.utils.ProviderChangeNotifier
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.format
import kotlinx.coroutines.launch
import retrofit2.HttpException

class HomeMobileFragment : ComposeHostFragment() {

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
    override fun ScreenContent() {
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
                    onItemClick = ::onItemClick,
                    onItemLongClick = ::onItemLongClick,
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

    private fun onItemClick(item: AppAdapter.Item, fromContinueWatching: Boolean) {
        when (item) {
            is Movie -> {
                switchProviderIfNeeded(item.providerName)
                if (fromContinueWatching && item.watchHistory != null) {
                    findNavController().navigate(
                        HomeMobileFragmentDirections.actionHomeToMovie(id = item.id),
                    )
                    findNavController().navigate(
                        MovieMobileFragmentDirections.actionMovieToPlayer(
                            id = item.id,
                            title = item.title,
                            subtitle = item.released?.format("yyyy") ?: "",
                            videoType = Video.Type.Movie(
                                id = item.id,
                                title = item.title,
                                releaseDate = item.released?.format("yyyy-MM-dd") ?: "",
                                poster = item.poster ?: item.banner ?: "",
                                imdbId = item.imdbId,
                            ),
                        ),
                    )
                } else {
                    findNavController().navigate(
                        HomeMobileFragmentDirections.actionHomeToMovie(id = item.id),
                    )
                }
            }
            is TvShow -> {
                switchProviderIfNeeded(item.providerName)
                findNavController().navigate(
                    HomeMobileFragmentDirections.actionHomeToTvShow(
                        id = item.id,
                        poster = item.poster,
                        banner = item.banner,
                    ),
                )
            }
            is Episode -> {
                switchProviderIfNeeded(item.tvShow?.providerName)
                val tvShowId = item.tvShow?.id ?: return
                findNavController().navigate(
                    HomeMobileFragmentDirections.actionHomeToTvShow(
                        id = tvShowId,
                        poster = item.tvShow?.poster,
                        banner = item.tvShow?.banner,
                    ),
                )
                findNavController().navigate(
                    TvShowMobileFragmentDirections.actionTvShowToPlayer(
                        id = item.id,
                        title = item.tvShow?.title.orEmpty(),
                        subtitle = item.season?.takeIf { it.number != 0 }?.let { season ->
                            getString(
                                R.string.player_subtitle_tv_show,
                                season.number,
                                item.number,
                                item.title ?: getString(R.string.episode_number, item.number),
                            )
                        } ?: getString(
                            R.string.player_subtitle_tv_show_episode_only,
                            item.number,
                            item.title ?: getString(R.string.episode_number, item.number),
                        ),
                        videoType = com.betterstreamflix.models.Video.Type.Episode(
                            id = item.id,
                            number = item.number,
                            title = item.title,
                            poster = item.poster,
                            overview = item.overview,
                            tvShow = com.betterstreamflix.models.Video.Type.Episode.TvShow(
                                id = tvShowId,
                                title = item.tvShow?.title.orEmpty(),
                                poster = item.tvShow?.poster,
                                banner = item.tvShow?.banner,
                                releaseDate = item.tvShow?.released?.format("yyyy-MM-dd"),
                                imdbId = item.tvShow?.imdbId,
                            ),
                            season = com.betterstreamflix.models.Video.Type.Episode.Season(
                                number = item.season?.number ?: 0,
                                title = item.season?.title,
                            ),
                        ),
                    ),
                )
            }
        }
    }

    private fun onItemLongClick(item: AppAdapter.Item) {
        when (item) {
            is Movie -> ShowOptionsMobileDialog(requireContext(), item).show()
            is TvShow -> ShowOptionsMobileDialog(requireContext(), item).show()
            is Episode -> ShowOptionsMobileDialog(requireContext(), item).show()
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
