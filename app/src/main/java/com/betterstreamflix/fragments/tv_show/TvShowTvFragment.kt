package com.betterstreamflix.fragments.tv_show

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.betterstreamflix.R
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.MediaDetailScreen
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.People
import com.betterstreamflix.models.Season
import com.betterstreamflix.models.Show
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.models.Video
import com.betterstreamflix.ui.ShowOptionsActions
import com.betterstreamflix.utils.CacheUtils
import com.betterstreamflix.utils.format
import com.betterstreamflix.utils.viewModelsFactory
import retrofit2.HttpException

class TvShowTvFragment : ComposeHostFragment() {

    private var hasAutoCleared409 = false
    private val args by navArgs<TvShowTvFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory {
        TvShowViewModel(
            id = args.id,
            database = database,
            fallbackPoster = args.poster,
            fallbackBanner = args.banner,
        )
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle(initialValue = TvShowViewModel.State.Loading)
        val tvShow = (state as? TvShowViewModel.State.SuccessLoading)?.tvShow

        if (state is TvShowViewModel.State.FailedLoading) {
            val error = (state as TvShowViewModel.State.FailedLoading).error
            val code = (error as? HttpException)?.code()
            if (code == 409 && !hasAutoCleared409) {
                hasAutoCleared409 = true
                androidx.compose.runtime.LaunchedEffect(error) {
                    CacheUtils.clearAppCache(requireContext())
                    Toast.makeText(requireContext(), R.string.clear_cache_done_409, Toast.LENGTH_SHORT).show()
                    viewModel.getTvShow(args.id)
                }
            }
        }

        val episode = tvShow?.episodeToWatch
        val episodeSeason = tvShow?.let { resolveEpisodeSeason(it, episode) }
        val watchProgress = episode?.watchHistory?.let { history ->
            if (history.durationMillis > 0L) {
                (history.lastPlaybackPositionMillis.toFloat() / history.durationMillis).coerceIn(0f, 1f)
            } else null
        }

        MediaDetailScreen(
            title = tvShow?.title.orEmpty(),
            bannerUrl = tvShow?.banner ?: tvShow?.poster,
            overview = tvShow?.overview,
            metaLine = tvShowMetaLine(tvShow),
            genresLine = tvShow?.genres?.joinToString(", ") { it.name },
            watchLabel = watchLabel(episodeSeason, episode),
            watchProgress = watchProgress,
            showWatchButton = episode != null,
            isFavorite = tvShow?.isFavorite == true,
            showFavoriteButton = tvShow != null,
            cast = tvShow?.cast.orEmpty(),
            directors = tvShow?.directors.orEmpty(),
            seasons = tvShow?.seasons.orEmpty(),
            recommendations = tvShow?.recommendations.orEmpty(),
            isLoading = state is TvShowViewModel.State.Loading,
            errorMessage = (state as? TvShowViewModel.State.FailedLoading)?.error?.message,
            isTvLayout = true,
            onWatch = { tvShow?.let { openPlayer(it, episode, episodeSeason) } },
            onToggleFavorite = {
                tvShow?.let { ShowOptionsActions.toggleFavorite(requireContext(), it, viewLifecycleOwner.lifecycleScope) }
            },
            onCastClick = ::openPerson,
            onSeasonClick = { season -> tvShow?.let { openSeason(it, season) } },
            onRecommendationClick = ::openRecommendation,
            onRetry = { viewModel.getTvShow(args.id) },
        )
    }

    private fun tvShowMetaLine(tvShow: TvShow?): String? {
        if (tvShow == null) return null
        return listOfNotNull(
            tvShow.released?.format("yyyy"),
            tvShow.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
            tvShow.runtime?.let { minutes ->
                val hours = minutes / 60
                val rem = minutes % 60
                if (hours > 0) getString(R.string.tv_show_runtime_hours_minutes, hours, rem)
                else getString(R.string.tv_show_runtime_minutes, minutes)
            },
        ).joinToString("  •  ").takeIf { it.isNotBlank() }
    }

    private fun watchLabel(season: Season?, episode: Episode?): String? {
        if (episode == null) return null
        val episodeNumber = episode.number
        val seasonNumber = season?.number ?: -1
        return when {
            seasonNumber == 0 || season?.title.equals("Filme", ignoreCase = true) -> {
                episode.title?.takeIf { it.isNotBlank() }
                    ?.let { getString(R.string.tv_show_watch_title, it) }
                    ?: getString(R.string.tv_show_watch_episode, episodeNumber)
            }
            seasonNumber > 0 -> getString(R.string.tv_show_watch_season_episode, seasonNumber, episodeNumber)
            else -> getString(R.string.tv_show_watch_episode, episodeNumber)
        }
    }

    private fun resolveEpisodeSeason(tvShow: TvShow, episode: Episode?): Season? {
        if (episode == null) return null
        val currentSeason = episode.season
        val seasonKey = episode.id.substringBeforeLast("/", "").takeIf { it.isNotBlank() }
        if (currentSeason != null && currentSeason.number != 0) return currentSeason
        return tvShow.seasons.firstOrNull { season ->
            season.id == seasonKey ||
                season.id == currentSeason?.id ||
                season.episodes.any { it.id == episode.id }
        } ?: currentSeason
    }

    private fun openPlayer(tvShow: TvShow, episode: Episode?, season: Season?) {
        val ep = episode ?: return
        findNavController().navigate(
            TvShowTvFragmentDirections.actionTvShowToPlayer(
                id = ep.id,
                title = tvShow.title,
                subtitle = episodeSubtitle(season, ep),
                videoType = Video.Type.Episode(
                    id = ep.id,
                    number = ep.number,
                    title = ep.title,
                    poster = ep.poster,
                    overview = ep.overview,
                    tvShow = Video.Type.Episode.TvShow(
                        id = tvShow.id,
                        title = tvShow.title,
                        poster = tvShow.poster,
                        banner = tvShow.banner,
                        releaseDate = tvShow.released?.format("yyyy-MM-dd"),
                        imdbId = tvShow.imdbId,
                    ),
                    season = Video.Type.Episode.Season(
                        number = season?.number?.takeIf { it > 0 } ?: 1,
                        title = season?.title,
                    ),
                ),
            ),
        )
    }

    private fun episodeSubtitle(season: Season?, episode: Episode): String {
        val seasonNumber = season?.number ?: -1
        return when {
            seasonNumber == 0 || season?.title.equals("Filme", ignoreCase = true) -> episode.title.orEmpty()
            seasonNumber > 0 -> "S$seasonNumber E${episode.number}  •  ${episode.title}"
            else -> "E${episode.number}  •  ${episode.title}"
        }
    }

    private fun openSeason(tvShow: TvShow, season: Season) {
        findNavController().navigate(
            TvShowTvFragmentDirections.actionTvShowToSeason(
                tvShowId = tvShow.id,
                tvShowTitle = tvShow.title,
                tvShowPoster = tvShow.poster,
                tvShowBanner = tvShow.banner,
                seasonId = season.id,
                seasonNumber = season.number,
                seasonTitle = season.title,
            ),
        )
    }

    private fun openPerson(person: People) {
        findNavController().navigate(
            TvShowTvFragmentDirections.actionTvShowToPeople(
                id = person.id,
                name = person.name,
                image = person.image,
            ),
        )
    }

    private fun openRecommendation(show: Show) {
        when (show) {
            is Movie -> findNavController().navigate(TvShowTvFragmentDirections.actionTvShowToMovie(id = show.id))
            is TvShow -> findNavController().navigate(
                TvShowTvFragmentDirections.actionTvShowToTvShow(
                    id = show.id,
                    poster = show.poster,
                    banner = show.banner,
                ),
            )
        }
    }
}
