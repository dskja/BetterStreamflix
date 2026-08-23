package com.betterstreamflix.accessibility

import android.content.Context
import android.view.KeyEvent
import androidx.media3.exoplayer.ExoPlayer

/**
 * TV accessibility helper — provides enhanced TV navigation support
 * including focus management and DPAD navigation helpers.
 */
object TvAccessibilityHelper {

    /**
     * Check if the device supports leanback.
     */
    fun isTv(context: Context): Boolean {
        return context.packageManager.hasSystemFeature("android.software.leanback")
    }

    /**
     * Check if the device has a touchscreen.
     */
    fun hasTouchscreen(context: Context): Boolean {
        return context.packageManager.hasSystemFeature("android.hardware.touchscreen")
    }

    /**
     * Get the recommended focusable state for TV views.
     */
    fun shouldFocusOnTv(context: Context): Boolean {
        return isTv(context) && !hasTouchscreen(context)
    }

    /**
     * Map a remote key event to a player action.
     */
    fun handlePlayerKey(player: ExoPlayer, keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player.play()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player.pause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                player.seekTo(player.currentPosition + 10_000)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                player.seekTo(player.currentPosition - 10_000)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                player.seekToNextMediaItem()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                player.seekToPreviousMediaItem()
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                player.seekTo(player.currentPosition + 60_000)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                player.seekTo(player.currentPosition - 60_000)
                return true
            }
        }
        return false
    }
}
