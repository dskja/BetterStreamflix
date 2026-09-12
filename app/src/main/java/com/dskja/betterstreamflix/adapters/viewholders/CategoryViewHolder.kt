package com.dskja.betterstreamflix.adapters.viewholders

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.os.postDelayed
import androidx.core.view.children
import android.content.res.ColorStateList
import android.transition.AutoTransition
import android.transition.TransitionManager
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.widget.ViewPager2
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.databinding.ContentCategorySwiperMobileBinding
import com.dskja.betterstreamflix.databinding.ContentCategorySwiperTvBinding
import com.dskja.betterstreamflix.databinding.ItemCategoryMobileBinding
import com.dskja.betterstreamflix.databinding.ItemCategoryTvBinding
import com.dskja.betterstreamflix.fragments.home.HomeMobileFragment
import com.dskja.betterstreamflix.fragments.home.HomeTvFragment
import com.dskja.betterstreamflix.fragments.home.HomeTvFragmentDirections
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.Show
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.ui.SpacingItemDecoration
import com.dskja.betterstreamflix.utils.ExperimentalMobileDesign
import com.dskja.betterstreamflix.utils.dp
import com.dskja.betterstreamflix.utils.format
import com.dskja.betterstreamflix.utils.getCurrentFragment
import com.dskja.betterstreamflix.utils.toActivity
import java.util.Locale
import com.dskja.betterstreamflix.utils.UserPreferences
import com.dskja.betterstreamflix.utils.ExpMotion
import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.database.AppDatabase

class CategoryViewHolder(
    private val _binding: ViewBinding
) : RecyclerView.ViewHolder(
    _binding.root
) {

    private val context = itemView.context
    private lateinit var category: Category

    val childRecyclerView: RecyclerView?
        get() = when (_binding) {
            is ItemCategoryMobileBinding -> _binding.rvCategory
            is ItemCategoryTvBinding -> _binding.hgvCategory
            is ContentCategorySwiperMobileBinding -> {
                // Avoid ViewPager2 private-field reflection (NoSuchFieldException: mRecyclerView
                // after R8/ProGuard or AndroidX updates) — caused release crashes on home.
                (_binding.vpCategorySwiper.getChildAt(0) as? RecyclerView)
            }
            else -> null
        }

    fun bind(
        category: Category,
        onMovieClick: ((Movie) -> Unit)? = null,
        onTvShowClick: ((TvShow) -> Unit)? = null,
        onMovieLongClick: ((Movie) -> Unit)? = null,
        onTvShowLongClick: ((TvShow) -> Unit)? = null,
    ) {
        this.category = category

        when (_binding) {
            is ItemCategoryMobileBinding -> displayMobileItem(_binding, onMovieClick, onTvShowClick, onMovieLongClick, onTvShowLongClick)
            is ItemCategoryTvBinding -> displayTvItem(_binding, onMovieClick, onTvShowClick, onMovieLongClick, onTvShowLongClick)
            is ContentCategorySwiperMobileBinding -> displayMobileSwiper(_binding, onMovieClick, onTvShowClick, onMovieLongClick, onTvShowLongClick)
            is ContentCategorySwiperTvBinding -> displayTvSwiper(_binding)
        }
    }

    private fun displayMobileItem(
        binding: ItemCategoryMobileBinding,
        onMovieClick: ((Movie) -> Unit)?,
        onTvShowClick: ((TvShow) -> Unit)?,
        onMovieLongClick: ((Movie) -> Unit)?,
        onTvShowLongClick: ((TvShow) -> Unit)?,
    ) {
        binding.tvCategoryTitle.text = category.name

        com.dskja.betterstreamflix.utils.ExpMotion.staggerFirstFill(binding.rvCategory)

        binding.rvCategory.apply {
            val categoryAdapter = (adapter as? AppAdapter) ?: AppAdapter().also { adapter = it }
            categoryAdapter.apply {
                this.onMovieClickListener = onMovieClick
                this.onTvShowClickListener = onTvShowClick
                this.onMovieLongClickListener = onMovieLongClick
                this.onTvShowLongClickListener = onTvShowLongClick
                submitList(category.list)
            }
            if (itemDecorationCount == 0) {
                addItemDecoration(SpacingItemDecoration(category.itemSpacing))
            }
        }
    }

    private fun displayTvItem(
        binding: ItemCategoryTvBinding,
        onMovieClick: ((Movie) -> Unit)?,
        onTvShowClick: ((TvShow) -> Unit)?,
        onMovieLongClick: ((Movie) -> Unit)?,
        onTvShowLongClick: ((TvShow) -> Unit)?,
    ) {
        binding.tvCategoryTitle.text = category.name
        binding.hgvCategory.apply {
            setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)

            val categoryAdapter = (adapter as? AppAdapter) ?: AppAdapter().also { adapter = it }
            categoryAdapter.apply {
                this.onMovieClickListener = onMovieClick
                this.onTvShowClickListener = onTvShowClick
                this.onMovieLongClickListener = onMovieLongClick
                this.onTvShowLongClickListener = onTvShowLongClick
                submitList(category.list)
            }
            setItemSpacing(category.itemSpacing)

            isFocusable = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
    }


    private fun displayMobileSwiper(
        binding: ContentCategorySwiperMobileBinding,
        onMovieClick: ((Movie) -> Unit)?,
        onTvShowClick: ((TvShow) -> Unit)?,
        onMovieLongClick: ((Movie) -> Unit)?,
        onTvShowLongClick: ((TvShow) -> Unit)?,
    ) {
        binding.tvCategoryTitle.text = category.name
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(8_000) {
            binding.vpCategorySwiper.currentItem += 1
        }

        val items = listOf(
            listOfNotNull(category.list.lastOrNull()),
            category.list,
            listOfNotNull(category.list.firstOrNull()),
        ).flatten()
        binding.vpCategorySwiper.apply {
            adapter = AppAdapter().apply {
                this.onMovieClickListener = onMovieClick
                this.onTvShowClickListener = onTvShowClick
                this.onMovieLongClickListener = onMovieLongClick
                this.onTvShowLongClickListener = onTvShowLongClick
                submitList(category.list)
                post { (adapter as AppAdapter).submitList(items) }
            }
        }

        val exp = ExperimentalMobileDesign.enabled()
        if (exp) {
            applyExperimentalSwiperChrome(binding)
        }

        binding.llDotsIndicator.apply {
            removeAllViews()
            if (exp) {
                val dotSize = 6.dp(context)
                val dotMargin = 5.dp(context)
                repeat(category.list.size) {
                    val view = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                            setMargins(dotMargin, 0, dotMargin, 0)
                        }
                        setBackgroundResource(R.drawable.bg_exp_dot)
                        backgroundTintList = ColorStateList.valueOf(expDotInactive)
                    }
                    addView(view)
                }
            } else {
                repeat(category.list.size) {
                    val view = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(15, 15).apply {
                            setMargins(10, 0, 10, 0)
                        }
                        setBackgroundResource(R.drawable.bg_dot_indicator)
                    }
                    addView(view)
                }
            }
        }

        binding.vpCategorySwiper.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val indicatorPosition = when (position) {
                    0 -> category.list.lastIndex
                    items.lastIndex -> 0
                    else -> position - 1
                }
                if (exp) {
                    updateExpDots(binding, indicatorPosition)
                    (category.list.getOrNull(indicatorPosition) as? Show)?.let { show ->
                        (context.toActivity()?.getCurrentFragment() as? HomeMobileFragment)
                            ?.updateExperimentalHeroArt(show)
                        val title = when (show) {
                            is Movie -> show.title
                            is TvShow -> show.title
                            else -> null
                        }
                        if (title != null) {
                            binding.vpCategorySwiper.announceForAccessibility(
                                context.getString(R.string.exp_swiper_page, title)
                            )
                        }
                    }
                } else {
                    binding.llDotsIndicator.children.forEachIndexed { index, view ->
                        view.isSelected = (indicatorPosition == index)
                    }
                }

                handler.removeCallbacksAndMessages(null)
                handler.postDelayed(8_000) {
                    binding.vpCategorySwiper.currentItem += 1
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    when (binding.vpCategorySwiper.currentItem) {
                        0 -> binding.vpCategorySwiper.setCurrentItem(
                            items.lastIndex - 1,
                            false
                        )
                        items.lastIndex -> binding.vpCategorySwiper.setCurrentItem(
                            1,
                            false
                        )
                    }
                }
            }
        })
    }

    private val expDotInactive: Int
        get() = androidx.core.graphics.ColorUtils.setAlphaComponent(
            MaterialColors.getColor(
                context, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF.toInt(),
            ),
            0x59,
        )

    private fun applyExperimentalSwiperChrome(binding: ContentCategorySwiperMobileBinding) {
        binding.vpCategorySwiper.setPageTransformer { page, position ->
            val clamped = abs(position).coerceAtMost(1f)
            page.findViewById<View>(R.id.iv_swiper_background)?.apply {
                translationX = -position * page.width * 0.25f
                scaleX = 1f + clamped * 0.05f
                scaleY = scaleX
            }
            page.findViewById<View>(R.id.tv_swiper_title)?.apply {
                alpha = (1f - clamped * 1.2f).coerceAtLeast(0f)
                translationY = position * page.width * 0.05f
            }
        }
    }

    private fun updateExpDots(
        binding: ContentCategorySwiperMobileBinding,
        selected: Int,
    ) {
        val activeColor = MaterialColors.getColor(
            context, androidx.appcompat.R.attr.colorPrimary, 0xFFFFFFFF.toInt(),
        )
        val inactive = expDotInactive
        val activeWidth = 20.dp(context)
        val dotSize = 6.dp(context)
        TransitionManager.beginDelayedTransition(
            binding.llDotsIndicator,
            AutoTransition().setDuration(180),
        )
        binding.llDotsIndicator.children.forEachIndexed { index, view ->
            val isActive = index == selected
            view.layoutParams = (view.layoutParams as LinearLayout.LayoutParams).apply {
                width = if (isActive) activeWidth else dotSize
            }
            view.backgroundTintList = ColorStateList.valueOf(if (isActive) activeColor else inactive)
        }
    }

    private fun displayTvSwiper(binding: ContentCategorySwiperTvBinding) {
        binding.tvCategoryTitle.text = category.name
        val selected = category.list.getOrNull(category.selectedIndex) as? Show ?: return

        fun checkProviderAndRun(show: Show, action: () -> Unit) {
            val providerName = when(show){
                is Movie -> show.providerName
                is TvShow -> show.providerName
            }

            if (!providerName.isNullOrBlank() && providerName != UserPreferences.currentProvider?.name) {
                Provider.findByName(providerName)?.let {
                    UserPreferences.currentProvider = it
                }
            }
            action()
        }
        
        // Aggiornamento dello sfondo forzato per TV all'inizio o al cambio indice
        val poster = when (selected) {
            is Movie -> selected.banner
            is TvShow -> selected.banner
            else -> null
        }
        
        when (val fragment = context.toActivity()?.getCurrentFragment()) {
            is HomeTvFragment -> {
                if (poster != null) {
                    fragment.updateBackground(poster, false) // Imposta lo sfondo senza marcare come focalizzato
                }
                
                // Se l'elemento è stato appena selezionato (indice cambiato), assicura che l'aggiornamento sia visibile
                if (category.selectedIndex == category.list.indexOf(selected)) {
                    fragment.resetSwiperSchedule() // Riavvia lo scheduler per assicurarsi che continui
                }
            }
        }

        binding.tvSwiperTitle.text = when (selected) {
            is Movie -> selected.title
            is TvShow -> selected.title
        }

        binding.tvSwiperTvShowLastEpisode.apply {
            text = when (selected) {
                is TvShow -> selected.seasons.lastOrNull()?.let { season ->
                    season.episodes.lastOrNull()?.let { episode ->
                        if (season.number != 0) {
                            context.getString(
                                R.string.tv_show_item_season_number_episode_number,
                                season.number,
                                episode.number
                            )
                        } else {
                            context.getString(
                                R.string.tv_show_item_episode_number,
                                episode.number
                            )
                        }
                    }
                } ?: context.getString(R.string.tv_show_item_type)
                else -> context.getString(R.string.movie_item_type)
            }
        }

        binding.tvSwiperQuality.apply {
            text = when (selected) {
                is Movie -> selected.quality
                is TvShow -> selected.quality
            }
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvSwiperReleased.apply {
            text = when (selected) {
                is Movie -> selected.released?.format("yyyy")
                is TvShow -> selected.released?.format("yyyy")
            }
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvSwiperRating.apply {
            text = when (selected) {
                is Movie -> selected.rating?.let { String.format(Locale.ROOT, "%.1f", it) }
                is TvShow -> selected.rating?.let { String.format(Locale.ROOT, "%.1f", it) }
            }
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.ivSwiperRatingIcon.visibility = binding.tvSwiperRating.visibility

        binding.tvSwiperOverview.text = when (selected) {
            is Movie -> selected.overview
            is TvShow -> selected.overview
        }


        binding.btnSwiperWatchNow.apply {
            setOnClickListener {
                checkProviderAndRun(selected) {
                    findNavController().navigate(
                        when (selected) {
                            is Movie -> HomeTvFragmentDirections.actionHomeToMovie(selected.id)
                            is TvShow -> HomeTvFragmentDirections.actionHomeToTvShow(
                                id = selected.id,
                                poster = selected.poster,
                                banner = selected.banner,
                            )
                        }
                    )
                }
            }
            setOnKeyListener { _, _, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            when (val fragment = context.toActivity()?.getCurrentFragment()) {
                                is HomeTvFragment -> fragment.resetSwiperSchedule()
                            }
                            category.selectedIndex = (category.selectedIndex + 1) % category.list.size
                            when (val fragment = context.toActivity()?.getCurrentFragment()) {
                                is HomeTvFragment -> when (val it = category.list[category.selectedIndex]) {
                                    is Movie -> fragment.updateBackground(it.banner, true)
                                    is TvShow -> fragment.updateBackground(it.banner, true)
                                }
                            }
                            bindingAdapter?.notifyItemChanged(bindingAdapterPosition)
                            return@setOnKeyListener true
                        }
                    }
                }
                false
            }
        }

        binding.pbSwiperProgress.apply {
            val watchHistory = when (selected) {
                is Movie -> selected.watchHistory
                is TvShow -> null
            }

            progress = when {
                watchHistory != null -> (watchHistory.lastPlaybackPositionMillis * 100 / watchHistory.durationMillis.toDouble()).toInt()
                else -> 0
            }
            visibility = when {
                watchHistory != null -> View.VISIBLE
                else -> View.GONE
            }
        }

        binding.llDotsIndicator.apply {
            removeAllViews()
            repeat(category.list.size) { index ->
                val view = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(15, 15).apply {
                        setMargins(10, 0, 10, 0)
                    }
                    setBackgroundResource(R.drawable.bg_dot_indicator)
                    isSelected = (category.selectedIndex == index)
                }
                addView(view)
            }
        }
    }
}
