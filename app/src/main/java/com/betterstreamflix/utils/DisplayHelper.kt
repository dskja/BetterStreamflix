package com.betterstreamflix.utils

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.WindowMetrics

/**
 * Display helper — provides screen dimensions, DPI, and orientation info.
 */
object DisplayHelper {

    /**
     * Get screen width in pixels.
     */
    fun getScreenWidth(context: Context): Int {
        return context.resources.displayMetrics.widthPixels
    }

    /**
     * Get screen height in pixels.
     */
    fun getScreenHeight(context: Context): Int {
        return context.resources.displayMetrics.heightPixels
    }

    /**
     * Get screen density (DPI).
     */
    fun getScreenDpi(context: Context): Int {
        return context.resources.displayMetrics.densityDpi
    }

    /**
     * Get screen density multiplier.
     */
    fun getScreenDensity(context: Context): Float {
        return context.resources.displayMetrics.density
    }

    /**
     * Convert DP to pixels.
     */
    fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    /**
     * Convert pixels to DP.
     */
    fun pxToDp(context: Context, px: Float): Float {
        return px / context.resources.displayMetrics.density
    }

    /**
     * Check if the device is in landscape orientation.
     */
    fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }

    /**
     * Check if the device has a large screen (sw >= 600dp).
     */
    fun isLargeScreen(context: Context): Boolean {
        return context.resources.configuration.smallestScreenWidthDp >= 600
    }

    /**
     * Get the screen aspect ratio (width / height).
     */
    fun getAspectRatio(context: Context): Float {
        val w = getScreenWidth(context).toFloat()
        val h = getScreenHeight(context).toFloat()
        return if (h > w) h / w else w / h
    }

    /**
     * Check if the device is a TV (Leanback).
     */
    fun isTv(context: Context): Boolean {
        return context.packageManager.hasSystemFeature("android.software.leanback")
    }
}
