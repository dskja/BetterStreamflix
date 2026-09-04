package com.betterstreamflix.fragments.genre

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.betterstreamflix.R
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.GenreScreen
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.Show
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.ui.ShowOptionsDialog
import com.betterstreamflix.utils.CacheUtils
import com.betterstreamflix.utils.viewModelsFactory
import retrofit2.HttpException

class GenreTvFragment : ComposeHostFragment() {

    private var hasAutoCleared409 = false
    private val args by navArgs<GenreTvFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { GenreViewModel(args.id, database) }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle(initialValue = GenreViewModel.State.Loading)
        val genre = when (val current = state) {
            is GenreViewModel.State.SuccessLoading -> current.genre
            is GenreViewModel.State.LoadingMore -> current.genre
            else -> null
        }
        val hasMore = when (val current = state) {
            is GenreViewModel.State.SuccessLoading -> current.hasMore
            is GenreViewModel.State.LoadingMore -> current.hasMore
            else -> false
        }

        if (state is GenreViewModel.State.FailedLoading) {
            val error = (state as GenreViewModel.State.FailedLoading).error
            val code = (error as? HttpException)?.code()
            if (code == 409 && !hasAutoCleared409) {
                hasAutoCleared409 = true
                androidx.compose.runtime.LaunchedEffect(error) {
                    CacheUtils.clearAppCache(requireContext())
                    Toast.makeText(requireContext(), R.string.clear_cache_done_409, Toast.LENGTH_SHORT).show()
                    viewModel.getGenre(args.id)
                }
            }
        }

        GenreScreen(
            genre = genre,
            isLoading = state is GenreViewModel.State.Loading,
            isLoadingMore = state is GenreViewModel.State.LoadingMore,
            hasMore = hasMore,
            errorMessage = (state as? GenreViewModel.State.FailedLoading)?.error?.message,
            isTvLayout = true,
            onShowClick = ::openShow,
            onShowLongClick = ::onShowLongClick,
            onLoadMore = { viewModel.loadMoreGenreShows() },
            onRetry = { viewModel.getGenre(args.id) },
        )
    }

    private fun openShow(show: Show) {
        when (show) {
            is Movie -> findNavController().navigate(GenreTvFragmentDirections.actionGenreToMovie(id = show.id))
            is TvShow -> findNavController().navigate(
                GenreTvFragmentDirections.actionGenreToTvShow(
                    id = show.id,
                    poster = show.poster,
                    banner = show.banner,
                ),
            )
        }
    }

    private fun onShowLongClick(show: Show) {
        when (show) {
            is Movie, is TvShow ->
                ShowOptionsDialog(requireContext(), show, isTv = true).show()
            else -> Unit
        }
    }
}
