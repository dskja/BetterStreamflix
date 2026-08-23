package com.betterstreamflix.tv

import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * TV focus animator — manages focus animations for TV navigation
 * with scale, elevation, and border effects.
 */
object TvFocusAnimator {

    /**
     * Apply focus scale animation to a view.
     */
    fun applyFocusScale(view: View, hasFocus: Boolean, scaleFactor: Float = 1.1f) {
        view.animate()
            .scaleX(if (hasFocus) scaleFactor else 1f)
            .scaleY(if (hasFocus) scaleFactor else 1f)
            .setDuration(150)
            .start()
    }

    /**
     * Apply focus elevation to a view.
     */
    fun applyFocusElevation(view: View, hasFocus: Boolean, elevationDp: Float = 8f) {
        val density = view.resources.displayMetrics.density
        ViewCompat.setElevation(view, if (hasFocus) elevationDp * density else 0f)
    }

    /**
     * Apply focus border to a view.
     */
    fun applyFocusBorder(view: View, hasFocus: Boolean, borderColor: Int = 0xFFFFFFFF.toInt()) {
        if (hasFocus) {
            view.background = android.graphics.drawable.GradientDrawable().apply {
                setStroke(3, borderColor)
                cornerRadius = 8f
            }
        } else {
            view.background = null
        }
    }

    /**
     * Apply combined focus effects.
     */
    fun applyFocusEffects(view: View, hasFocus: Boolean) {
        applyFocusScale(view, hasFocus)
        applyFocusElevation(view, hasFocus)
    }

    /**
     * Setup focus change listener with animations.
     */
    fun setupFocusAnimation(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            applyFocusEffects(v, hasFocus)
        }
    }
}
