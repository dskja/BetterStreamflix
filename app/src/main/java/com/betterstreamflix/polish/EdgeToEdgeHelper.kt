package com.betterstreamflix.polish

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Edge-to-edge helper — handles edge-to-edge display and inset
 * management for modern Android UIs.
 */
object EdgeToEdgeHelper {

    /**
     * Apply window insets to a view as padding.
     */
    fun applyWindowInsets(
        view: View,
        applyTop: Boolean = true,
        applyBottom: Boolean = true,
        applyLeft: Boolean = true,
        applyRight: Boolean = true,
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                if (applyLeft) v.paddingLeft + systemBars.left else v.paddingLeft,
                if (applyTop) v.paddingTop + systemBars.top else v.paddingTop,
                if (applyRight) v.paddingRight + systemBars.right else v.paddingRight,
                if (applyBottom) v.paddingBottom + systemBars.bottom else v.paddingBottom,
            )
            insets
        }
    }

    /**
     * Apply status bar inset as top padding.
     */
    fun applyStatusBarInset(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, v.paddingTop + systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }
    }

    /**
     * Apply navigation bar inset as bottom padding.
     */
    fun applyNavigationBarInset(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom + systemBars.bottom)
            insets
        }
    }
}
