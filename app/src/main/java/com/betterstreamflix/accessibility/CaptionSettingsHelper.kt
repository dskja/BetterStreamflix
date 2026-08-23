package com.betterstreamflix.accessibility

import android.content.Context
import android.view.accessibility.CaptioningManager

/**
 * Caption settings helper — bridges system caption settings to ExoPlayer.
 */
object CaptionSettingsHelper {

    /**
     * Check if system captions are enabled.
     */
    fun isCaptioningEnabled(context: Context): Boolean {
        val cm = context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
        return cm?.isEnabled ?: false
    }

    /**
     * Get system caption font scale.
     */
    fun getCaptionFontScale(context: Context): Float {
        val cm = context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
        return cm?.fontScale ?: 1.0f
    }

    /**
     * Get system caption locale.
     */
    fun getCaptionLocale(context: Context): String? {
        val cm = context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
        return cm?.locale?.language
    }

    /**
     * Get the raw caption style from system.
     */
    fun getCaptionStyle(context: Context): CaptioningManager.CaptionStyle? {
        val cm = context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
        return cm?.userStyle
    }
}
