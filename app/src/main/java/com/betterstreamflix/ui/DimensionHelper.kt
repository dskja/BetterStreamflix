package com.betterstreamflix.ui

import android.content.Context
import android.util.TypedValue

/**
 * Dimension helper — converts between dp, sp, and px.
 */
object DimensionHelper {

    fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
    }

    fun dpToPxInt(context: Context, dp: Float): Int = dpToPx(context, dp).toInt()

    fun spToPx(context: Context, sp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
    }

    fun spToPxInt(context: Context, sp: Float): Int = spToPx(context, sp).toInt()

    fun pxToDp(context: Context, px: Float): Float {
        return px / context.resources.displayMetrics.density
    }

    fun pxToSp(context: Context, px: Float): Float {
        return px / context.resources.displayMetrics.scaledDensity
    }

    fun getScreenWidthPx(context: Context): Int = context.resources.displayMetrics.widthPixels

    fun getScreenHeightPx(context: Context): Int = context.resources.displayMetrics.heightPixels

    fun getScreenWidthDp(context: Context): Float = pxToDp(context, getScreenWidthPx(context).toFloat())

    fun getScreenHeightDp(context: Context): Float = pxToDp(context, getScreenHeightPx(context).toFloat())

    fun getDensity(context: Context): Float = context.resources.displayMetrics.density
}
