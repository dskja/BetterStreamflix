package com.betterstreamflix.accessibility

import android.content.Context
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.betterstreamflix.R

/**
 * Accessibility helper — provides content descriptions and TalkBack support.
 */
object AccessibilityHelper {

    /**
     * Set a content description on a view.
     */
    fun setContentDescription(view: View, description: String) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.contentDescription = description
            }
        })
        view.contentDescription = description
    }

    /**
     * Set a button role with description for TalkBack.
     */
    fun setAsButton(view: View, description: String) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.contentDescription = description
                info.className = android.widget.Button::class.java.name
            }
        })
    }

    /**
     * Set a heading role for TalkBack navigation.
     */
    fun setAsHeading(view: View) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.isHeading = true
            }
        })
    }

    /**
     * Announce text via TalkBack.
     */
    fun announce(view: View, text: String) {
        view.announceForAccessibility(text)
    }

    fun isReducedMotionEnabled(context: Context): Boolean {
        return ReducedMotionHelper.isReducedMotion(context)
    }

    fun getFontScale(context: Context): Float {
        return FontScaleHelper.getSystemFontScale(context)
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openDisplaySettings(context: Context) {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun announceReducedMotionState(view: View, context: Context) {
        val enabled = isReducedMotionEnabled(context)
        announce(
            view,
            if (enabled) {
                context.getString(R.string.settings_reduced_motion_on)
            } else {
                context.getString(R.string.settings_reduced_motion_off)
            },
        )
    }

    fun announceFontScale(view: View, context: Context) {
        val scale = getFontScale(context)
        announce(view, context.getString(R.string.settings_font_scale_summary, scale))
    }

    /**
     * Check if TalkBack is enabled.
     */
    fun isTalkBackEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            ?: return false
        return am.isEnabled && am.isTouchExplorationEnabled
    }

    /**
     * Set live region for dynamic content updates.
     */
    fun setLiveRegion(view: View, polite: Boolean = true) {
        ViewCompat.setAccessibilityLiveRegion(view,
            if (polite) ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE
            else ViewCompat.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
        )
    }
}
