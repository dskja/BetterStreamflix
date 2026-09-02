package com.betterstreamflix.fragments.people

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.betterstreamflix.R
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.PeopleScreen
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.Show
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.utils.CacheUtils
import com.betterstreamflix.utils.viewModelsFactory
import retrofit2.HttpException

class PeopleTvFragment : ComposeHostFragment() {

    private var hasAutoCleared409 = false
    private val args by navArgs<PeopleTvFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { PeopleViewModel(args.id, database) }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle(initialValue = PeopleViewModel.State.Loading)
        val people = (state as? PeopleViewModel.State.SuccessLoading)?.people
        val hasMore = (state as? PeopleViewModel.State.SuccessLoading)?.hasMore == true

        if (state is PeopleViewModel.State.FailedLoading) {
            val error = (state as PeopleViewModel.State.FailedLoading).error
            val code = (error as? HttpException)?.code()
            if (code == 409 && !hasAutoCleared409) {
                hasAutoCleared409 = true
                androidx.compose.runtime.LaunchedEffect(error) {
                    CacheUtils.clearAppCache(requireContext())
                    Toast.makeText(requireContext(), R.string.clear_cache_done_409, Toast.LENGTH_SHORT).show()
                    viewModel.getPeople(args.id)
                }
            }
        }

        PeopleScreen(
            people = people,
            fallbackName = args.name,
            fallbackImage = args.image,
            isLoading = state is PeopleViewModel.State.Loading,
            isLoadingMore = state is PeopleViewModel.State.LoadingMore,
            hasMore = hasMore,
            errorMessage = (state as? PeopleViewModel.State.FailedLoading)?.error?.message,
            isTvLayout = true,
            onFilmographyClick = ::openShow,
            onLoadMore = { viewModel.loadMorePeopleFilmography() },
            onRetry = { viewModel.getPeople(args.id) },
        )
    }

    private fun openShow(show: Show) {
        when (show) {
            is Movie -> findNavController().navigate(PeopleTvFragmentDirections.actionPeopleToMovie(id = show.id))
            is TvShow -> findNavController().navigate(
                PeopleTvFragmentDirections.actionPeopleToTvShow(
                    id = show.id,
                    poster = show.poster,
                    banner = show.banner,
                ),
            )
        }
    }
}
