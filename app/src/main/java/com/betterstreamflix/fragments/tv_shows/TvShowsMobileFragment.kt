package com.betterstreamflix.fragments.tv_shows

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.betterstreamflix.R
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.TvShowsScreen
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.utils.CacheUtils
import com.betterstreamflix.utils.viewModelsFactory
import retrofit2.HttpException

class TvShowsMobileFragment : ComposeHostFragment() {

    private var hasAutoCleared409 = false
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { TvShowsViewModel(database) }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle(initialValue = TvShowsViewModel.State.Loading)

        val shows = (state as? TvShowsViewModel.State.SuccessLoading)?.tvShows.orEmpty()
        val hasMore = (state as? TvShowsViewModel.State.SuccessLoading)?.hasMore == true

        if (state is TvShowsViewModel.State.FailedLoading) {
            val error = (state as TvShowsViewModel.State.FailedLoading).error
            val code = (error as? HttpException)?.code()
            if (code == 409 && !hasAutoCleared409) {
                hasAutoCleared409 = true
                androidx.compose.runtime.LaunchedEffect(error) {
                    CacheUtils.clearAppCache(requireContext())
                    Toast.makeText(requireContext(), R.string.clear_cache_done_409, Toast.LENGTH_SHORT).show()
                    viewModel.getTvShows()
                }
            }
        }

        TvShowsScreen(
            shows = shows,
            isLoading = state is TvShowsViewModel.State.Loading,
            isLoadingMore = state is TvShowsViewModel.State.LoadingMore,
            hasMore = hasMore,
            errorMessage = (state as? TvShowsViewModel.State.FailedLoading)?.error?.message,
            onShowClick = ::openShow,
            onLoadMore = { viewModel.loadMoreTvShows() },
            onRetry = { viewModel.getTvShows() },
        )
    }

    private fun openShow(show: TvShow) {
        findNavController().navigate(
            TvShowsMobileFragmentDirections.actionTvShowsToTvShow(
                id = show.id,
                poster = show.poster,
                banner = show.banner,
            ),
        )
    }
}
