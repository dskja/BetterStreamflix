package com.betterstreamflix.fragments.season

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.betterstreamflix.R
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.SeasonScreen
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.download.DownloadEnqueueHelper
import com.betterstreamflix.download.DownloadManager
import com.betterstreamflix.download.DownloadRepository
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Video
import com.betterstreamflix.ui.ShowOptionsDialog
import com.betterstreamflix.utils.CacheUtils
import com.betterstreamflix.utils.format
import com.betterstreamflix.utils.viewModelsFactory
import kotlinx.coroutines.launch
import retrofit2.HttpException

class SeasonTvFragment : ComposeHostFragment() {

    private var hasAutoCleared409 = false
    private val args by navArgs<SeasonTvFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val downloadRepository by lazy { DownloadRepository(requireContext()) }
    private val viewModel by viewModelsFactory {
        SeasonViewModel(args.seasonId, args.tvShowId, database)
    }

    private var allEpisodes: List<Episode> = emptyList()

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle(initialValue = SeasonViewModel.State.LoadingEpisodes)
        val downloads by downloadRepository.observeTasks()
            .collectAsStateWithLifecycle(initialValue = emptyList())

        val episodes = (state as? SeasonViewModel.State.SuccessLoadingEpisodes)?.episodes.orEmpty()
        if (episodes.isNotEmpty()) allEpisodes = episodes

        val downloadStatusByEpisodeId = remember(downloads, episodes) {
            episodes.associate { episode ->
                episode.id to downloads.firstOrNull {
                    it.videoId == episode.id &&
                        it.status != DownloadManager.DownloadStatus.CANCELLED
                }?.status
            }
        }

        androidx.compose.runtime.LaunchedEffect(state) {
            if (state is SeasonViewModel.State.FailedLoadingEpisodes) {
                val error = (state as SeasonViewModel.State.FailedLoadingEpisodes).error
                val code = (error as? HttpException)?.code()
                if (code == 409 && !hasAutoCleared409) {
                    hasAutoCleared409 = true
                    CacheUtils.clearAppCache(requireContext())
                    Toast.makeText(requireContext(), R.string.clear_cache_done_409, Toast.LENGTH_SHORT).show()
                    viewModel.getSeasonEpisodes(args.seasonId)
                }
            }
        }

        SeasonScreen(
            seasonTitle = args.seasonTitle.orEmpty().ifBlank { getString(R.string.season_number, args.seasonNumber) },
            episodes = episodes,
            query = "",
            onQueryChange = {},
            isLoading = state is SeasonViewModel.State.LoadingEpisodes,
            errorMessage = (state as? SeasonViewModel.State.FailedLoadingEpisodes)?.error?.message,
            downloadStatusByEpisodeId = downloadStatusByEpisodeId,
            onRetry = { viewModel.getSeasonEpisodes(args.seasonId) },
            onEpisodeClick = ::openEpisode,
            onEpisodeLongClick = { episode ->
                ShowOptionsDialog(requireContext(), episode, isTv = true).show()
            },
            onDownloadEpisode = ::requestDownload,
            onDownloadSeason = ::requestDownloadSeason,
            isTvLayout = true,
        )
    }

    private fun openEpisode(episode: Episode) {
        val season = episode.season
        val subtitle = season?.takeIf { it.number != 0 }?.let { s ->
            getString(
                R.string.player_subtitle_tv_show,
                s.number,
                episode.number,
                episode.title ?: getString(R.string.episode_number, episode.number),
            )
        } ?: getString(
            R.string.player_subtitle_tv_show_episode_only,
            episode.number,
            episode.title ?: getString(R.string.episode_number, episode.number),
        )
        findNavController().navigate(
            SeasonTvFragmentDirections.actionSeasonToPlayer(
                id = episode.id,
                title = episode.tvShow?.title ?: "",
                subtitle = subtitle,
                videoType = Video.Type.Episode(
                    id = episode.id,
                    number = episode.number,
                    title = episode.title,
                    poster = episode.poster,
                    overview = episode.overview,
                    tvShow = Video.Type.Episode.TvShow(
                        id = episode.tvShow?.id ?: "",
                        title = episode.tvShow?.title ?: "",
                        poster = episode.tvShow?.poster,
                        banner = episode.tvShow?.banner,
                        releaseDate = episode.tvShow?.released?.format("yyyy-MM-dd"),
                        imdbId = episode.tvShow?.imdbId,
                    ),
                    season = Video.Type.Episode.Season(
                        number = season?.number ?: 0,
                        title = season?.title,
                    ),
                ),
            ),
        )
    }

    fun requestDownload(episode: Episode) {
        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), R.string.download_starting, Toast.LENGTH_SHORT).show()
            DownloadEnqueueHelper.enqueueEpisode(requireContext(), episode)
        }
    }

    fun requestDownloadSeason() {
        if (allEpisodes.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.download_season_all)
            .setMessage(
                getString(
                    R.string.download_season_all_confirm,
                    allEpisodes.size,
                    args.seasonTitle,
                ),
            )
            .setPositiveButton(R.string.download_season_all) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    Toast.makeText(requireContext(), R.string.download_starting, Toast.LENGTH_SHORT).show()
                    val started = DownloadEnqueueHelper.enqueueEpisodes(requireContext(), allEpisodes)
                    Toast.makeText(
                        requireContext(),
                        if (started > 0) {
                            getString(R.string.download_season_started, started)
                        } else {
                            getString(R.string.download_season_none)
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
