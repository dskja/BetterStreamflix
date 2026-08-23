package com.betterstreamflix.fragments.season

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.databinding.FragmentSeasonMobileBinding
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Season
import com.betterstreamflix.ui.SpacingItemDecoration
import com.betterstreamflix.utils.CacheUtils
import com.betterstreamflix.utils.LoggingUtils
import com.betterstreamflix.utils.dp
import com.betterstreamflix.utils.viewModelsFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SeasonMobileFragment : Fragment() {

    private var hasAutoCleared409: Boolean = false

    private var _binding: FragmentSeasonMobileBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null. View has been destroyed.")

    private val args by navArgs<SeasonMobileFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory {
        SeasonViewModel(
            args.seasonId,
            args.tvShowId,
            database
        )
    }

    private val appAdapter = AppAdapter()
    private var allEpisodes: List<Episode> = emptyList()
    private var searchWatcher: TextWatcher? = null
    private var allSeasons: List<Season> = emptyList()
    private var currentQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSeasonMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeSeason()
        loadSeasonsForDropdown()

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
                                val doRetry = { viewModel.getSeasonEpisodes(args.seasonId) }
                                btnIsLoadingRetry.setOnClickListener { doRetry() }
                                btnIsLoadingClearCache.setOnClickListener {
                                    CacheUtils.clearAppCache(requireContext())
                                    android.widget.Toast.makeText(requireContext(), getString(com.betterstreamflix.R.string.clear_cache_done), android.widget.Toast.LENGTH_SHORT).show()
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
        searchWatcher?.let { binding.etSeasonSearch.removeTextChangedListener(it) }
        _binding = null
    }

    private fun initializeSeason() {
        binding.tvSeasonTitle.text = args.seasonTitle

        binding.btnSeasonBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnSeasonSearch.setOnClickListener {
            val isVisible = binding.etSeasonSearch.visibility == View.VISIBLE
            if (isVisible) {
                binding.etSeasonSearch.visibility = View.GONE
                binding.etSeasonSearch.text?.clear()
                binding.rvEpisodes.requestLayout()
            } else {
                binding.etSeasonSearch.visibility = View.VISIBLE
                binding.etSeasonSearch.requestFocus()
            }
        }

        binding.btnSeasonDropdown.setOnClickListener {
            showSeasonDropdown()
        }

        searchWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterEpisodes(s?.toString() ?: "")
            }
        }
        binding.etSeasonSearch.addTextChangedListener(searchWatcher)

        binding.rvEpisodes.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            addItemDecoration(
                SpacingItemDecoration(20.dp(requireContext()))
            )
        }
    }

    private fun loadSeasonsForDropdown() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val seasons = database.seasonDao().getByTvShowId(args.tvShowId)
            withContext(Dispatchers.Main) {
                allSeasons = seasons
            }
        }
    }

    private fun showSeasonDropdown() {
        if (allSeasons.isEmpty()) {
            loadSeasonsForDropdown()
            return
        }

        val seasonLabels = allSeasons.map { it.title ?: getString(com.betterstreamflix.R.string.season_number, it.number) }
        val currentIdx = allSeasons.indexOfFirst { it.id == args.seasonId }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(com.betterstreamflix.R.string.select_season))
            .setSingleChoiceItems(seasonLabels.toTypedArray(), currentIdx.coerceAtLeast(0)) { dialog, which ->
                val selected = allSeasons[which]
                if (selected.id != args.seasonId) {
                    findNavController().navigate(
                        com.betterstreamflix.R.id.tv_show,
                        Bundle().apply {
                            putString("id", args.tvShowId)
                            putString("poster", args.tvShowPoster)
                            putString("banner", args.tvShowBanner)
                        }
                    )
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun filterEpisodes(query: String) {
        currentQuery = query
        if (query.isBlank()) {
            displaySeason(allEpisodes)
            return
        }
        val filtered = allEpisodes.filter { episode ->
            episode.title?.contains(query, ignoreCase = true) == true ||
            episode.number.toString().contains(query, ignoreCase = true)
        }
        displaySeason(filtered)
    }

    private fun displaySeason(episodes: List<Episode>) {
        appAdapter.submitList(episodes.onEach { episode ->
            episode.itemType = AppAdapter.Type.EPISODE_MOBILE_ITEM
        })

        if (currentQuery.isBlank()) {
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
}
