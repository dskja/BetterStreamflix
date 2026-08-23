package com.betterstreamflix.ui

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import androidx.core.view.isGone
import androidx.core.view.isVisible

/**
 * Helper for showing/hiding loading shimmer placeholders.
 */
object ShimmerHelper {

    /**
     * Show a shimmer loading placeholder.
     */
    fun show(view: View) {
        view.isVisible = true
        val alphaAnim = AlphaAnimation(0.3f, 0.7f).apply {
            duration = 800
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        view.startAnimation(alphaAnim)
    }

    /**
     * Hide a shimmer loading placeholder.
     */
    fun hide(view: View) {
        view.clearAnimation()
        view.isGone = true
    }

    /**
     * Toggle shimmer based on loading state.
     */
    fun toggle(view: View, isLoading: Boolean) {
        if (isLoading) show(view) else hide(view)
    }
}

// Note: AnimationHelper has been moved to its own file AnimationHelper.kt
// with enhanced functionality (slide, shake, pulse, etc.)
