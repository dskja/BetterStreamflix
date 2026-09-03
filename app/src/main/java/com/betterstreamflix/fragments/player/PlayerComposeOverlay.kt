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
 * Attaches the Compose playback bar to legacy ExoPlayer fragments and hides the
 * duplicated Media3 time bar while keeping center transport and action buttons.
 */
fun Fragment.setupPlayerComposeOverlay(
    composeView: ComposeView,
    playerView: PlayerView,
    player: ExoPlayer,
    playbackController: PlayerPlaybackController,
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
            )
        }
    }

    hideLegacyTimeBar(playerView)

    playerView.setControllerVisibilityListener(
        PlayerView.ControllerVisibilityListener { visibility ->
            composeView.visibility = visibility
        },
    )
}

private fun hideLegacyTimeBar(playerView: PlayerView) {
    val controllerRoot = playerView.findViewById<View>(R.id.cl_exo_controller) ?: return
    listOf(
        R.id.exo_progress,
        R.id.exo_position,
        R.id.exo_duration,
        R.id.tv_time_separator,
    ).forEach { viewId ->
        controllerRoot.findViewById<View>(viewId)?.visibility = View.GONE
    }
}
