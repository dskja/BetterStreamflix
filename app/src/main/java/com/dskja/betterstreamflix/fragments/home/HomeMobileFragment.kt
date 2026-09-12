package com.dskja.betterstreamflix.fragments.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.database.AppDatabase
import com.dskja.betterstreamflix.databinding.FragmentHomeMobileBinding
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.ui.SpacingItemDecoration
import com.dskja.betterstreamflix.utils.UserPreferences
import com.dskja.betterstreamflix.utils.dp
import com.dskja.betterstreamflix.utils.CacheUtils
import com.dskja.betterstreamflix.utils.ExpAmbientGlow
import com.dskja.betterstreamflix.utils.ExpMotion
import com.dskja.betterstreamflix.utils.ExpNavAutoHide
import com.dskja.betterstreamflix.utils.ExperimentalMobileDesign
import com.dskja.betterstreamflix.utils.LoggingUtils
import com.dskja.betterstreamflix.utils.ProviderChangeNotifier
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import android.view.animation.AnimationUtils

class HomeMobileFragment : Fragment() {

    private var hasAutoCleared409: Boolean = false

    private var _binding: FragmentHomeMobileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by lazy {
        val providerKey = UserPreferences.currentProvider?.name ?: "default"
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(AppDatabase.getInstance(requireContext())) as T
            }
        }
        ViewModelProvider(this, factory).get(providerKey, HomeViewModel::class.java)
    }

    private val appAdapter = AppAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val layoutRes = ExperimentalMobileDesign.layout(
            R.layout.fragment_home_mobile,
            R.layout.fragment_home_mobile_exp,
        )
        val root = inflater.inflate(layoutRes, container, false)
        _binding = FragmentHomeMobileBinding.bind(root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeHome()

        // Lightweight refresh when provider changes
        viewLifecycleOwner.lifecycleScope.launch {
            com.dskja.betterstreamflix.utils.ProviderChangeNotifier.providerChangeFlow.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { viewModel.getHome() }
        }

        // Initial load
        viewModel.getHome()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    HomeViewModel.State.Loading -> binding.isLoading.apply {
                        ExpMotion.fadeInAndShow(root)
                        pbIsLoading.visibility = View.VISIBLE
                        gIsLoadingRetry.visibility = View.GONE
                    }
                    is HomeViewModel.State.SuccessLoading -> {
                        displayHome(state.categories)
                        ExpMotion.fadeOutAndHide(binding.isLoading.root)
                    }
                    is HomeViewModel.State.FailedLoading -> {
                        val code = (state.error as? retrofit2.HttpException)?.code()
                        if (code == 409 && !hasAutoCleared409) {
                            hasAutoCleared409 = true
                            CacheUtils.clearAppCache(requireContext())
                            android.widget.Toast.makeText(requireContext(), getString(com.dskja.betterstreamflix.R.string.clear_cache_done_409), android.widget.Toast.LENGTH_SHORT).show()
                            viewModel.getHome()
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
                            val doRetry = { viewModel.getHome() }
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
        appAdapter.onSaveInstanceState(binding.rvHome)
        _binding = null
    }


    private fun initializeHome() {
        binding.rvHome.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            addItemDecoration(
                SpacingItemDecoration(20.dp(requireContext()))
            )
        }

        binding.ivProviderLogo.apply {
            Glide.with(context)
                .load(UserPreferences.currentProvider?.logo?.takeIf { it.isNotEmpty() }
                    ?: R.drawable.ic_provider_default_logo)
                .error(R.drawable.ic_provider_default_logo)
                .fitCenter()
                .into(this)

            setOnClickListener {
                findNavController().navigate(R.id.providers)
            }
        }
        
        // Default shell hides the background; experimental keeps a full-bleed hero plane.
        binding.ivHomeBackground.visibility =
            if (ExperimentalMobileDesign.enabled()) View.VISIBLE else View.GONE

        if (ExperimentalMobileDesign.enabled()) {
            applyExperimentalParallax()
            ExpNavAutoHide.attach(binding.root)
        }
    }

    private var heroScrollOffset = 0

    private fun applyExperimentalParallax() {
        binding.rvHome.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                heroScrollOffset += dy
                // Hero drifts up slower than content, glow/veil follow it.
                val parallax = (heroScrollOffset * 0.38f).coerceIn(0f, 900f)
                binding.ivHomeBackground.translationY = -parallax
                binding.root.findViewById<View>(R.id.v_home_atmosphere)
                    ?.translationY = -parallax
                val brandDrift = -parallax * 0.5f
                val brandAlpha = (1f - parallax / 340f).coerceIn(0f, 1f)
                binding.root.findViewById<View>(R.id.tv_home_brand)?.apply {
                    translationY = brandDrift
                    alpha = brandAlpha
                }
                binding.root.findViewById<View>(R.id.tv_home_tagline)?.apply {
                    translationY = brandDrift
                    alpha = brandAlpha
                }
                binding.root.findViewById<View>(R.id.v_home_brand_rule)?.apply {
                    translationY = brandDrift
                    alpha = brandAlpha
                }
            }
        })
    }

    private fun displayHome(categories: List<Category>) {
        if (ExperimentalMobileDesign.enabled()) {
            updateExperimentalHero(categories)
        }

        categories
            .find { it.name == Category.FEATURED }
            ?.also {
                it.list.forEach { show ->
                    when (show) {
                        is Movie -> show.itemType = AppAdapter.Type.MOVIE_SWIPER_MOBILE_ITEM
                        is TvShow -> show.itemType = AppAdapter.Type.TV_SHOW_SWIPER_MOBILE_ITEM
                    }
                }
            }

        categories
            .find { it.name == Category.CONTINUE_WATCHING }
            ?.also {
                it.name = getString(R.string.home_continue_watching)
                it.list.forEach { show ->
                    when (show) {
                        is Episode -> show.itemType = AppAdapter.Type.EPISODE_CONTINUE_WATCHING_MOBILE_ITEM
                        is Movie -> show.itemType = AppAdapter.Type.MOVIE_CONTINUE_WATCHING_MOBILE_ITEM
                    }
                }
            }

        categories
            .find { it.name == Category.RECENTLY_WATCHED }
            ?.also {
                it.name = getString(R.string.home_recently_watched)
            }

        categories
            .find { it.name == Category.FAVORITE_MOVIES }
            ?.also { it.name = getString(R.string.home_favorite_movies) }

        categories
            .find { it.name == Category.FAVORITE_TV_SHOWS }
            ?.also { it.name = getString(R.string.home_favorite_tv_shows) }

        appAdapter.submitList(
            categories
                .filter { it.list.isNotEmpty() }
                .onEach { category ->
                    if (category.name != Category.FEATURED && category.name != getString(R.string.home_continue_watching)) {
                        category.list.onEach { show ->
                            when (show) {
                                is Episode -> show.itemType = AppAdapter.Type.EPISODE_MOBILE_ITEM
                                is Movie -> show.itemType = AppAdapter.Type.MOVIE_MOBILE_ITEM
                                is TvShow -> show.itemType = AppAdapter.Type.TV_SHOW_MOBILE_ITEM
                            }
                        }
                    }
                    category.itemSpacing = 10.dp(requireContext())
                    category.itemType = when (category.name) {
                        Category.FEATURED -> AppAdapter.Type.CATEGORY_MOBILE_SWIPER
                        else -> AppAdapter.Type.CATEGORY_MOBILE_ITEM
                    }
                }
        )

        if (ExperimentalMobileDesign.enabled()) {
            binding.rvHome.startAnimation(
                AnimationUtils.loadAnimation(requireContext(), R.anim.exp_fade_slide_up)
            )
            binding.root.findViewById<View>(R.id.tv_home_brand)?.startAnimation(
                AnimationUtils.loadAnimation(requireContext(), R.anim.exp_brand_reveal)
            )
        }
    }

    private var currentHeroArt: String? = null

    private fun updateExperimentalHero(categories: List<Category>) {
        val featured = categories.find { it.name == Category.FEATURED }?.list?.firstOrNull()
        updateExperimentalHeroArt(featured)
    }

    /** Keeps the hero backdrop + ambient glow in sync with the featured swiper. */
    fun updateExperimentalHeroArt(show: com.dskja.betterstreamflix.models.Show?) {
        if (_binding == null || !ExperimentalMobileDesign.enabled()) return
        val art = when (show) {
            is Movie -> show.banner ?: show.poster
            is TvShow -> show.banner ?: show.poster
            else -> null
        }
        if (art == currentHeroArt) return
        currentHeroArt = art
        if (!art.isNullOrBlank()) {
            Glide.with(binding.ivHomeBackground)
                .load(art)
                .transition(DrawableTransitionOptions.withCrossFade(450))
                .centerCrop()
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                        isFirstResource: Boolean,
                    ) = false

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        model: Any,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                        dataSource: com.bumptech.glide.load.DataSource,
                        isFirstResource: Boolean,
                    ): Boolean {
                        val bitmap = (resource as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        binding.root.findViewById<View>(R.id.v_home_glow)?.let { glow ->
                            ExpAmbientGlow.apply(bitmap, glow)
                        }
                        return false
                    }
                })
                .into(binding.ivHomeBackground)
            binding.ivHomeBackground.startAnimation(
                AnimationUtils.loadAnimation(requireContext(), R.anim.exp_hero_kenburns)
            )
        } else {
            binding.ivHomeBackground.setImageResource(R.drawable.bg_exp_lumina_sky)
        }
    }
}
