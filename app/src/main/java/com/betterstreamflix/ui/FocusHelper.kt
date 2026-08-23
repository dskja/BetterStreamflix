package com.betterstreamflix.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import com.google.android.material.card.MaterialCardView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat

/**
 * Focus helper — manages visual focus indicators for TV and keyboard navigation.
 */
object FocusHelper {

    /**
     * Apply focus styling to a card view.
     */
    fun applyCardFocusStyle(card: MaterialCardView, focused: Boolean) {
        if (focused) {
            card.cardElevation = dpToPx(card.context, 8).toFloat()
            card.radius = dpToPx(card.context, 12).toFloat()
        } else {
            card.cardElevation = dpToPx(card.context, 2).toFloat()
            card.radius = dpToPx(card.context, 8).toFloat()
        }
    }

    /**
     * Apply focus tint to an ImageView.
     */
    fun applyImageFocusTint(image: ImageView, focused: Boolean, tintColor: Int) {
        if (focused) {
            ImageViewCompat.setImageTintList(image, ColorStateList.valueOf(tintColor))
        } else {
            ImageViewCompat.setImageTintList(image, null)
        }
    }

    /**
     * Set up a view for focus with a focus change listener.
     */
    fun setupFocus(view: View, onFocusGain: (() -> Unit)? = null, onFocusLost: (() -> Unit)? = null) {
        view.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) onFocusGain?.invoke() else onFocusLost?.invoke()
        }
        view.isFocusable = true
        view.isFocusableInTouchMode = true
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
