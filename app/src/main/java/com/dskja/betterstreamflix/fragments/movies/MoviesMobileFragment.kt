package com.dskja.betterstreamflix.fragments.movies

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.database.AppDatabase
import com.dskja.betterstreamflix.databinding.FragmentMoviesMobileBinding
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.ui.SpacingItemDecoration
import com.dskja.betterstreamflix.utils.UserPreferences
import com.dskja.betterstreamflix.utils.dp
import com.dskja.betterstreamflix.utils.viewModelsFactory
import com.dskja.betterstreamflix.utils.CacheUtils
import com.dskja.betterstreamflix.utils.ExpNavAutoHide
import com.dskja.betterstreamflix.utils.ExpMotion
import com.dskja.betterstreamflix.utils.ExperimentalMobileDesign
import kotlinx.coroutines.launch

class MoviesMobileFragment : Fragment() {

    private var hasAutoCleared409: Boolean = false

    private var _binding: FragmentMoviesMobileBinding? = null
    private val binding get() = _binding!!

    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { MoviesViewModel(database) }

    private val appAdapter = AppAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val layoutRes = ExperimentalMobileDesign.layout(
            R.layout.fragment_movies_mobile,
            R.layout.fragment_movies_mobile_exp,
        )
        val root = inflater.inflate(layoutRes, container, false)
        _binding = FragmentMoviesMobileBinding.bind(root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ExpNavAutoHide.attach(binding.root)
        ExpMotion.enterScreen(binding.root)
        ExpMotion.staggerFirstFill(binding.rvMovies)
        if (ExperimentalMobileDesign.enabled()) {
            ExpMotion.startAnimation(binding.rvMovies, R.anim.exp_fade_slide_in)
        }

        initializeMovies()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    MoviesViewModel.State.Loading -> binding.isLoading.apply {
                        ExpMotion.fadeInAndShow(root)
                        pbIsLoading.visibility = View.VISIBLE
                        gIsLoadingRetry.visibility = View.GONE
                    }
                    MoviesViewModel.State.LoadingMore -> appAdapter.isLoading = true
                    is MoviesViewModel.State.SuccessLoading -> {
                        displayMovies(state.movies, state.hasMore)
                        appAdapter.isLoading = false
                        ExpMotion.fadeOutAndHide(binding.isLoading.root)
                    }
                    is MoviesViewModel.State.FailedLoading -> {
                        val code = (state.error as? retrofit2.HttpException)?.code()
                        if (code == 409 && !hasAutoCleared409) {
                            hasAutoCleared409 = true
                            CacheUtils.clearAppCache(requireContext())
                            android.widget.Toast.makeText(requireContext(), getString(com.dskja.betterstreamflix.R.string.clear_cache_done_409), android.widget.Toast.LENGTH_SHORT).show()
                            viewModel.getMovies()
                            return@collect
                        }
                        Toast.makeText(
                            requireContext(),
                            state.error.message ?: "",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (appAdapter.isLoading) {
                            appAdapter.isLoading = false
                        } else {
                            binding.isLoading.apply {
                                pbIsLoading.visibility = View.GONE
                                gIsLoadingRetry.visibility = View.VISIBLE
                                val doRetry = { viewModel.getMovies() }
                                btnIsLoadingRetry.setOnClickListener { doRetry() }
                                btnIsLoadingClearCache.setOnClickListener {
                                    CacheUtils.clearAppCache(requireContext())
                                    android.widget.Toast.makeText(requireContext(), getString(com.dskja.betterstreamflix.R.string.clear_cache_done), android.widget.Toast.LENGTH_SHORT).show()
                                    doRetry()
                                }
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


    private fun initializeMovies() {
        binding.rvMovies.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            addItemDecoration(
                SpacingItemDecoration(10.dp(requireContext()))
            )
        }
    }

    private fun displayMovies(movies: List<Movie>, hasMore: Boolean) {
        appAdapter.submitList(movies.onEach {
            it.itemType = AppAdapter.Type.MOVIE_GRID_MOBILE_ITEM
        })

        if (hasMore) {
            appAdapter.setOnLoadMoreListener { viewModel.loadMoreMovies() }
        } else {
            appAdapter.setOnLoadMoreListener(null)
        }
    }
}