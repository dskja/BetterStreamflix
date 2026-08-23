package com.betterstreamflix.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Theme helper — provides dynamic theming utilities.
 */
object ThemeHelper {

    /**
     * Get a color from theme attributes.
     */
    fun getThemeColor(context: Context, attrId: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.data
    }

    /**
     * Create a rounded background drawable.
     */
    fun createRoundedBackground(color: Int, cornerRadius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = cornerRadius
        }
    }

    /**
     * Create a circular background drawable.
     */
    fun createCircularBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    /**
     * Apply a translucent overlay to a view.
     */
    fun applyOverlay(view: View, alpha: Float) {
        view.background = ColorDrawable(Color.argb((alpha * 255).toInt(), 0, 0, 0))
    }

    /**
     * Set text color from theme.
     */
    fun setThemeTextColor(textView: TextView, attrId: Int) {
        textView.setTextColor(getThemeColor(textView.context, attrId))
    }

    /**
     * Get the primary color from the current theme.
     */
    fun getPrimaryColor(context: Context): Int {
        return getThemeColor(context, android.R.attr.colorPrimary)
    }

    /**
     * Get the accent/secondary color from the current theme.
     */
    fun getAccentColor(context: Context): Int {
        return getThemeColor(context, android.R.attr.colorAccent)
    }

    /**
     * Get the background color from the current theme.
     */
    fun getBackgroundColor(context: Context): Int {
        return getThemeColor(context, android.R.attr.colorBackground)
    }
}
