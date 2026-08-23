package com.betterstreamflix.accessibility

import android.content.Context
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat

/**
 * Reduced motion helper — detects if the user has enabled reduced motion
 * and provides guidance for animations.
 */
object ReducedMotionHelper {

    /**
     * Check if reduced motion is preferred.
     */
    fun isReducedMotion(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // On Android 12+, check if "Remove animations" is enabled
            return try {
                android.provider.Settings.Global.getFloat(
                    context.contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f,
                ) == 0f
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    /**
     * Get appropriate animation duration based on user preference.
     */
    fun getAnimationDuration(context: Context, defaultMs: Long = 300): Long {
        return if (isReducedMotion(context)) 0 else defaultMs
    }

    /**
     * Apply reduced motion to a view's accessibility.
     */
    fun applyReducedMotion(context: Context, view: View) {
        if (isReducedMotion(context)) {
            ViewCompat.setAccessibilityLiveRegion(view, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        }
    }
}
