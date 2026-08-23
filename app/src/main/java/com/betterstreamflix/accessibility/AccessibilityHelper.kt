package com.betterstreamflix.accessibility

import android.content.Context
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

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

    /**
     * Check if TalkBack is enabled.
     */
    fun isTalkBackEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
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
