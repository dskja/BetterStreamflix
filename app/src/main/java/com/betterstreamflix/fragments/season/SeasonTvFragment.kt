package com.betterstreamflix.fragments.season

import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.betterstreamflix.R
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.databinding.FragmentSeasonTvBinding
import com.betterstreamflix.download.DownloadEnqueueHelper
import com.betterstreamflix.models.Episode
import com.betterstreamflix.utils.CacheUtils
import com.betterstreamflix.utils.LoggingUtils
import com.betterstreamflix.utils.dp
import com.betterstreamflix.utils.viewModelsFactory
import kotlinx.coroutines.launch

class SeasonTvFragment : Fragment() {

    private var hasAutoCleared409: Boolean = false

    private var _binding: FragmentSeasonTvBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null. View has been destroyed.")

    private val args by navArgs<SeasonTvFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory {
        SeasonViewModel(
            args.seasonId,
            args.tvShowId,
            database,
        )
    }

    private val appAdapter = AppAdapter()
    private var allEpisodes: List<Episode> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSeasonTvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeSeason()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    SeasonViewModel.State.LoadingEpisodes -> binding.isLoading.apply {
                        root.visibility = View.VISIBLE
                        pbIsLoading.visibility = View.VISIBLE
                        gIsLoadingRetry.visibility = View.GONE
                    }

                    is SeasonViewModel.State.SuccessLoadingEpisodes -> {
                        allEpisodes = state.episodes
                        displaySeason(state.episodes)
                        binding.isLoading.root.visibility = View.GONE
                    }

                    is SeasonViewModel.State.FailedLoadingEpisodes -> {
                        // Auto clear cache on HTTP 409 and retry
                        val code = (state.error as? retrofit2.HttpException)?.code()
                        if (code == 409 && !hasAutoCleared409) {
                            hasAutoCleared409 = true
                            CacheUtils.clearAppCache(requireContext())
                            android.widget.Toast.makeText(requireContext(), getString(com.betterstreamflix.R.string.clear_cache_done_409), android.widget.Toast.LENGTH_SHORT).show()
                            viewModel.getSeasonEpisodes(args.seasonId)
                            return@collect
                        }
                        Toast.makeText(
                            requireContext(),
                            state.error.message ?: "",
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.isLoading.apply {
                            pbIsLoading.visibility = View.GONE
                            gIsLoadingRetry.visibility = View.VISIBLE
                            btnIsLoadingRetry.setOnClickListener { viewModel.getSeasonEpisodes(args.seasonId) }
                            btnIsLoadingClearCache.setOnClickListener {
                                CacheUtils.clearAppCache(requireContext())
                                android.widget.Toast.makeText(requireContext(), getString(com.betterstreamflix.R.string.clear_cache_done), android.widget.Toast.LENGTH_SHORT).show()
                                viewModel.getSeasonEpisodes(args.seasonId)
                            }
                            btnIsLoadingErrorDetails.setOnClickListener {
                                LoggingUtils.showErrorDialog(requireContext(), state.error)
                            }
                            btnIsLoadingRetry.requestFocus()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    private fun initializeSeason() {
        binding.tvSeasonTitle.text = args.seasonTitle

        binding.btnSeasonDownloadAll.setOnClickListener {
            requestDownloadSeason()
        }

        binding.hgvEpisodes.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            setItemSpacing(resources.getDimension(R.dimen.season_episodes_spacing).toInt())
        }
    }

    private var focusedEpisodeIndex: Int? = null

    private fun displaySeason(episodes: List<Episode>) {
        val preparedEpisodes = episodes.onEach { episode ->
            episode.itemType = AppAdapter.Type.EPISODE_TV_ITEM
        }

        val lastWatchedIndex = episodes
            .filter { it.watchHistory != null }
            .sortedByDescending { it.watchHistory?.lastEngagementTimeUtcMillis }
            .firstOrNull()
            ?.let { episodes.indexOf(it) }
            ?: episodes.indexOfLast { it.isWatched }

        appAdapter.submitList(preparedEpisodes)

        if (focusedEpisodeIndex == null) {
            val scrollIndex = when {
                lastWatchedIndex == -1 -> 0
                lastWatchedIndex < episodes.lastIndex -> lastWatchedIndex + 1
                else -> lastWatchedIndex
            }
            binding.hgvEpisodes.scrollAndFocus(scrollIndex)
            focusedEpisodeIndex = scrollIndex
        }
    }

    private fun RecyclerView.scrollAndFocus(position: Int) {
        scrollToPosition(position)
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
            }
        })
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
