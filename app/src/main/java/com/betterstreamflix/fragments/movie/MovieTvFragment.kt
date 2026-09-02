package com.betterstreamflix.fragments.movie

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.betterstreamflix.R
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.MediaDetailScreen
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.download.DownloadEnqueueHelper
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.People
import com.betterstreamflix.models.Show
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.models.Video
import com.betterstreamflix.utils.CacheUtils
import com.betterstreamflix.utils.format
import com.betterstreamflix.utils.viewModelsFactory
import kotlinx.coroutines.launch
import retrofit2.HttpException

class MovieTvFragment : ComposeHostFragment() {

    private var hasAutoCleared409 = false
    private val args by navArgs<MovieTvFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { MovieViewModel(args.id, database) }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle(initialValue = MovieViewModel.State.Loading)
        val movie = (state as? MovieViewModel.State.SuccessLoading)?.movie

        if (state is MovieViewModel.State.FailedLoading) {
            val error = (state as MovieViewModel.State.FailedLoading).error
            val code = (error as? HttpException)?.code()
            if (code == 409 && !hasAutoCleared409) {
                hasAutoCleared409 = true
                androidx.compose.runtime.LaunchedEffect(error) {
                    CacheUtils.clearAppCache(requireContext())
                    Toast.makeText(requireContext(), R.string.clear_cache_done_409, Toast.LENGTH_SHORT).show()
                    viewModel.getMovie(args.id)
                }
            }
        }

        val watchProgress = movie?.watchHistory?.let { history ->
            if (history.durationMillis > 0L) {
                (history.lastPlaybackPositionMillis.toFloat() / history.durationMillis).coerceIn(0f, 1f)
            } else null
        }

        MediaDetailScreen(
            title = movie?.title.orEmpty(),
            bannerUrl = movie?.banner ?: movie?.poster,
            overview = movie?.overview,
            metaLine = movieMetaLine(movie),
            genresLine = movie?.genres?.joinToString(", ") { it.name },
            watchLabel = stringResource(R.string.movie_watch_now),
            watchProgress = watchProgress,
            showWatchButton = movie != null,
            showDownloadButton = movie != null,
            cast = movie?.cast.orEmpty(),
            directors = movie?.directors.orEmpty(),
            recommendations = movie?.recommendations.orEmpty(),
            isLoading = state is MovieViewModel.State.Loading,
            errorMessage = (state as? MovieViewModel.State.FailedLoading)?.error?.message,
            isTvLayout = true,
            onWatch = { movie?.let(::openPlayer) },
            onDownload = { movie?.let(::requestDownload) },
            onCastClick = ::openPerson,
            onRecommendationClick = ::openRecommendation,
            onRetry = { viewModel.getMovie(args.id) },
        )
    }

    private fun movieMetaLine(movie: Movie?): String? {
        if (movie == null) return null
        return listOfNotNull(
            movie.released?.format("yyyy"),
            movie.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
            movie.runtime?.let { getString(R.string.tv_show_runtime_minutes, it) },
        ).joinToString("  •  ").takeIf { it.isNotBlank() }
    }

    private fun openPlayer(movie: Movie) {
        findNavController().navigate(
            MovieTvFragmentDirections.actionMovieToPlayer(
                id = movie.id,
                title = movie.title,
                subtitle = movie.released?.format("yyyy") ?: "",
                videoType = Video.Type.Movie(
                    id = movie.id,
                    title = movie.title,
                    releaseDate = movie.released?.format("yyyy-MM-dd") ?: "",
                    poster = movie.poster ?: movie.banner ?: "",
                    imdbId = movie.imdbId,
                ),
            ),
        )
    }

    private fun openPerson(person: People) {
        findNavController().navigate(
            MovieTvFragmentDirections.actionMovieToPeople(
                id = person.id,
                name = person.name,
                image = person.image,
            ),
        )
    }

    private fun openRecommendation(show: Show) {
        when (show) {
            is Movie -> findNavController().navigate(MovieTvFragmentDirections.actionMovieToMovie(id = show.id))
            is TvShow -> findNavController().navigate(
                MovieTvFragmentDirections.actionMovieToTvShow(
                    id = show.id,
                    poster = show.poster,
                    banner = show.banner,
                ),
            )
        }
    }

    fun requestDownload(movie: Movie) {
        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), R.string.download_starting, Toast.LENGTH_SHORT).show()
            DownloadEnqueueHelper.enqueueMovie(requireContext(), movie)
        }
    }
}
