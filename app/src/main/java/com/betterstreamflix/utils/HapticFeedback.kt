package com.betterstreamflix.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic feedback helper for touch interactions.
 */
object HapticFeedback {

    /**
     * Vibrate for a short duration (tap feedback).
     */
    fun tap(context: Context) {
        vibrate(context, 50)
    }

    /**
     * Vibrate for a medium duration (selection confirmation).
     */
    fun select(context: Context) {
        vibrate(context, 100)
    }

    /**
     * Vibrate with a pattern (for errors or special events).
     */
    fun pattern(context: Context, timings: LongArray = longArrayOf(0, 100, 50, 100)) {
        val vibrator = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    /**
     * Vibrate for a custom duration.
     */
    private fun vibrate(context: Context, durationMs: Long) {
        val vibrator = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
