package com.betterstreamflix.accessibility

import android.content.Context
import android.content.res.Configuration
import android.os.Build

/**
 * Font scale helper — manages font size preferences for accessibility.
 */
object FontScaleHelper {

    /**
     * Get the current system font scale.
     */
    fun getSystemFontScale(context: Context): Float {
        return context.resources.configuration.fontScale
    }

    /**
     * Check if large fonts are enabled.
     */
    fun isLargeFontEnabled(context: Context): Boolean {
        return getSystemFontScale(context) > 1.2f
    }

    /**
     * Check if extra large fonts are enabled.
     */
    fun isExtraLargeFontEnabled(context: Context): Boolean {
        return getSystemFontScale(context) > 1.5f
    }

    /**
     * Get recommended text size multiplier based on font scale.
     */
    fun getTextSizeMultiplier(context: Context): Float {
        val scale = getSystemFontScale(context)
        return when {
            scale > 1.5f -> 1.5f
            scale > 1.2f -> 1.25f
            scale > 1.0f -> 1.1f
            else -> 1.0f
        }
    }

    /**
     * Check if the device is in high contrast mode.
     */
    fun isHighContrastMode(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
}
