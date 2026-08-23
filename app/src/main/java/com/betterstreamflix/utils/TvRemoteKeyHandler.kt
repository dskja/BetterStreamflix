package com.betterstreamflix.utils

import android.content.Context
import android.view.KeyEvent
import androidx.core.view.KeyEventDispatcher

/**
 * TV remote key handler — centralizes DPAD remote control navigation.
 */
class TvRemoteKeyHandler(
    private val onDpadCenter: () -> Unit,
    private val onDpadUp: () -> Unit = {},
    private val onDpadDown: () -> Unit = {},
    private val onDpadLeft: () -> Unit = {},
    private val onDpadRight: () -> Unit = {},
    private val onBack: () -> Unit = {},
    private val onPlayPause: () -> Unit = {},
    private val onFastForward: () -> Unit = {},
    private val onRewind: () -> Unit = {},
    private val onChannelUp: () -> Unit = {},
    private val onChannelDown: () -> Unit = {},
) {
    /**
     * Handle a key event. Returns true if handled.
     */
    fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> { onDpadCenter(); true }
            KeyEvent.KEYCODE_DPAD_UP -> { onDpadUp(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { onDpadDown(); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { onDpadLeft(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { onDpadRight(); true }
            KeyEvent.KEYCODE_BACK -> { onBack(); true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { onPlayPause(); true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { onFastForward(); true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { onRewind(); true }
            KeyEvent.KEYCODE_CHANNEL_UP -> { onChannelUp(); true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { onChannelDown(); true }
            else -> false
        }
    }
}

/**
 * TV focus helper — ensures proper focus navigation for TV layouts.
 */
object TvFocusHelper {

    /**
     * Check if a view should be focusable on TV.
     */
    fun isTvFocusable(context: Context): Boolean {
        return context.packageManager.hasSystemFeature("android.software.leanback")
    }

    /**
     * Get the recommended focus scale factor for TV.
     */
    fun getFocusScaleFactor(): Float = 1.1f
}
