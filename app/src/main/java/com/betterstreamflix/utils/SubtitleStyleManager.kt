package com.betterstreamflix.utils

import android.content.Context
import android.view.accessibility.CaptioningManager
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView

/**
 * Subtitle style manager — applies system caption settings or custom styles
 * to ExoPlayer subtitle rendering.
 */
object SubtitleStyleManager {

    /**
     * Apply system caption style to a PlayerView.
     */
    fun applySystemCaptionStyle(context: Context, playerView: PlayerView) {
        val captioningManager = context.getSystemService(Context.CAPTIONING_SERVICE)
            as? CaptioningManager ?: return

        if (!captioningManager.isEnabled) {
            playerView.subtitleView?.setApplyEmbeddedStyles(true)
            return
        }

        val style = CaptionStyleCompat.createFromCaptionStyle(
            captioningManager.userStyle
        )
        playerView.subtitleView?.apply {
            setApplyEmbeddedStyles(false)
            setStyle(style)
            setFractionalTextSize(0.0533f * captioningManager.fontScale)
        }
    }

    /**
     * Apply custom subtitle style.
     */
    fun applyCustomStyle(
        playerView: PlayerView,
        style: CaptionStyleCompat,
        textSizeFraction: Float = 0.0533f,
    ) {
        playerView.subtitleView?.apply {
            setApplyEmbeddedStyles(false)
            setStyle(style)
            setFractionalTextSize(textSizeFraction)
        }
    }

    /**
     * Get the system font scale for captions.
     */
    fun getSystemFontScale(context: Context): Float {
        val captioningManager = context.getSystemService(Context.CAPTIONING_SERVICE)
            as? CaptioningManager ?: return 1f
        return captioningManager.fontScale
    }

    /**
     * Check if system captions are enabled.
     */
    fun isSystemCaptioningEnabled(context: Context): Boolean {
        val captioningManager = context.getSystemService(Context.CAPTIONING_SERVICE)
            as? CaptioningManager ?: return false
        return captioningManager.isEnabled
    }
}
