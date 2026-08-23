package com.betterstreamflix.widgets

import android.content.Context
import android.os.Build

/**
 * Widget compatibility checker — checks if widgets are supported
 * on the current device and Android version.
 */
object WidgetCompatibilityChecker {

    /**
     * Check if widgets are supported.
     */
    fun areWidgetsSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2
    }

    /**
     * Check if widget pinning is supported (Android 8+).
     */
    fun isWidgetPinningSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    /**
     * Check if remote views are supported.
     */
    fun areRemoteViewsSupported(): Boolean = areWidgetsSupported()

    /**
     * Check if widget previews are supported.
     */
    fun areWidgetPreviewsSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
    }

    /**
     * Get widget compatibility info.
     */
    fun getCompatibilityInfo(): WidgetCompatibility {
        return WidgetCompatibility(
            widgetsSupported = areWidgetsSupported(),
            pinningSupported = isWidgetPinningSupported(),
            remoteViewsSupported = areRemoteViewsSupported(),
            previewsSupported = areWidgetPreviewsSupported(),
            minApiLevel = Build.VERSION_CODES.HONEYCOMB_MR2,
            currentApiLevel = Build.VERSION.SDK_INT,
        )
    }

    data class WidgetCompatibility(
        val widgetsSupported: Boolean,
        val pinningSupported: Boolean,
        val remoteViewsSupported: Boolean,
        val previewsSupported: Boolean,
        val minApiLevel: Int,
        val currentApiLevel: Int,
    )
}
