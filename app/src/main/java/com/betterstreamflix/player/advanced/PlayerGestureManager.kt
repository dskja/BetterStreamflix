package com.betterstreamflix.player.advanced

import android.content.Context
import androidx.core.content.edit

/**
 * Player gesture manager — manages gesture-based controls like
 * swipe to seek, tap to pause, double-tap to skip.
 */
object PlayerGestureManager {

    private const val PREFS_NAME = "player_gestures"
    private const val KEY_SEEK_AMOUNT = "seek_amount_ms"
    private const val DEFAULT_SEEK_MS = 10_000L

    /**
     * Get the seek amount for swipe gestures.
     */
    fun getSeekAmountMs(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_SEEK_AMOUNT, DEFAULT_SEEK_MS)
    }

    /**
     * Set the seek amount for swipe gestures.
     */
    fun setSeekAmountMs(context: Context, ms: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putLong(KEY_SEEK_AMOUNT, ms)
        }
    }

    /**
     * Calculate seek position from a horizontal swipe.
     */
    fun calculateSeekFromSwipe(
        currentPosition: Long,
        duration: Long,
        swipeDeltaX: Float,
        viewWidth: Int,
        seekAmountMs: Long = DEFAULT_SEEK_MS,
    ): Long {
        if (viewWidth == 0) return currentPosition
        val ratio = swipeDeltaX / viewWidth
        val seekDelta = (ratio * seekAmountMs * 10).toLong()
        return (currentPosition + seekDelta).coerceIn(0, duration)
    }

    /**
     * Calculate volume from a vertical swipe.
     */
    fun calculateVolumeFromSwipe(
        currentVolume: Int,
        maxVolume: Int,
        swipeDeltaY: Float,
        viewHeight: Int,
    ): Int {
        if (viewHeight == 0) return currentVolume
        val ratio = -swipeDeltaY / viewHeight
        val volumeDelta = (ratio * maxVolume).toInt()
        return (currentVolume + volumeDelta).coerceIn(0, maxVolume)
    }

    /**
     * Calculate brightness from a vertical swipe.
     */
    fun calculateBrightnessFromSwipe(
        currentBrightness: Float,
        swipeDeltaY: Float,
        viewHeight: Int,
    ): Float {
        if (viewHeight == 0) return currentBrightness
        val ratio = -swipeDeltaY / viewHeight
        return (currentBrightness + ratio).coerceIn(0f, 1f)
    }

    /**
     * Detect gesture type from motion event.
     */
    fun detectGestureType(
        deltaX: Float,
        deltaY: Float,
        threshold: Float = 50f,
    ): GestureType {
        val absX = kotlin.math.abs(deltaX)
        val absY = kotlin.math.abs(deltaY)

        return when {
            absX < threshold && absY < threshold -> GestureType.TAP
            absX > absY && absX > threshold -> GestureType.SWIPE_HORIZONTAL
            absY > absX && absY > threshold -> GestureType.SWIPE_VERTICAL
            else -> GestureType.NONE
        }
    }

    enum class GestureType {
        TAP,
        DOUBLE_TAP,
        SWIPE_HORIZONTAL,
        SWIPE_VERTICAL,
        LONG_PRESS,
        NONE,
    }
}
