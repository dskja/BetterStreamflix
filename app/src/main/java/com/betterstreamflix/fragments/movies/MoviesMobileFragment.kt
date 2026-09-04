package com.betterstreamflix.fragments.movies

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.betterstreamflix.R
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.MoviesScreen
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Movie
import com.betterstreamflix.utils.CacheUtils
import com.betterstreamflix.utils.viewModelsFactory
import retrofit2.HttpException

class MoviesMobileFragment : ComposeHostFragment() {

    private var hasAutoCleared409 = false
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { MoviesViewModel(database) }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle(initialValue = MoviesViewModel.State.Loading)

        val movies = when (val current = state) {
            is MoviesViewModel.State.SuccessLoading -> current.movies
            is MoviesViewModel.State.LoadingMore -> current.movies
            else -> emptyList()
        }
        val hasMore = when (val current = state) {
            is MoviesViewModel.State.SuccessLoading -> current.hasMore
            is MoviesViewModel.State.LoadingMore -> current.hasMore
            else -> false
        }

        if (state is MoviesViewModel.State.FailedLoading) {
            val error = (state as MoviesViewModel.State.FailedLoading).error
            val code = (error as? HttpException)?.code()
            if (code == 409 && !hasAutoCleared409) {
                hasAutoCleared409 = true
                androidx.compose.runtime.LaunchedEffect(error) {
                    CacheUtils.clearAppCache(requireContext())
                    Toast.makeText(requireContext(), R.string.clear_cache_done_409, Toast.LENGTH_SHORT).show()
                    viewModel.getMovies()
                }
            }
        }

        MoviesScreen(
            movies = movies,
            isLoading = state is MoviesViewModel.State.Loading,
            isLoadingMore = state is MoviesViewModel.State.LoadingMore,
            hasMore = hasMore,
            errorMessage = (state as? MoviesViewModel.State.FailedLoading)?.error?.message,
            onMovieClick = ::openMovie,
            onLoadMore = { viewModel.loadMoreMovies() },
            onRetry = { viewModel.getMovies() },
        )
    }

    private fun openMovie(movie: Movie) {
        findNavController().navigate(
            MoviesMobileFragmentDirections.actionMoviesToMovie(id = movie.id),
        )
    }
}
