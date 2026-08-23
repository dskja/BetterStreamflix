package com.betterstreamflix.polish

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit

/**
 * Accessibility helper — provides accessibility utilities for better
 * screen reader and assistive technology support.
 */
object AccessibilityHelper {

    /**
     * Check if talkback is enabled.
     */
    fun isTalkBackEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        return am.isEnabled && am.isTouchExplorationEnabled
    }

    /**
     * Set a content description on a view.
     */
    fun setContentDescription(view: android.view.View, description: String) {
        view.contentDescription = description
    }

    /**
     * Set an accessible click target with minimum touch target size (48dp).
     */
    fun ensureMinTouchTargetSize(view: android.view.View) {
        val density = view.resources.displayMetrics.density
        val minSize = (48 * density).toInt()
        view.minimumWidth = minSize
        view.minimumHeight = minSize
    }

    /**
     * Announce a message via accessibility.
     */
    fun announceForAccessibility(view: android.view.View, text: String) {
        view.announceForAccessibility(text)
    }

    /**
     * Check if reduced motion is preferred.
     */
    fun isReducedMotionPreferred(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        // Android 12+ has ANIMATOR_DURATION_SCALE
        return android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    /**
     * Get the font scale from configuration.
     */
    fun getFontScale(context: Context): Float {
        return context.resources.configuration.fontScale
    }

    /**
     * Check if large text is enabled.
     */
    fun isLargeTextEnabled(context: Context): Boolean {
        return getFontScale(context) > 1.2f
    }
}
