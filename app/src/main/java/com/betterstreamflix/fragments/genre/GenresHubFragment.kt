package com.betterstreamflix.fragments.genre

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.betterstreamflix.R
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.GenresHubScreen
import com.betterstreamflix.models.Genre

class GenresHubFragment : ComposeHostFragment() {

    private val viewModel by viewModels<GenresHubViewModel>()

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadGenres()
    }

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsStateWithLifecycle(initialValue = GenresHubViewModel.State.Loading)

        if (state is GenresHubViewModel.State.Failed) {
            val error = (state as GenresHubViewModel.State.Failed).error
            LaunchedEffect(error) {
                Toast.makeText(
                    requireContext(),
                    error.message ?: getString(R.string.loading_error_generic),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        GenresHubScreen(
            genres = (state as? GenresHubViewModel.State.Success)?.genres.orEmpty(),
            isLoading = state is GenresHubViewModel.State.Loading,
            errorMessage = (state as? GenresHubViewModel.State.Failed)?.error?.message,
            onGenreClick = ::openGenre,
            onRetry = { viewModel.loadGenres() },
        )
    }

    private fun openGenre(genre: Genre) {
        findNavController().navigate(
            GenresHubFragmentDirections.actionGenresHubToGenre(
                id = genre.id,
                name = genre.name,
            ),
        )
    }
}
