package com.dskja.betterstreamflix.adapters.viewholders

import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.utils.ExpPressEffects.applyExpPress
import com.dskja.betterstreamflix.utils.ExperimentalMobileDesign
import com.dskja.betterstreamflix.databinding.ItemEpisodeContinueWatchingMobileBinding
import com.dskja.betterstreamflix.databinding.ItemEpisodeContinueWatchingTvBinding
import com.dskja.betterstreamflix.databinding.ItemEpisodeMobileBinding
import com.dskja.betterstreamflix.databinding.ItemEpisodeTvBinding
import com.dskja.betterstreamflix.download.DownloadContentKey
import com.dskja.betterstreamflix.download.OfflineBadgeStore
import com.dskja.betterstreamflix.fragments.home.HomeMobileFragmentDirections
import com.dskja.betterstreamflix.fragments.home.HomeTvFragment
import com.dskja.betterstreamflix.fragments.home.HomeTvFragmentDirections
import com.dskja.betterstreamflix.fragments.season.SeasonMobileFragmentDirections
import com.dskja.betterstreamflix.fragments.season.SeasonTvFragmentDirections
import com.dskja.betterstreamflix.fragments.tv_show.TvShowMobileFragmentDirections
import com.dskja.betterstreamflix.fragments.tv_show.TvShowTvFragmentDirections
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.ui.ShowOptionsMobileDialog
import com.dskja.betterstreamflix.ui.ShowOptionsTvDialog
import com.dskja.betterstreamflix.utils.EpisodeManager
import com.dskja.betterstreamflix.utils.UserPreferences
import com.dskja.betterstreamflix.utils.format
import com.dskja.betterstreamflix.utils.getCurrentFragment
import com.dskja.betterstreamflix.utils.loadTvShowCardArtwork
import com.dskja.betterstreamflix.utils.toActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class EpisodeViewHolder(
    private val _binding: ViewBinding
) : RecyclerView.ViewHolder(
    _binding.root
) {

    private val context = itemView.context
    init {
        if (ExperimentalMobileDesign.enabled() && (_binding is ItemEpisodeMobileBinding || _binding is ItemEpisodeContinueWatchingMobileBinding)) {
            itemView.applyExpPress()
        }
    }

    private lateinit var episode: Episode
    private var downloadRibbonJob: Job? = null

    fun bind(episode: Episode) {
        this.episode = episode

        when (_binding) {
            is ItemEpisodeMobileBinding -> displayMobileItem(_binding)
            is ItemEpisodeTvBinding -> displayTvItem(_binding)
            is ItemEpisodeContinueWatchingMobileBinding -> displayContinueWatchingMobileItem(_binding)
            is ItemEpisodeContinueWatchingTvBinding -> displayContinueWatchingTvItem(_binding)
        }
    }


    private fun displayMobileItem(binding: ItemEpisodeMobileBinding) {
        binding.root.apply {
            setOnClickListener {
                findNavController().navigate(
                    SeasonMobileFragmentDirections.actionSeasonToPlayer(
                        id = episode.id,
                        title = episode.tvShow?.title ?: "",
                        subtitle = episode.season?.takeIf { it.number != 0 }?.let { season ->
                            context.getString(
                                R.string.player_subtitle_tv_show,
                                season.number,
                                episode.number,
                                episode.title ?: context.getString(
                                    R.string.episode_number,
                                    episode.number
                                )
                            )
                        } ?: context.getString(
                            R.string.player_subtitle_tv_show_episode_only,
                            episode.number,
                            episode.title ?: context.getString(
                                R.string.episode_number,
                                episode.number
                            )
                        ),
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
                                number = episode.season?.number ?: 0,
                                title = episode.season?.title,
                            ),
                        ),
                    )
                )
            }
            setOnLongClickListener {
                ShowOptionsMobileDialog(context, episode)
                    .show()
                true
            }
        }

        binding.ivEpisodePoster.apply {
            clipToOutline = true
            Glide.with(context)
                .load(episode.poster)
                .error(R.drawable.glide_fallback_cover)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(this)
        }
        binding.ivEpisodeWatchedRibbon.visibility = if (episode.isWatched) View.VISIBLE else View.GONE
        bindDownloadRibbon(binding.ivEpisodeDownloadRibbon)

        binding.pbEpisodeProgress.apply {
            val watchHistory = episode.watchHistory

            progress = when {
                watchHistory != null -> (watchHistory.lastPlaybackPositionMillis * 100 / watchHistory.durationMillis.toDouble()).toInt()
                episode.isWatched -> 100
                else -> 0
            }
            visibility = when {
                watchHistory != null -> View.VISIBLE
                episode.isWatched -> View.VISIBLE
                else -> View.GONE
            }
        }

        binding.tvEpisodeInfo.text = context.getString(
            R.string.episode_number,
            episode.number
        )

        binding.tvEpisodeTitle.text = episode.title ?: context.getString(
            R.string.episode_number,
            episode.number
        )

        binding.tvEpisodeReleased.apply {
            text = episode.released?.let { " • ${it.format("yyyy-MM-dd")}" }
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }
        binding.tvEpisodeOverview.text = episode.overview ?: ""
    }

    private fun displayTvItem(binding: ItemEpisodeTvBinding) {
        binding.root.apply {
            setOnClickListener {
                findNavController().navigate(
                    SeasonTvFragmentDirections.actionSeasonToPlayer(
                        id = episode.id,
                        title = episode.tvShow?.title ?: "",
                        subtitle = episode.season?.takeIf { it.number != 0 }?.let { season ->
                            context.getString(
                                R.string.player_subtitle_tv_show,
                                season.number,
                                episode.number,
                                episode.title ?: context.getString(
                                    R.string.episode_number,
                                    episode.number
                                )
                            )
                        } ?: context.getString(
                            R.string.player_subtitle_tv_show_episode_only,
                            episode.number,
                            episode.title ?: context.getString(
                                R.string.episode_number,
                                episode.number
                            )
                        ),
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
                                number = episode.season?.number ?: 0,
                                title = episode.season?.title,
                            ),
                        ),
                    )
                )
            }
            setOnLongClickListener {
                ShowOptionsTvDialog(context, episode)
                    .show()
                true
            }
            setOnFocusChangeListener { _, hasFocus ->
                val animation = when {
                    hasFocus -> AnimationUtils.loadAnimation(context, R.anim.zoom_in)
                    else -> AnimationUtils.loadAnimation(context, R.anim.zoom_out)
                }
                binding.root.startAnimation(animation)
                animation.fillAfter = true
            }
        }

        binding.ivEpisodePoster.apply {
            clipToOutline = true
            Glide.with(context)
                .load(episode.poster)
                .error(R.drawable.glide_fallback_cover)
                .fallback(R.drawable.glide_fallback_cover)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(this)
        }
        binding.ivEpisodeWatchedRibbon.visibility = if (episode.isWatched) View.VISIBLE else View.GONE
        bindDownloadRibbon(binding.ivEpisodeDownloadRibbon)

        binding.pbEpisodeProgress.apply {
            val watchHistory = episode.watchHistory

            progress = when {
                watchHistory != null -> (watchHistory.lastPlaybackPositionMillis * 100 / watchHistory.durationMillis.toDouble()).toInt()
                episode.isWatched -> 100
                else -> 0
            }
            visibility = when {
                watchHistory != null -> View.VISIBLE
                episode.isWatched -> View.VISIBLE
                else -> View.GONE
            }
        }

        binding.tvEpisodeInfo.text = context.getString(
            R.string.episode_number,
            episode.number
        )

        binding.tvEpisodeTitle.text = episode.title ?: context.getString(
            R.string.episode_number,
            episode.number
        )

        binding.tvEpisodeReleased.apply {
            text = episode.released?.format("EEEE - MMMM dd, yyyy")
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }
        binding.tvEpisodeOverview.text = episode.overview ?: ""
    }

    private fun checkProviderAndRun(action: () -> Unit) {
        val providerName = episode.tvShow?.providerName
        if (!providerName.isNullOrBlank() && providerName != UserPreferences.currentProvider?.name) {
            Provider.findByName(providerName)?.let {
                UserPreferences.currentProvider = it
            }
        }
        action()
    }

    private fun displayContinueWatchingMobileItem(binding: ItemEpisodeContinueWatchingMobileBinding) {
        binding.root.apply {
            setOnClickListener {
                checkProviderAndRun {
                findNavController().navigate(
                    HomeMobileFragmentDirections.actionHomeToTvShow(
                        id = episode.tvShow?.id ?: "",
                        poster = episode.tvShow?.poster,
                        banner = episode.tvShow?.banner,
                    )
                )
                findNavController().navigate(
                    TvShowMobileFragmentDirections.actionTvShowToPlayer(
                        id = episode.id,
                        title = episode.tvShow?.title ?: "",
                        subtitle = episode.season?.takeIf { it.number != 0 }?.let { season ->
                            context.getString(
                                R.string.player_subtitle_tv_show,
                                season.number,
                                episode.number,
                                episode.title ?: context.getString(
                                    R.string.episode_number,
                                    episode.number
                                )
                            )
                        } ?: context.getString(
                            R.string.player_subtitle_tv_show_episode_only,
                            episode.number,
                            episode.title ?: context.getString(
                                R.string.episode_number,
                                episode.number
                            )
                        ),
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
                                number = episode.season?.number ?: 0,
                                title = episode.season?.title,
                            ),
                        ),
                    )
                )
                }
            }
            setOnLongClickListener {
                ShowOptionsMobileDialog(context, episode)
                    .show()
                true
            }
        }

        binding.ivEpisodeTvShowPoster.apply {
            clipToOutline = true
            loadContinueWatchingArtwork()
        }

        binding.pbEpisodeProgress.apply {
            val watchHistory = episode.watchHistory

            progress = when {
                watchHistory != null -> (watchHistory.lastPlaybackPositionMillis * 100 / watchHistory.durationMillis.toDouble()).toInt()
                else -> 0
            }
            visibility = when {
                watchHistory != null -> View.VISIBLE
                else -> View.GONE
            }
        }

        binding.tvEpisodeTvShowTitle.text = episode.tvShow?.title ?: ""

        binding.tvEpisodeInfo.text = episode.season?.takeIf { it.number != 0 }?.let { season ->
            context.getString(
                R.string.episode_item_info,
                season.number,
                episode.number,
                episode.title ?: context.getString(
                    R.string.episode_number,
                    episode.number
                )
            )
        } ?: context.getString(
            R.string.episode_item_info_episode_only,
            episode.number,
            episode.title ?: context.getString(
                R.string.episode_number,
                episode.number
            )
        )
    }

    private fun displayContinueWatchingTvItem(binding: ItemEpisodeContinueWatchingTvBinding) {
        binding.root.apply {
            setOnClickListener {
                checkProviderAndRun {
                findNavController().navigate(
                    HomeTvFragmentDirections.actionHomeToTvShow(
                        id = episode.tvShow?.id ?: "",
                        poster = episode.tvShow?.poster,
                        banner = episode.tvShow?.banner,
                    )
                )
                findNavController().navigate(
                    TvShowTvFragmentDirections.actionTvShowToPlayer(
                        id = episode.id,
                        title = episode.tvShow?.title ?: "",
                        subtitle = episode.season?.takeIf { it.number != 0 }?.let { season ->
                            context.getString(
                                R.string.player_subtitle_tv_show,
                                season.number,
                                episode.number,
                                episode.title ?: context.getString(
                                    R.string.episode_number,
                                    episode.number
                                )
                            )
                        } ?: context.getString(
                            R.string.player_subtitle_tv_show_episode_only,
                            episode.number,
                            episode.title ?: context.getString(
                                R.string.episode_number,
                                episode.number
                            )
                        ),
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
                                number = episode.season?.number ?: 0,
                                title = episode.season?.title,
                            ),
                        ),
                    )
                )
                }
            }
            setOnLongClickListener {
                ShowOptionsTvDialog(context, episode)
                    .show()
                true
            }
            setOnFocusChangeListener { _, hasFocus ->
                val animation = when {
                    hasFocus -> AnimationUtils.loadAnimation(context, R.anim.zoom_in)
                    else -> AnimationUtils.loadAnimation(context, R.anim.zoom_out)
                }
                binding.root.startAnimation(animation)
                animation.fillAfter = true

                when (val fragment = context.toActivity()?.getCurrentFragment()) {
                    is HomeTvFragment -> {
                        if (hasFocus) {
                            fragment.pinBackground(episode.tvShow?.banner)
                        } else {
                            fragment.releasePinnedBackground()
                        }
                    }
                }
            }
        }

        binding.ivEpisodeTvShowPoster.apply {
            clipToOutline = true
            loadContinueWatchingArtwork(withFallback = true)
        }

        binding.pbEpisodeProgress.apply {
            val watchHistory = episode.watchHistory

            progress = when {
                watchHistory != null -> (watchHistory.lastPlaybackPositionMillis * 100 / watchHistory.durationMillis.toDouble()).toInt()
                episode.isWatched -> 100
                else -> 0
            }
            visibility = when {
                watchHistory != null -> View.VISIBLE
                episode.isWatched -> View.VISIBLE
                else -> View.GONE
            }
        }

        binding.tvEpisodeTvShowTitle.text = episode.tvShow?.title ?: ""

        binding.tvEpisodeInfo.text = episode.season?.takeIf { it.number != 0 }?.let { season ->
            context.getString(
                R.string.episode_item_info,
                season.number,
                episode.number,
                episode.title ?: context.getString(
                    R.string.episode_number,
                    episode.number
                )
            )
        } ?: context.getString(
            R.string.episode_item_info_episode_only,
            episode.number,
            episode.title ?: context.getString(
                R.string.episode_number,
                episode.number
            )
        )
    }

    private fun episodeDownloadContentKey(): String? {
        val providerName = UserPreferences.currentProvider?.name ?: return null
        val tvShowId = episode.tvShow?.id ?: return null
        val seasonNumber = episode.season?.number ?: return null
        return DownloadContentKey.episode(
            providerName = providerName,
            tvShowId = tvShowId,
            seasonNumber = seasonNumber,
            episodeNumber = episode.number,
            episodeId = episode.id,
        )
    }

    private fun bindDownloadRibbon(downloadRibbon: View) {
        val boundEpisodeId = episode.id
        val contentKey = episodeDownloadContentKey()
        downloadRibbon.visibility =
            if (contentKey != null && OfflineBadgeStore.isCompleted(context, contentKey)) {
                View.VISIBLE
            } else {
                View.GONE
            }

        downloadRibbonJob?.cancel()
        val lifecycleOwner = itemView.findViewTreeLifecycleOwner()
            ?: context.toActivity()
            ?: return
        downloadRibbonJob = lifecycleOwner.lifecycleScope.launch {
            OfflineBadgeStore.completedKeys(context).collect { keys ->
                if (episode.id != boundEpisodeId) return@collect
                val key = episodeDownloadContentKey()
                downloadRibbon.visibility =
                    if (key != null && keys.contains(key)) View.VISIBLE else View.GONE
            }
        }
    }

    private fun ImageView.loadContinueWatchingArtwork(withFallback: Boolean = false) {
        val tvShow = episode.tvShow
        if (tvShow == null) {
            Glide.with(context)
                .load(episode.poster)
                .error(R.drawable.glide_fallback_cover)
                .apply {
                    if (withFallback) fallback(R.drawable.glide_fallback_cover)
                }
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(this)
            return
        }

        loadTvShowCardArtwork(tvShow) {
            error(R.drawable.glide_fallback_cover)
            apply {
                if (withFallback) fallback(R.drawable.glide_fallback_cover)
            }
            centerCrop()
            transition(DrawableTransitionOptions.withCrossFade())
        }
    }
}
