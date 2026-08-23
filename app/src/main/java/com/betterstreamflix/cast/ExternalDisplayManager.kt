package com.betterstreamflix.cast

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.Display
import android.view.WindowManager

/**
 * External display manager — manages external displays (HDMI, Miracast)
 * and handles display changes.
 */
object ExternalDisplayManager {

    private var isExternalDisplayActive: Boolean = false
    private var externalDisplayMode: ExternalDisplayMode = ExternalDisplayMode.NONE

    enum class ExternalDisplayMode { NONE, MIRROR, EXTENDED, CAST }

    /**
     * Check if an external display is connected.
     */
    fun isExternalDisplayConnected(context: Context): Boolean {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
            ?: return false

        val displays = displayManager.displays
        return displays.size > 1
    }

    /**
     * Get all connected displays.
     */
    fun getConnectedDisplays(context: Context): List<DisplayInfo> {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
            ?: return emptyList()

        return displayManager.displays.mapIndexed { index, display ->
            DisplayInfo(
                id = display.displayId,
                name = "Display ${index + 1}",
                width = display.mode?.physicalWidth ?: 0,
                height = display.mode?.physicalHeight ?: 0,
                refreshRate = display.mode?.refreshRate ?: 60f,
                isExternal = display.displayId != Display.DEFAULT_DISPLAY,
            )
        }
    }

    /**
     * Set the external display mode.
     */
    fun setExternalDisplayMode(mode: ExternalDisplayMode) {
        externalDisplayMode = mode
        isExternalDisplayActive = mode != ExternalDisplayMode.NONE
    }

    /**
     * Get the current external display mode.
     */
    fun getExternalDisplayMode(): ExternalDisplayMode = externalDisplayMode

    /**
     * Check if currently using an external display.
     */
    fun isUsingExternalDisplay(): Boolean = isExternalDisplayActive

    /**
     * Keep screen on while using external display.
     */
    fun keepScreenOn(activity: Activity, keepOn: Boolean) {
        if (keepOn) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Get the optimal video resolution for the current display.
     */
    fun getOptimalResolution(context: Context): Pair<Int, Int> {
        if (!isExternalDisplayConnected(context)) {
            val metrics = context.resources.displayMetrics
            return metrics.widthPixels to metrics.heightPixels
        }

        val displays = getConnectedDisplays(context)
        val external = displays.firstOrNull { it.isExternal }
        if (external != null) {
            return external.width to external.height
        }

        val metrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    data class DisplayInfo(
        val id: Int,
        val name: String,
        val width: Int,
        val height: Int,
        val refreshRate: Float,
        val isExternal: Boolean,
    )
}
