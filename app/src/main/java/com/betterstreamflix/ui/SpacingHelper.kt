package com.betterstreamflix.ui

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Spacing helper — provides consistent spacing values and padding
 * management following Material Design spacing guidelines.
 */
object SpacingHelper {

    enum class Spacing(val dp: Float) {
        NONE(0f),
        EXTRA_SMALL(4f),
        SMALL(8f),
        MEDIUM(12f),
        LARGE(16f),
        EXTRA_LARGE(24f),
        DOUBLE_EXTRA_LARGE(32f),
        TRIPLE_EXTRA_LARGE(48f),
    }

    /**
     * Convert dp to pixels.
     */
    fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics,
        ).toInt()
    }

    /**
     * Convert sp to pixels.
     */
    fun spToPx(context: Context, sp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics,
        ).toInt()
    }

    /**
     * Apply uniform padding to a view.
     */
    fun applyPadding(view: View, context: Context, spacing: Spacing) {
        val px = dpToPx(context, spacing.dp)
        view.setPadding(px, px, px, px)
    }

    /**
     * Apply horizontal padding.
     */
    fun applyHorizontalPadding(view: View, context: Context, spacing: Spacing) {
        val px = dpToPx(context, spacing.dp)
        view.setPadding(px, view.paddingTop, px, view.paddingBottom)
    }

    /**
     * Apply vertical padding.
     */
    fun applyVerticalPadding(view: View, context: Context, spacing: Spacing) {
        val px = dpToPx(context, spacing.dp)
        view.setPadding(view.paddingLeft, px, view.paddingRight, px)
    }

    /**
     * Set margin on a view.
     */
    fun setMargin(view: View, context: Context, spacing: Spacing) {
        val px = dpToPx(context, spacing.dp)
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        params.setMargins(px, px, px, px)
        view.layoutParams = params
    }

    /**
     * Set horizontal margin.
     */
    fun setHorizontalMargin(view: View, context: Context, spacing: Spacing) {
        val px = dpToPx(context, spacing.dp)
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        params.leftMargin = px
        params.rightMargin = px
        view.layoutParams = params
    }

    /**
     * Get screen width in dp.
     */
    fun getScreenWidthDp(context: Context): Float {
        return context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density
    }

    /**
     * Get screen height in dp.
     */
    fun getScreenHeightDp(context: Context): Float {
        return context.resources.displayMetrics.heightPixels / context.resources.displayMetrics.density
    }
}
