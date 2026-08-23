package com.betterstreamflix.player.advanced

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

/**
 * Background audio manager — allows audio to continue playing
 * when the app goes to background.
 */
object BackgroundAudioManager {

    private var wasPlayingBeforeBackground: Boolean = false

    /**
     * Handle app going to background.
     */
    fun onAppBackground(player: ExoPlayer) {
        wasPlayingBeforeBackground = player.isPlaying
        // Keep audio playing in background
    }

    /**
     * Handle app returning to foreground.
     */
    fun onAppForeground(player: ExoPlayer) {
        // Restore video surface if needed
        wasPlayingBeforeBackground = false
    }

    /**
     * Check if audio should continue in background.
     */
    fun shouldContinueInBackground(player: ExoPlayer): Boolean {
        return player.isPlaying && player.currentMediaItem != null
    }

    /**
     * Pause audio when going to background (if configured).
     */
    fun pauseForBackground(player: ExoPlayer, pauseOnBackground: Boolean) {
        if (pauseOnBackground) {
            player.pause()
        }
    }
}
