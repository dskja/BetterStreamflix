package com.betterstreamflix.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Material color helper — provides Material Design 3 color utilities
 * for dynamic theming.
 */
object MaterialColorHelper {

    /**
     * Material color roles.
     */
    data class ColorScheme(
        val primary: Int,
        val onPrimary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val secondary: Int,
        val onSecondary: Int,
        val secondaryContainer: Int,
        val onSecondaryContainer: Int,
        val tertiary: Int,
        val onTertiary: Int,
        val background: Int,
        val onBackground: Int,
        val surface: Int,
        val onSurface: Int,
        val surfaceVariant: Int,
        val onSurfaceVariant: Int,
        val error: Int,
        val onError: Int,
        val outline: Int,
    )

    /**
     * Dark color scheme.
     */
    val darkScheme = ColorScheme(
        primary = Color.parseColor("#D0BCFF"),
        onPrimary = Color.parseColor("#381E72"),
        primaryContainer = Color.parseColor("#4F378B"),
        onPrimaryContainer = Color.parseColor("#EADDFF"),
        secondary = Color.parseColor("#CCC2DC"),
        onSecondary = Color.parseColor("#332D41"),
        secondaryContainer = Color.parseColor("#4A4458"),
        onSecondaryContainer = Color.parseColor("#E8DEF8"),
        tertiary = Color.parseColor("#EFB8C8"),
        onTertiary = Color.parseColor("#492532"),
        background = Color.parseColor("#1C1B1F"),
        onBackground = Color.parseColor("#E6E1E5"),
        surface = Color.parseColor("#1C1B1F"),
        onSurface = Color.parseColor("#E6E1E5"),
        surfaceVariant = Color.parseColor("#49454F"),
        onSurfaceVariant = Color.parseColor("#CAC4D0"),
        error = Color.parseColor("#F2B8B5"),
        onError = Color.parseColor("#601410"),
        outline = Color.parseColor("#938F99"),
    )

    /**
     * Light color scheme.
     */
    val lightScheme = ColorScheme(
        primary = Color.parseColor("#6750A4"),
        onPrimary = Color.parseColor("#FFFFFF"),
        primaryContainer = Color.parseColor("#EADDFF"),
        onPrimaryContainer = Color.parseColor("#21005D"),
        secondary = Color.parseColor("#625B71"),
        onSecondary = Color.parseColor("#FFFFFF"),
        secondaryContainer = Color.parseColor("#E8DEF8"),
        onSecondaryContainer = Color.parseColor("#1D192B"),
        tertiary = Color.parseColor("#7D5260"),
        onTertiary = Color.parseColor("#FFFFFF"),
        background = Color.parseColor("#FFFBFE"),
        onBackground = Color.parseColor("#1C1B1F"),
        surface = Color.parseColor("#FFFBFE"),
        onSurface = Color.parseColor("#1C1B1F"),
        surfaceVariant = Color.parseColor("#E7E0EC"),
        onSurfaceVariant = Color.parseColor("#49454F"),
        error = Color.parseColor("#B3261E"),
        onError = Color.parseColor("#FFFFFF"),
        outline = Color.parseColor("#79747E"),
    )

    /**
     * Get the current color scheme based on dark mode.
     */
    fun getScheme(isDark: Boolean): ColorScheme = if (isDark) darkScheme else lightScheme

    /**
     * Create a ripple background drawable.
     */
    fun createRippleBackground(color: Int, rippleColor: Int, radiusDp: Float = 8f): android.graphics.drawable.RippleDrawable {
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        val background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * density
        }
        return android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(rippleColor),
            background,
            null,
        )
    }

    /**
     * Calculate contrast ratio between two colors.
     */
    fun contrastRatio(color1: Int, color2: Int): Double {
        val luminance1 = calculateLuminance(color1)
        val luminance2 = calculateLuminance(color2)
        val lighter = maxOf(luminance1, luminance2)
        val darker = minOf(luminance1, luminance2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun calculateLuminance(color: Int): Double {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
