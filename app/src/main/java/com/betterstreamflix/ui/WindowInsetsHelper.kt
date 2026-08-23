package com.betterstreamflix.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Window insets helper — handles status bar and navigation bar insets
 * for edge-to-edge layouts.
 */
object WindowInsetsHelper {

    /**
     * Apply window insets as padding to a view.
     */
    fun applyWindowInsets(view: View, applyTop: Boolean = true, applyBottom: Boolean = true) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                if (applyTop) systemBars.top else v.paddingTop,
                v.paddingRight,
                if (applyBottom) systemBars.bottom else v.paddingBottom,
            )
            insets
        }
    }

    /**
     * Apply window insets to a ViewGroup with optional child padding.
     */
    fun applyWindowInsetsToChildren(viewGroup: ViewGroup, applyTop: Boolean = true, applyBottom: Boolean = true) {
        ViewCompat.setOnApplyWindowInsetsListener(viewGroup) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            for (i in 0 until viewGroup.childCount) {
                val child = viewGroup.getChildAt(i)
                child.setPadding(
                    child.paddingLeft,
                    if (applyTop) systemBars.top else child.paddingTop,
                    child.paddingRight,
                    if (applyBottom) systemBars.bottom else child.paddingBottom,
                )
            }
            insets
        }
    }

    /**
     * Get the status bar height.
     */
    fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    /**
     * Get the navigation bar height.
     */
    fun getNavigationBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }
}
