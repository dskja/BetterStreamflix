package com.betterstreamflix.tv

import android.content.Context
import android.content.res.Resources
import com.betterstreamflix.utils.AppConfig

/**
 * TV layout helper — provides TV-specific layout calculations and
 * responsive sizing.
 */
object TvLayoutHelper {

    /**
     * Check if the app is running on a TV.
     */
    fun isTvLayout(): Boolean = AppConfig.isTv

    /**
     * Get the number of columns for grid layouts.
     */
    fun getGridColumnCount(context: Context): Int {
        val widthDp = context.resources.configuration.screenWidthDp
        return when {
            widthDp >= 960 -> 6
            widthDp >= 720 -> 5
            widthDp >= 540 -> 4
            widthDp >= 360 -> 3
            else -> 2
        }
    }

    /**
     * Get optimal card spacing in pixels.
     */
    fun getCardSpacing(context: Context): Int {
        val density = context.resources.displayMetrics.density
        return (16 * density).toInt()
    }

    /**
     * Get the content padding for TV layouts.
     */
    fun getContentPadding(context: Context): IntArray {
        val density = context.resources.displayMetrics.density
        val horizontal = (48 * density).toInt()
        val vertical = (24 * density).toInt()
        return intArrayOf(horizontal, vertical, horizontal, vertical)
    }

    /**
     * Get the banner height for TV headers.
     */
    fun getBannerHeight(context: Context): Int {
        val density = context.resources.displayMetrics.density
        return if (isTvLayout()) (200 * density).toInt() else (160 * density).toInt()
    }

    /**
     * Get the detail backdrop height.
     */
    fun getDetailBackdropHeight(context: Context): Int {
        val density = context.resources.displayMetrics.density
        return if (isTvLayout()) (400 * density).toInt() else (280 * density).toInt()
    }

    /**
     * Check if the layout should use leanback fragments.
     */
    fun shouldUseLeanback(): Boolean = isTvLayout()

    /**
     * Get the focus scale factor for TV cards.
     */
    fun getFocusScaleFactor(): Float = if (isTvLayout()) 1.1f else 1.05f
}
