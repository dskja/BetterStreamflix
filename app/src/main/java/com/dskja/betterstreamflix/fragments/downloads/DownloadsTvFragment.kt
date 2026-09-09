package com.dskja.betterstreamflix.fragments.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.databinding.FragmentDownloadsTvBinding
import com.dskja.betterstreamflix.download.DownloadController
import com.dskja.betterstreamflix.download.DownloadItemState
import com.dskja.betterstreamflix.download.OfflinePlayback
import com.dskja.betterstreamflix.download.ui.DownloadOptionsController
import com.dskja.betterstreamflix.download.ui.DownloadRowUiModel
import com.dskja.betterstreamflix.download.ui.DownloadsAdapter
import com.dskja.betterstreamflix.download.ui.DownloadsFilter
import com.dskja.betterstreamflix.download.ui.DownloadsViewModel
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.Season
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.utils.viewModelsFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadsTvFragment : Fragment() {
    private var _binding: FragmentDownloadsTvBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModelsFactory {
        DownloadsViewModel(requireContext().applicationContext)
    }

    private val adapter = DownloadsAdapter(
        onPlay = { playOffline(it) },
        onPauseResume = { row ->
            if (row.state == DownloadItemState.PAUSED) viewModel.resume(row.id)
            else viewModel.pause(row.id)
        },
        onRetry = { retry(it) },
        onDelete = { viewModel.remove(it.id) },
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDownloadsTvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvDownloads.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDownloads.adapter = adapter
        binding.btnDownloadsMenu.setOnClickListener { showMenu(it) }
        binding.chipFilterAll.setOnClickListener { viewModel.setFilter(DownloadsFilter.ALL) }
        binding.chipFilterDownloading.setOnClickListener { viewModel.setFilter(DownloadsFilter.DOWNLOADING) }
        binding.chipFilterCompleted.setOnClickListener { viewModel.setFilter(DownloadsFilter.COMPLETED) }
        binding.chipFilterFailed.setOnClickListener { viewModel.setFilter(DownloadsFilter.FAILED) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.rows.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { rows ->
                adapter.submitList(rows)
                binding.tvDownloadsEmpty.isVisible = rows.isEmpty()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.storageLabel.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect {
                binding.tvDownloadsStorage.text = it
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.wifiPaused.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { paused ->
                binding.tvDownloadsBanner.isVisible = paused || viewModel.lowSpace.value
                binding.tvDownloadsBanner.setText(
                    if (paused) R.string.downloads_wifi_paused else R.string.downloads_low_space,
                )
            }
        }
        viewModel.refreshStorage()
    }

    private fun showMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, 1, 0, R.string.downloads_action_pause_all)
            menu.add(0, 2, 1, R.string.downloads_action_resume_all)
            menu.add(0, 3, 2, R.string.downloads_action_clear_completed)
            menu.add(0, 4, 3, R.string.downloads_action_clear_failed)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> viewModel.pauseAll()
                    2 -> viewModel.resumeAll()
                    3 -> viewModel.clearCompleted()
                    4 -> viewModel.clearFailed()
                }
                true
            }
            show()
        }
    }

    private fun playOffline(row: DownloadRowUiModel.Item) {
        viewLifecycleOwner.lifecycleScope.launch {
            val videoType = DownloadController.deserializeVideoType(row.entity.videoTypeJson) ?: return@launch
            val local = withContext(Dispatchers.IO) {
                OfflinePlayback.buildLocalVideo(requireContext(), row.entity)
            } ?: run {
                Toast.makeText(requireContext(), R.string.download_error_file_missing, Toast.LENGTH_SHORT).show()
                return@launch
            }
            OfflineVideoCache.put(row.entity.contentKey, local)
            findNavController().navigate(
                R.id.action_global_player,
                bundleOf(
                    "id" to videoType.let {
                        when (it) {
                            is Video.Type.Movie -> it.id
                            is Video.Type.Episode -> it.id
                        }
                    },
                    "title" to row.entity.title,
                    "subtitle" to row.entity.subtitle,
                    "videoType" to videoType,
                    "preferredServerName" to DownloadsMobileFragment.OFFLINE_SERVER,
                ),
            )
        }
    }

    private fun retry(row: DownloadRowUiModel.Item) {
        viewLifecycleOwner.lifecycleScope.launch {
            val videoType = DownloadController.deserializeVideoType(row.entity.videoTypeJson) ?: return@launch
            viewModel.remove(row.id)
            when (videoType) {
                is Video.Type.Movie -> DownloadOptionsController.enqueueMovie(
                    this@DownloadsTvFragment,
                    Movie(id = videoType.id, title = videoType.title, poster = videoType.poster),
                )
                is Video.Type.Episode -> DownloadOptionsController.enqueueEpisode(
                    this@DownloadsTvFragment,
                    Episode(
                        id = videoType.id,
                        number = videoType.number,
                        title = videoType.title,
                        poster = videoType.poster,
                        overview = videoType.overview,
                        tvShow = TvShow(
                            id = videoType.tvShow.id,
                            title = videoType.tvShow.title,
                            poster = videoType.tvShow.poster,
                        ),
                        season = Season(
                            id = "",
                            number = videoType.season.number,
                            title = videoType.season.title,
                        ),
                    ),
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
