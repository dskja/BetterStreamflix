package com.betterstreamflix.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Monitors player state and provides reactive callbacks for UI updates.
 */
class PlayerStateMonitor(private val player: ExoPlayer) {

    private var listener: Player.Listener? = null

    /**
     * Start monitoring player state.
     */
    fun startMonitoring(
        onPlaying: () -> Unit = {},
        onPaused: () -> Unit = {},
        onBuffering: () -> Unit = {},
        onEnded: () -> Unit = {},
        onPositionUpdate: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    ) {
        listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> onBuffering()
                    Player.STATE_READY -> {
                        if (player.isPlaying) onPlaying() else onPaused()
                    }
                    Player.STATE_ENDED -> onEnded()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) onPlaying() else onPaused()
            }
        }
        listener?.let { player.addListener(it) }
    }

    /**
     * Stop monitoring.
     */
    fun stopMonitoring() {
        listener?.let { player.removeListener(it) }
        listener = null
    }
}

/**
 * Player state sealed class for UI representation.
 */
sealed class PlayerState {
    data object Idle : PlayerState()
    data object Buffering : PlayerState()
    data object Ready : PlayerState()
    data object Ended : PlayerState()
}

/**
 * Convert ExoPlayer state int to PlayerState.
 */
fun Int.toPlayerState(): PlayerState = when (this) {
    Player.STATE_IDLE -> PlayerState.Idle
    Player.STATE_BUFFERING -> PlayerState.Buffering
    Player.STATE_READY -> PlayerState.Ready
    Player.STATE_ENDED -> PlayerState.Ended
    else -> PlayerState.Idle
}
