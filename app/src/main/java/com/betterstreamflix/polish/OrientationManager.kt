package com.betterstreamflix.polish

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit

/**
 * Orientation manager — handles orientation changes and responsive
 * layout adjustments.
 */
object OrientationManager {

    /**
     * Check if the device is in landscape orientation.
     */
    fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    /**
     * Check if the device is in portrait orientation.
     */
    fun isPortrait(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    /**
     * Get the number of columns for a grid based on orientation.
     */
    fun getGridColumns(context: Context, portraitColumns: Int, landscapeColumns: Int): Int {
        return if (isLandscape(context)) landscapeColumns else portraitColumns
    }

    /**
     * Check if the layout should use a two-pane master-detail layout.
     */
    fun shouldUseTwoPane(context: Context): Boolean {
        val widthDp = context.resources.configuration.screenWidthDp
        return widthDp >= 900 && isLandscape(context)
    }

    /**
     * Get the optimal player aspect ratio for current orientation.
     */
    fun getPlayerAspectRatio(context: Context): Float {
        return if (isLandscape(context)) 16f / 9f else 4f / 3f
    }
}
