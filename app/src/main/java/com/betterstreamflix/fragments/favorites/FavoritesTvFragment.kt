package com.betterstreamflix.fragments.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.betterstreamflix.R
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsPrimaryButton
import com.betterstreamflix.compose.screens.FavoritesScreen
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.viewModelsFactory

class FavoritesTvFragment : ComposeHostFragment() {

    private val database by lazy { AppDatabase.getInstanceOrNull(requireContext()) }
    private val providerName get() = UserPreferences.currentProvider?.name.orEmpty()
    private val viewModel by viewModelsFactory {
        FavoritesViewModel(
            requireNotNull(database) { "Current provider is not set" },
            providerName,
        )
    }

    @Composable
    override fun ScreenContent() {
        if (database == null || UserPreferences.currentProvider == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BsEmptyState(message = stringResource(R.string.providers_choose_title))
                    BsPrimaryButton(
                        text = stringResource(R.string.main_menu_change_provider),
                        onClick = { findNavController().navigate(R.id.providers) },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            return
        }

        val sections by viewModel.sections.collectAsStateWithLifecycle(initialValue = emptyList())
        var sortMode by remember { mutableStateOf(viewModel.currentSortMode()) }

        FavoritesScreen(
            sections = sections,
            sortMode = sortMode,
            isTvLayout = true,
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
            FavoritesTvFragmentDirections.actionFavoritesToMovie(id = movie.id),
        )
    }

    private fun navigateToTvShow(tvShow: TvShow) {
        findNavController().navigate(
            FavoritesTvFragmentDirections.actionFavoritesToTvShow(
                id = tvShow.id,
                poster = tvShow.poster,
                banner = tvShow.banner,
            ),
        )
    }
}
