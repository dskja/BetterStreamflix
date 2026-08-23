package com.betterstreamflix.polish

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View

/**
 * Theme helper — manages dynamic theming and color customization.
 */
object ThemeHelper {

    /**
     * Available theme modes.
     */
    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    /**
     * Get the current theme mode.
     */
    fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        return when (prefs.getString("theme_mode", "system")) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    /**
     * Set the theme mode.
     */
    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE).edit()
            .putString("theme_mode", when (mode) {
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
                ThemeMode.SYSTEM -> "system"
            }).apply()
    }

    /**
     * Check if dark mode is active.
     */
    fun isDarkMode(context: Context): Boolean {
        return when (getThemeMode(context)) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> {
                val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    /**
     * Get accent color.
     */
    fun getAccentColor(context: Context): Int {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val colorHex = prefs.getString("accent_color", "#6750A4")
        return try {
            Color.parseColor(colorHex)
        } catch (e: Exception) {
            Color.parseColor("#6750A4")
        }
    }

    /**
     * Set accent color.
     */
    fun setAccentColor(context: Context, colorHex: String) {
        context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE).edit()
            .putString("accent_color", colorHex).apply()
    }

    /**
     * Create a rounded background drawable.
     */
    fun createRoundedBackground(color: Int, radiusDp: Float = 8f): Drawable {
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * density
        }
    }

    /**
     * Apply a dim overlay to a view.
     */
    fun applyDimOverlay(view: View, dimAmount: Float = 0.5f) {
        view.alpha = 1f - dimAmount
    }

}
