package com.betterstreamflix.player

import android.content.Context
import android.provider.Settings
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * Manages screen brightness for video playback.
 * Saves and restores system brightness when entering/leaving player.
 */
class BrightnessManager(private val activity: AppCompatActivity) {

    private var originalBrightness: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

    /**
     * Save current system brightness and switch to custom.
     */
    fun beginCustomBrightness() {
        originalBrightness = activity.window.attributes.screenBrightness
    }

    /**
     * Set brightness level (0.0 to 1.0).
     */
    fun setBrightness(level: Float) {
        val attrs = activity.window.attributes
        attrs.screenBrightness = level.coerceIn(-1f, 1f)
        activity.window.attributes = attrs
    }

    /**
     * Get current brightness level.
     */
    fun getBrightness(): Float {
        return activity.window.attributes.screenBrightness
    }

    /**
     * Restore original system brightness.
     */
    fun restoreBrightness() {
        val attrs = activity.window.attributes
        attrs.screenBrightness = originalBrightness
        activity.window.attributes = attrs
    }

    /**
     * Increase brightness by a step.
     */
    fun increaseBrightness(step: Float = 0.1f) {
        val current = activity.window.attributes.screenBrightness
        val newBrightness = if (current < 0) 0.5f + step else (current + step).coerceAtMost(1f)
        setBrightness(newBrightness)
    }

    /**
     * Decrease brightness by a step.
     */
    fun decreaseBrightness(step: Float = 0.1f) {
        val current = activity.window.attributes.screenBrightness
        val newBrightness = if (current < 0) 0.5f - step else (current - step).coerceAtLeast(0f)
        setBrightness(newBrightness)
    }
}
