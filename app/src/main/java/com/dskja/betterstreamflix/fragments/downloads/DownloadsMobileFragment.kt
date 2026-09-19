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
import com.dskja.betterstreamflix.databinding.FragmentDownloadsMobileBinding
import com.dskja.betterstreamflix.download.DownloadController
import com.dskja.betterstreamflix.download.DownloadItemState
import com.dskja.betterstreamflix.download.OfflinePlayback
import com.dskja.betterstreamflix.download.ui.DownloadOptionsController
import com.dskja.betterstreamflix.download.ui.DownloadRowUiModel
import com.dskja.betterstreamflix.download.ui.DownloadsAdapter
import com.dskja.betterstreamflix.download.ui.DownloadsFilter
import com.dskja.betterstreamflix.download.ui.DownloadsSort
import com.dskja.betterstreamflix.download.ui.DownloadsViewModel
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.utils.ExperimentalMobileDesign
import com.dskja.betterstreamflix.utils.viewModelsFactory
import com.dskja.betterstreamflix.utils.ExpMotion
import com.dskja.betterstreamflix.utils.ExpNavAutoHide
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadsMobileFragment : Fragment() {
    private var _binding: FragmentDownloadsMobileBinding? = null
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
        onItemMore = { row, anchor -> showItemMenu(row, anchor) },
        onPackMore = { pack, anchor -> showPackMenu(pack, anchor) },
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDownloadsMobileBinding.bind(
            inflater.inflate(
                ExperimentalMobileDesign.layout(
                    R.layout.fragment_downloads_mobile,
                    R.layout.fragment_downloads_mobile_exp,
                ),
                container,
                false,
            )
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ExpNavAutoHide.attach(binding.root)
        ExpMotion.enterScreen(binding.root)
        ExpMotion.staggerFirstFill(binding.rvDownloads)
        if (ExperimentalMobileDesign.enabled()) {
            ExpMotion.revealHeader(
                binding.root.findViewById(R.id.tv_downloads_eyebrow),
                binding.tvDownloadsTitle,
                binding.root.findViewById(R.id.tv_downloads_tagline),
            )
        }
        binding.rvDownloads.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDownloads.itemAnimator = null
        binding.rvDownloads.adapter = adapter
        binding.btnDownloadsMenu.setOnClickListener { showMenu(it) }
        binding.chipFilterAll.setOnClickListener { viewModel.setFilter(DownloadsFilter.ALL) }
        binding.chipFilterDownloading.setOnClickListener { viewModel.setFilter(DownloadsFilter.DOWNLOADING) }
        binding.chipFilterCompleted.setOnClickListener { viewModel.setFilter(DownloadsFilter.COMPLETED) }
        binding.chipFilterFailed.setOnClickListener { viewModel.setFilter(DownloadsFilter.FAILED) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.rows.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { rows ->
                adapter.submitList(rows.toList())
                binding.tvDownloadsEmpty.isVisible = rows.isEmpty()
                binding.tvDownloadsEmpty.setText(
                    if (viewModel.currentFilter() == DownloadsFilter.ALL) {
                        R.string.downloads_empty
                    } else {
                        R.string.downloads_filter_empty
                    },
                )
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedFilter.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect {
                styleFilters(it)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.storageLabel.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect {
                binding.tvDownloadsStorage.text = it
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.lowSpace.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { low ->
                updateBanner(low, viewModel.wifiPaused.value)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.wifiPaused.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { paused ->
                updateBanner(viewModel.lowSpace.value, paused)
            }
        }
        viewModel.refreshStorage()
        styleFilters(viewModel.currentFilter())
    }

    override fun onResume() {
        super.onResume()
        viewModel.startLiveProgress()
    }

    override fun onPause() {
        viewModel.stopLiveProgress()
        super.onPause()
    }

    private fun styleFilters(selected: DownloadsFilter) {
        styleChip(binding.chipFilterAll, selected == DownloadsFilter.ALL)
        styleChip(binding.chipFilterDownloading, selected == DownloadsFilter.DOWNLOADING)
        styleChip(binding.chipFilterCompleted, selected == DownloadsFilter.COMPLETED)
        styleChip(binding.chipFilterFailed, selected == DownloadsFilter.FAILED)
    }

    private fun styleChip(chip: android.widget.TextView, selected: Boolean) {
        chip.isSelected = selected
        val exp = ExperimentalMobileDesign.enabled()
        chip.setBackgroundResource(
            when {
                exp && selected -> R.drawable.bg_exp_button_primary
                exp -> R.drawable.bg_exp_chip
                selected -> R.drawable.bg_download_filter_chip_selected
                else -> R.drawable.bg_download_filter_chip
            },
        )
        chip.setTextColor(
            when {
                exp && selected -> MaterialColors.getColor(
                    chip, com.google.android.material.R.attr.colorOnPrimary,
                )
                exp -> MaterialColors.getColor(
                    chip, com.google.android.material.R.attr.colorOnSurfaceVariant,
                )
                selected -> 0xFF111111.toInt()
                else -> 0xFFFFFFFF.toInt()
            },
        )
    }

    private fun updateBanner(lowSpace: Boolean, wifiPaused: Boolean) {
        when {
            wifiPaused -> {
                binding.tvDownloadsBanner.isVisible = true
                binding.tvDownloadsBanner.setText(R.string.downloads_wifi_paused)
            }
            lowSpace -> {
                binding.tvDownloadsBanner.isVisible = true
                binding.tvDownloadsBanner.setText(R.string.downloads_low_space)
            }
            else -> binding.tvDownloadsBanner.isVisible = false
        }
    }

    private fun showMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, 1, 0, R.string.downloads_action_pause_all)
            menu.add(0, 2, 1, R.string.downloads_action_resume_all)
            menu.add(0, 6, 2, R.string.downloads_action_retry_all_failed)
            menu.add(0, 3, 3, R.string.downloads_action_clear_completed)
            menu.add(0, 7, 4, R.string.downloads_action_clear_watched)
            menu.add(0, 4, 5, R.string.downloads_action_clear_failed)
            val sortMenu = menu.addSubMenu(0, 8, 6, R.string.downloads_sort)
            sortMenu.add(0, 10, 0, R.string.downloads_sort_newest)
                .setCheckable(true)
                .setChecked(viewModel.currentSort() == DownloadsSort.NEWEST)
            sortMenu.add(0, 11, 1, R.string.downloads_sort_title)
                .setCheckable(true)
                .setChecked(viewModel.currentSort() == DownloadsSort.TITLE)
            sortMenu.add(0, 12, 2, R.string.downloads_sort_size)
                .setCheckable(true)
                .setChecked(viewModel.currentSort() == DownloadsSort.SIZE)
            sortMenu.setGroupCheckable(0, true, true)
            menu.add(0, 5, 7, R.string.downloads_action_settings)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> viewModel.pauseAll()
                    2 -> viewModel.resumeAll()
                    3 -> viewModel.clearCompleted()
                    4 -> viewModel.clearFailed()
                    5 -> findNavController().navigate(R.id.settings)
                    6 -> viewModel.retryAllFailed()
                    7 -> viewModel.clearWatched()
                    10 -> viewModel.setSort(DownloadsSort.NEWEST)
                    11 -> viewModel.setSort(DownloadsSort.TITLE)
                    12 -> viewModel.setSort(DownloadsSort.SIZE)
                }
                true
            }
            show()
        }
    }

    private fun showItemMenu(row: DownloadRowUiModel.Item, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            if (row.state == DownloadItemState.COMPLETED) {
                menu.add(0, 1, 0, R.string.downloads_action_share)
            }
            if (row.state == DownloadItemState.FAILED) {
                menu.add(0, 2, 1, R.string.downloads_action_retry)
            }
            menu.add(0, 3, 2, R.string.downloads_action_delete)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> share(row)
                    2 -> retry(row)
                    3 -> viewModel.remove(row.id)
                }
                true
            }
            show()
        }
    }

    private fun showPackMenu(pack: DownloadRowUiModel.SeasonPack, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, 1, 0, R.string.downloads_pack_pause)
            menu.add(0, 2, 1, R.string.downloads_pack_resume)
            menu.add(0, 3, 2, R.string.downloads_pack_delete)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> viewModel.pausePack(pack.pack.id)
                    2 -> viewModel.resumePack(pack.pack.id)
                    3 -> viewModel.removePack(pack.pack.id)
                }
                true
            }
            show()
        }
    }

    private fun share(row: DownloadRowUiModel.Item) {
        viewLifecycleOwner.lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                OfflinePlayback.exportShareUri(requireContext(), row.entity)
            }
            if (uri == null) {
                Toast.makeText(requireContext(), R.string.download_error_file_missing, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                startActivity(android.content.Intent.createChooser(intent, row.entity.title))
            }
        }
    }

    private fun playOffline(row: DownloadRowUiModel.Item) {
        viewLifecycleOwner.lifecycleScope.launch {
            val videoType = DownloadController.deserializeVideoType(row.entity.videoTypeJson)
            if (videoType == null) {
                Toast.makeText(requireContext(), R.string.downloads_failed_generic, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val local = withContext(Dispatchers.IO) {
                OfflinePlayback.buildLocalVideo(requireContext(), row.entity)
            }
            if (local == null) {
                Toast.makeText(requireContext(), R.string.download_error_file_missing, Toast.LENGTH_SHORT).show()
                return@launch
            }
            OfflineVideoCache.put(row.entity.contentKey, local)
            findNavController().navigate(
                R.id.action_global_player,
                bundleOf(
                    "id" to when (videoType) {
                        is Video.Type.Movie -> videoType.id
                        is Video.Type.Episode -> videoType.id
                    },
                    "title" to row.entity.title,
                    "subtitle" to row.entity.subtitle,
                    "videoType" to videoType,
                    "preferredServerName" to OFFLINE_SERVER,
                ),
            )
        }
    }

    private fun retry(row: DownloadRowUiModel.Item) {
        viewLifecycleOwner.lifecycleScope.launch {
            val videoType = DownloadController.deserializeVideoType(row.entity.videoTypeJson) ?: return@launch
            viewModel.remove(row.id)
            when (videoType) {
                is Video.Type.Movie -> {
                    val movie = com.dskja.betterstreamflix.models.Movie(
                        id = videoType.id,
                        title = videoType.title,
                        poster = videoType.poster,
                    )
                    DownloadOptionsController.enqueueMovie(this@DownloadsMobileFragment, movie)
                }
                is Video.Type.Episode -> {
                    val episode = com.dskja.betterstreamflix.models.Episode(
                        id = videoType.id,
                        number = videoType.number,
                        title = videoType.title,
                        poster = videoType.poster,
                        overview = videoType.overview,
                        tvShow = com.dskja.betterstreamflix.models.TvShow(
                            id = videoType.tvShow.id,
                            title = videoType.tvShow.title,
                            poster = videoType.tvShow.poster,
                        ),
                        season = com.dskja.betterstreamflix.models.Season(
                            id = "",
                            number = videoType.season.number,
                            title = videoType.season.title,
                        ),
                    )
                    DownloadOptionsController.enqueueEpisode(this@DownloadsMobileFragment, episode)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val OFFLINE_SERVER = "__offline__"
    }
}

object OfflineVideoCache {
    private val map = mutableMapOf<String, Video>()
    fun put(key: String, video: Video) {
        map[key] = video
    }
    fun take(key: String): Video? = map.remove(key)
    fun get(key: String): Video? = map[key]
    fun remove(key: String) {
        map.remove(key)
    }
    fun clear() {
        map.clear()
    }
}
