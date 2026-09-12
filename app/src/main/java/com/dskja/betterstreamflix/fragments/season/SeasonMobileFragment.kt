package com.dskja.betterstreamflix.fragments.season

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.database.AppDatabase
import com.dskja.betterstreamflix.databinding.FragmentSeasonMobileBinding
import com.dskja.betterstreamflix.download.ui.DownloadOptionsController
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.ui.SpacingItemDecoration
import com.dskja.betterstreamflix.utils.CacheUtils
import com.dskja.betterstreamflix.utils.ExpNavAutoHide
import com.dskja.betterstreamflix.utils.ExpMotion
import com.dskja.betterstreamflix.utils.ExperimentalMobileDesign
import com.dskja.betterstreamflix.utils.LoggingUtils
import com.dskja.betterstreamflix.utils.dp
import com.dskja.betterstreamflix.utils.viewModelsFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SeasonMobileFragment : Fragment() {

    private var hasAutoCleared409: Boolean = false

    private var _binding: FragmentSeasonMobileBinding? = null
    private val binding get() = _binding!!

    private val args by navArgs<SeasonMobileFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory {
        SeasonViewModel(
            args.seasonId,
            args.tvShowId,
            database,
            args.seasonNumber,
        )
    }

    private val appAdapter = AppAdapter()
    private var loadedEpisodes: List<Episode> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSeasonMobileBinding.bind(
            inflater.inflate(
                ExperimentalMobileDesign.layout(
                    R.layout.fragment_season_mobile,
                    R.layout.fragment_season_mobile_exp,
                ),
                container,
                false,
            )
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ExpNavAutoHide.attach(binding.root)
        ExpMotion.enterScreen(binding.root)
        binding.root.findViewById<View>(com.dskja.betterstreamflix.R.id.iv_detail_back)
            ?.also { back ->
                androidx.appcompat.widget.TooltipCompat.setTooltipText(
                    back, back.context.getString(com.dskja.betterstreamflix.R.string.exp_back))
            }
            ?.setOnClickListener {
                androidx.navigation.Navigation.findNavController(binding.root).navigateUp()
            }
        ExpMotion.staggerFirstFill(binding.rvEpisodes)

        initializeSeason()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    SeasonViewModel.State.LoadingEpisodes -> binding.isLoading.apply {
                        ExpMotion.fadeInAndShow(root)
                        pbIsLoading.visibility = View.VISIBLE
                        gIsLoadingRetry.visibility = View.GONE
                    }
                    is SeasonViewModel.State.SuccessLoadingEpisodes -> {
                        displaySeason(state.episodes)
                        ExpMotion.fadeOutAndHide(binding.isLoading.root)
                    }
                    is SeasonViewModel.State.FailedLoadingEpisodes -> {
                        val code = (state.error as? retrofit2.HttpException)?.code()
                        if (code == 409 && !hasAutoCleared409) {
                            hasAutoCleared409 = true
                            CacheUtils.clearAppCache(requireContext())
                            android.widget.Toast.makeText(requireContext(), getString(com.dskja.betterstreamflix.R.string.clear_cache_done_409), android.widget.Toast.LENGTH_SHORT).show()
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
                                val doRetry = { viewModel.getSeasonEpisodes(args.seasonId) }
                                btnIsLoadingRetry.setOnClickListener { doRetry() }
                                btnIsLoadingClearCache.setOnClickListener {
                                    CacheUtils.clearAppCache(requireContext())
                                    android.widget.Toast.makeText(requireContext(), getString(com.dskja.betterstreamflix.R.string.clear_cache_done), android.widget.Toast.LENGTH_SHORT).show()
                                    doRetry()
                                }
                                btnIsLoadingErrorDetails.setOnClickListener {
                                    LoggingUtils.showErrorDialog(requireContext(), state.error)
                                }
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

        SeasonSwitcher.bind(
            fragment = this,
            spinner = binding.spSeasonPicker,
            database = database,
            tvShowId = args.tvShowId,
            tvShowTitle = args.tvShowTitle,
            tvShowPoster = args.tvShowPoster,
            tvShowBanner = args.tvShowBanner,
            currentSeasonId = args.seasonId,
            currentSeasonNumber = args.seasonNumber,
            currentSeasonTitle = args.seasonTitle,
        )

        binding.btnSeasonDownload.setOnClickListener {
            val episodes = loadedEpisodes
            if (episodes.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.season_download_confirm, episodes.size))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val tvShow = withContext(Dispatchers.IO) {
                            database.tvShowDao().getById(args.tvShowId)
                        } ?: TvShow(
                            id = args.tvShowId,
                            title = viewModel.tvShowTitle.ifBlank {
                                episodes.firstOrNull()?.tvShow?.title.orEmpty()
                            },
                        )
                        DownloadOptionsController.enqueueSeason(
                            this@SeasonMobileFragment,
                            tvShow,
                            viewModel.seasonNumber,
                            episodes,
                        )
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.rvEpisodes.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            addItemDecoration(
                SpacingItemDecoration(20.dp(requireContext()))
            )
        }
    }

    private fun displaySeason(episodes: List<Episode>) {
        loadedEpisodes = episodes
        appAdapter.submitList(episodes.onEach { episode ->
            episode.itemType = AppAdapter.Type.EPISODE_MOBILE_ITEM
        })

        val episodeIndex = episodes
            .sortedByDescending { it.watchHistory?.lastEngagementTimeUtcMillis }
            .firstOrNull { it.watchHistory != null }
            ?.let { episodes.indexOf(it) }
            ?: episodes.indexOfLast { it.isWatched }
                .takeIf { it != -1 && it + 1 < episodes.size }
                ?.let { it + 1 }

        if (episodeIndex != null) {
            val layoutManager = binding.rvEpisodes.layoutManager as? LinearLayoutManager
            layoutManager?.scrollToPositionWithOffset(
                episodeIndex,
                binding.rvEpisodes.height / 2 - 100.dp(requireContext())
            )
        }
    }
}
