package com.betterstreamflix.fragments.player

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.ExoPlayer
import com.betterstreamflix.R
import com.betterstreamflix.compose.screens.NextEpisodeOverlayCard
import com.betterstreamflix.compose.screens.NextEpisodeOverlayUiState
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Video
import com.betterstreamflix.utils.EpisodeManager
import com.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared next-episode overlay + skip-intro wiring for mobile and TV players.
 * UI is a single Compose card; timing comes from [NextEpisodeOverlayLogic].
 */
class NextEpisodeOverlayManager(
    private val fragment: Fragment,
    private val player: ExoPlayer,
    private val database: AppDatabase,
    private val composeView: ComposeView,
    private val playbackController: PlayerPlaybackController? = null,
    private val isTvLayout: Boolean = false,
) {
    private var nextEpisodeOverlayDismissed = false
    private var nextEpisodePrefetchTargetId: String? = null
    private var nextEpisodePrefetchJob: Job? = null
    private var hasRequestedInitialFocus = false

    private val _uiState = MutableStateFlow<NextEpisodeOverlayUiState?>(null)
    val uiState: StateFlow<NextEpisodeOverlayUiState?> = _uiState.asStateFlow()

    /** Called on the main thread after a next-episode prefetch completes. */
    var onPrefetchComplete: (() -> Unit)? = null

    /** Play-now action from the Compose card. */
    var onPlayNext: (() -> Unit)? = null

    /** Fired when overlay visibility changes (for TV focus bindings). */
    var onVisibilityChanged: ((Boolean) -> Unit)? = null

    init {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            BetterStreamflixTheme {
                val state by uiState.collectAsStateWithLifecycle()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = if (isTvLayout) 24.dp else 18.dp, bottom = if (isTvLayout) 24.dp else 18.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    NextEpisodeOverlayCard(
                        state = state,
                        onPlay = {
                            hideOverlay()
                            onPlayNext?.invoke()
                        },
                        onDismiss = { dismissOverlay() },
                        isTvLayout = isTvLayout,
                    )
                }
            }
        }
        composeView.isVisible = false
    }

    fun dismissOverlay() {
        nextEpisodeOverlayDismissed = true
        hideOverlay()
    }

    fun hideOverlay() {
        val wasVisible = _uiState.value != null
        _uiState.value = null
        hasRequestedInitialFocus = false
        composeView.isVisible = false
        if (wasVisible) {
            onVisibilityChanged?.invoke(false)
        }
    }

    fun resetDismissed() {
        nextEpisodeOverlayDismissed = false
        hasRequestedInitialFocus = false
    }

    fun isOverlayVisible(): Boolean = _uiState.value != null

    fun updateOverlay(currentVideoType: Video.Type) {
        val currentEpisode = currentVideoType as? Video.Type.Episode ?: run {
            hideOverlay()
            return
        }
        val duration = player.duration.takeIf { it > 0 } ?: run {
            hideOverlay()
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
            hideOverlay()
            return
        }

        showNextEpisodeOverlay(nextEpisode!!, remainingMs)
    }

    fun updateSkipIntroButton() {
        val show = player.currentPosition in 3000..120000
        playbackController?.setSkipIntroVisible(show)
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
        val ctx = fragment.requireContext()
        val meta = ctx.getString(
            R.string.tv_show_item_season_number_episode_number,
            nextEpisode.season.number,
            nextEpisode.number,
        )
        val title = nextEpisode.title
            ?: ctx.getString(R.string.episode_number, nextEpisode.number)
        val countdown = if (UserPreferences.autoplay) {
            ctx.getString(
                R.string.player_next_episode_autoplay_in,
                NextEpisodeOverlayLogic.countdownSeconds(remainingMs),
            )
        } else {
            ctx.getString(R.string.player_next_episode_ready)
        }

        val wasHidden = _uiState.value == null
        val requestFocus = isTvLayout && wasHidden && !hasRequestedInitialFocus
        if (requestFocus) {
            hasRequestedInitialFocus = true
        }

        _uiState.value = NextEpisodeOverlayUiState(
            posterUrl = nextEpisode.poster ?: nextEpisode.tvShow.poster,
            meta = meta,
            title = title,
            countdownLabel = countdown,
            requestInitialFocus = requestFocus,
        )

        if (!composeView.isVisible) {
            composeView.isVisible = true
            composeView.isFocusable = isTvLayout
            composeView.isFocusableInTouchMode = isTvLayout
            onVisibilityChanged?.invoke(true)
            if (requestFocus) {
                composeView.post {
                    if (_uiState.value != null) {
                        composeView.requestFocus()
                    }
                }
            }
        }
    }

    fun cancelPrefetch() {
        nextEpisodePrefetchJob?.cancel()
    }

    /**
     * Wire D-pad focus between next-episode ComposeView and playback chrome ComposeView.
     * Shows Media3 controller first so [playbackComposeView] is VISIBLE — otherwise Down
     * targets a GONE chrome and traps focus on Play/Dismiss.
     */
    fun bindTvFocusToPlaybackChrome(
        playbackComposeView: View,
        playerView: androidx.media3.ui.PlayerView?,
        overlayVisible: Boolean,
    ) {
        if (!isTvLayout) return
        if (overlayVisible) {
            playerView?.controllerShowTimeoutMs = 0
            playerView?.showController()
            playbackComposeView.visibility = View.VISIBLE
            composeView.nextFocusDownId = playbackComposeView.id
            playbackComposeView.nextFocusUpId = composeView.id
        } else {
            if (playerView != null && playerView.controllerShowTimeoutMs == 0) {
                playerView.controllerShowTimeoutMs = 4_000
            }
            composeView.nextFocusDownId = View.NO_ID
            playbackComposeView.nextFocusUpId = View.NO_ID
        }
    }
}
