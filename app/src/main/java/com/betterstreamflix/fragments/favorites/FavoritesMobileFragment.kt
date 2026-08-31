package com.betterstreamflix.fragments.favorites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.FavoritesScreen
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.viewModelsFactory

class FavoritesMobileFragment : ComposeHostFragment() {

    private val providerName get() = UserPreferences.currentProvider?.name.orEmpty()
    private val viewModel by viewModelsFactory {
        FavoritesViewModel(AppDatabase.getInstance(requireContext()), providerName)
    }

    @Composable
    override fun Content() {
        val sections by viewModel.sections.collectAsStateWithLifecycle(initialValue = emptyList())
        var sortMode by remember { mutableStateOf(viewModel.currentSortMode()) }

        FavoritesScreen(
            sections = sections,
            sortMode = sortMode,
            onSortModeChange = { mode ->
                viewModel.setSortMode(mode)
                sortMode = mode
            },
            onMovieClick = { movie -> navigateToMovie(movie) },
            onTvShowClick = { tvShow -> navigateToTvShow(tvShow) },
        )
    }

    private fun navigateToMovie(movie: Movie) {
        findNavController().navigate(
            FavoritesMobileFragmentDirections.actionFavoritesToMovie(movie.id),
        )
    }

    private fun navigateToTvShow(tvShow: TvShow) {
        findNavController().navigate(
            FavoritesMobileFragmentDirections.actionFavoritesToTvShow(tvShow.id),
        )
    }
}
