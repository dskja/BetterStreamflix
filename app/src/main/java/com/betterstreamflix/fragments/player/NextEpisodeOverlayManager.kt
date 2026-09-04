package com.betterstreamflix.fragments.player

import android.view.animation.AnimationUtils
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.ExoPlayer
import com.betterstreamflix.R
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.databinding.ContentExoControllerMobileBinding
import com.betterstreamflix.databinding.FragmentPlayerMobileBinding
import com.betterstreamflix.models.Video
import com.betterstreamflix.utils.EpisodeManager
import com.betterstreamflix.utils.UserPreferences
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the "Next Episode" overlay and skip-intro button for the mobile player.
 * Extracted from PlayerMobileFragment for separation of concerns.
 */
class NextEpisodeOverlayManager(
    private val fragment: Fragment,
    private val player: ExoPlayer,
    private val database: AppDatabase,
    private val playbackController: PlayerPlaybackController? = null,
) {
    private var nextEpisodeOverlayDismissed = false
    private var nextEpisodePrefetchTargetId: String? = null
    private var nextEpisodePrefetchJob: Job? = null

    /** Called on the main thread after a next-episode prefetch completes. */
    var onPrefetchComplete: (() -> Unit)? = null

    private val binding: FragmentPlayerMobileBinding?
        get() = fragment.view?.let { FragmentPlayerMobileBinding.bind(it) }

    private val controllerBinding: ContentExoControllerMobileBinding?
        get() = binding?.pvPlayer?.controller?.let {
            ContentExoControllerMobileBinding.bind(it.findViewById(R.id.cl_exo_controller))
        }

    fun dismissOverlay() {
        nextEpisodeOverlayDismissed = true
        hideNextEpisodeOverlay()
    }

    fun hideOverlay() {
        hideNextEpisodeOverlay()
    }

    fun resetDismissed() {
        nextEpisodeOverlayDismissed = false
    }

    fun updateOverlay(currentVideoType: Video.Type) {
        val currentEpisode = currentVideoType as? Video.Type.Episode ?: run {
            hideNextEpisodeOverlay()
            return
        }
        val duration = player.duration.takeIf { it > 0 } ?: run {
            hideNextEpisodeOverlay()
            return
        }
        val remainingMs = (duration - player.currentPosition).coerceAtLeast(0L)

        if (NextEpisodeOverlayLogic.shouldPrefetchNext(remainingMs)) {
            ensureNextEpisodePrepared(currentEpisode)
        }

        val nextEpisode = EpisodeManager.peekNextEpisode()
        if (!NextEpisodeOverlayLogic.shouldShowOverlay(
                hasNextEpisode = nextEpisode != null,
                remainingMs = remainingMs,
                autoplayBufferSeconds = UserPreferences.autoplayBuffer,
                dismissed = nextEpisodeOverlayDismissed,
            )
        ) {
            hideNextEpisodeOverlay()
            return
        }

        showNextEpisodeOverlay(nextEpisode!!, remainingMs)
    }

    fun updateSkipIntroButton() {
        val show = player.currentPosition in 3000..120000
        showSkipIntroButton(show)
    }

    private fun ensureNextEpisodePrepared(currentEpisode: Video.Type.Episode) {
        if (EpisodeManager.peekNextEpisode() != null) return
        if (nextEpisodePrefetchTargetId == currentEpisode.id && nextEpisodePrefetchJob?.isActive == true) {
            return
        }

        nextEpisodePrefetchTargetId = currentEpisode.id
        nextEpisodePrefetchJob?.cancel()
        nextEpisodePrefetchJob = fragment.lifecycleScope.launch(Dispatchers.IO) {
            val loaded = EpisodeManager.ensureNextEpisodeAvailable(currentEpisode, database)
            withContext(Dispatchers.Main) {
                if (!fragment.isAdded) return@withContext
                if (loaded) {
                    onPrefetchComplete?.invoke()
                }
            }
        }
    }

    private fun showNextEpisodeOverlay(nextEpisode: Video.Type.Episode, remainingMs: Long) {
        val b = binding ?: return
        b.tvNextEpisodeMeta.text = fragment.getString(
            R.string.tv_show_item_season_number_episode_number,
            nextEpisode.season.number,
            nextEpisode.number
        )
        b.tvNextEpisodeTitle.text = nextEpisode.title
            ?: fragment.getString(R.string.episode_number, nextEpisode.number)
        b.tvNextEpisodeCountdown.text = if (UserPreferences.autoplay) {
            fragment.getString(
                R.string.player_next_episode_autoplay_in,
                NextEpisodeOverlayLogic.countdownSeconds(remainingMs),
            )
        } else {
            fragment.getString(R.string.player_next_episode_ready)
        }

        Glide.with(fragment)
            .load(nextEpisode.poster ?: nextEpisode.tvShow.poster)
            .error(R.drawable.glide_fallback_cover)
            .fallback(R.drawable.glide_fallback_cover)
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(b.ivNextEpisodePoster)

        if (b.layoutNextEpisodeOverlay.isGone) {
            val fadeIn = AnimationUtils.loadAnimation(fragment.requireContext(), R.anim.fade_in)
            b.layoutNextEpisodeOverlay.startAnimation(fadeIn)
            b.layoutNextEpisodeOverlay.isVisible = true
        }
    }

    private fun hideNextEpisodeOverlay() {
        val b = binding ?: return
        if (b.layoutNextEpisodeOverlay.isVisible) {
            val fadeOut = AnimationUtils.loadAnimation(fragment.requireContext(), R.anim.fade_out)
            b.layoutNextEpisodeOverlay.startAnimation(fadeOut)
            b.layoutNextEpisodeOverlay.isGone = true
        }
    }

    private fun showSkipIntroButton(show: Boolean) {
        playbackController?.setSkipIntroVisible(show)
        val cb = controllerBinding ?: return
        val btnSkipIntro = cb.btnSkipIntro
        if (show && btnSkipIntro.isGone) {
            val fadeIn = AnimationUtils.loadAnimation(fragment.requireContext(), R.anim.fade_in)
            btnSkipIntro.startAnimation(fadeIn)
            btnSkipIntro.isVisible = true
        } else if (!show && btnSkipIntro.isVisible) {
            val fadeOut = AnimationUtils.loadAnimation(fragment.requireContext(), R.anim.fade_out)
            btnSkipIntro.startAnimation(fadeOut)
            btnSkipIntro.isGone = true
        }
    }

    fun cancelPrefetch() {
        nextEpisodePrefetchJob?.cancel()
    }
}
