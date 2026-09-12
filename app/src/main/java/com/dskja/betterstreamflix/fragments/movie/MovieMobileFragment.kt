package com.dskja.betterstreamflix.fragments.movie

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.database.AppDatabase
import com.dskja.betterstreamflix.databinding.FragmentMovieMobileBinding
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.ui.SpacingItemDecoration
import com.dskja.betterstreamflix.utils.CacheUtils
import com.dskja.betterstreamflix.utils.ExpNavAutoHide
import com.dskja.betterstreamflix.utils.ExpMotion
import com.dskja.betterstreamflix.utils.ExperimentalMobileDesign
import com.dskja.betterstreamflix.utils.LoggingUtils
import com.dskja.betterstreamflix.utils.dp
import com.dskja.betterstreamflix.utils.loadMovieBanner
import com.dskja.betterstreamflix.utils.viewModelsFactory
import kotlinx.coroutines.launch

class MovieMobileFragment : Fragment() {

    private var _binding: FragmentMovieMobileBinding? = null
    private val binding get() = _binding!!

    private val args by navArgs<MovieMobileFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { MovieViewModel(args.id, database) }

    private val appAdapter = AppAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMovieMobileBinding.bind(
            inflater.inflate(
                ExperimentalMobileDesign.layout(
                    R.layout.fragment_movie_mobile,
                    R.layout.fragment_movie_mobile_exp,
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

        initializeMovie()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    MovieViewModel.State.Loading -> binding.isLoading.apply {
                        ExpMotion.fadeInAndShow(root)
                        pbIsLoading.visibility = View.VISIBLE
                        gIsLoadingRetry.visibility = View.GONE
                    }
                    is MovieViewModel.State.SuccessLoading -> {
                        displayMovie(state.movie)
                        ExpMotion.fadeOutAndHide(binding.isLoading.root)
                    }
                    is MovieViewModel.State.FailedLoading -> {
                        Toast.makeText(
                            requireContext(),
                            state.error.message ?: "",
                            Toast.LENGTH_SHORT
                        ).show()
                            binding.isLoading.apply {
                            pbIsLoading.visibility = View.GONE
                            gIsLoadingRetry.visibility = View.VISIBLE
                                val doRetry = { viewModel.getMovie(args.id) }
                                btnIsLoadingRetry.setOnClickListener { doRetry() }
                                btnIsLoadingClearCache.setOnClickListener {
                                    CacheUtils.clearAppCache(requireContext())
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
        appAdapter.onSaveInstanceState(binding.rvMovie)
        _binding = null
    }


    private fun initializeMovie() {
        binding.rvMovie.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            addItemDecoration(
                SpacingItemDecoration(20.dp(requireContext()))
            )
        }
    }

    private fun displayMovie(movie: Movie) {
        binding.ivMovieBanner.loadMovieBanner(movie) {
            transition(DrawableTransitionOptions.withCrossFade())
        }

        appAdapter.submitList(listOfNotNull(
            movie.apply { itemType = AppAdapter.Type.MOVIE_MOBILE },

            movie.takeIf { it.directors.isNotEmpty() }
                ?.copy()
                ?.apply { itemType = AppAdapter.Type.MOVIE_DIRECTORS_MOBILE },

            movie.takeIf { it.cast.isNotEmpty() }
                ?.copy()
                ?.apply { itemType = AppAdapter.Type.MOVIE_CAST_MOBILE },

            movie.takeIf { it.recommendations.isNotEmpty() }
                ?.copy()
                ?.apply { itemType = AppAdapter.Type.MOVIE_RECOMMENDATIONS_MOBILE },
        ))
    }
}
