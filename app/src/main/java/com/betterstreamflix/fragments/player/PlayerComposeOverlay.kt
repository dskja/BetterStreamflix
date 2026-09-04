package com.betterstreamflix.fragments.player

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.betterstreamflix.R
import com.betterstreamflix.compose.screens.PlayerControlsOverlay
import com.betterstreamflix.compose.theme.BetterStreamflixTheme

/**
 * Single Compose player chrome. Legacy Media3 controller stays inflated for
 * existing click wiring but is fully hidden so users never see double UI.
 */
fun Fragment.setupPlayerComposeOverlay(
    composeView: ComposeView,
    playerView: PlayerView,
    player: ExoPlayer,
    playbackController: PlayerPlaybackController,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onPip: (() -> Unit)? = null,
    onAspectRatio: (() -> Unit)? = null,
    onExternalPlayer: (() -> Unit)? = null,
    onPreviousEpisode: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    onSkipIntro: (() -> Unit)? = null,
    onToggleLock: (() -> Unit)? = null,
    onCaptions: (() -> Unit)? = null,
) {
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    composeView.setContent {
        BetterStreamflixTheme {
            val state by playbackController.state.collectAsStateWithLifecycle()
            PlayerControlsOverlay(
                state = state,
                onPlayPause = {
                    if (player.isPlaying) player.pause() else player.play()
                },
                onSeek = { positionMs -> player.seekTo(positionMs) },
                visible = true,
                onBack = onBack,
                onSettings = onSettings,
                onPip = onPip,
                onAspectRatio = onAspectRatio,
                onExternalPlayer = onExternalPlayer,
                onPreviousEpisode = onPreviousEpisode,
                onNextEpisode = onNextEpisode,
                onSkipIntro = onSkipIntro,
                onToggleLock = onToggleLock,
                onCaptions = onCaptions,
            )
        }
    }

    hideLegacyPlayerChrome(playerView)

    // Keep Media3 controller for touch timeout / accessibility show-hide,
    // but never paint it — Compose is the only visible chrome.
    playerView.useController = true
    playerView.controllerShowTimeoutMs = 4_000
    playerView.setControllerVisibilityListener(
        PlayerView.ControllerVisibilityListener { visibility ->
            composeView.visibility = visibility
            // Re-hide legacy every time Media3 tries to show it.
            if (visibility == View.VISIBLE) {
                hideLegacyPlayerChrome(playerView)
            }
        },
    )
    playerView.showController()
}

fun hideLegacyPlayerChrome(playerView: PlayerView) {
    val controllerRoot = playerView.findViewById<View>(R.id.cl_exo_controller)
    if (controllerRoot != null) {
        controllerRoot.alpha = 0f
        controllerRoot.isClickable = false
        listOf(
            R.id.exo_controls_background,
            R.id.btn_exo_back,
            R.id.tv_exo_title,
            R.id.tv_exo_subtitle,
            R.id.btn_exo_external_player,
            R.id.exo_center_controls,
            R.id.exo_bottom_bar,
            R.id.exo_progress,
            R.id.exo_position,
            R.id.exo_duration,
            R.id.tv_time_separator,
            R.id.btn_skip_intro,
            R.id.btn_exo_lock,
            R.id.btn_exo_unlock,
            R.id.btn_exo_picture_in_picture,
            R.id.btn_exo_aspect_ratio,
            R.id.exo_settings,
            R.id.g_controls_lock,
        ).forEach { viewId ->
            controllerRoot.findViewById<View>(viewId)?.apply {
                visibility = View.GONE
                alpha = 0f
            }
        }
        return
    }

    // Default Media3 styled controller (offline player).
    listOf(
        androidx.media3.ui.R.id.exo_center_controls,
        androidx.media3.ui.R.id.exo_bottom_bar,
        androidx.media3.ui.R.id.exo_progress,
        androidx.media3.ui.R.id.exo_position,
        androidx.media3.ui.R.id.exo_duration,
        androidx.media3.ui.R.id.exo_play_pause,
        androidx.media3.ui.R.id.exo_rew,
        androidx.media3.ui.R.id.exo_ffwd,
        androidx.media3.ui.R.id.exo_settings,
    ).forEach { viewId ->
        playerView.findViewById<View>(viewId)?.visibility = View.GONE
    }
    playerView.findViewById<View>(androidx.media3.ui.R.id.exo_controls_background)?.alpha = 0f
}
