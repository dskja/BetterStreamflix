package com.dskja.betterstreamflix.utils

import android.app.Activity
import android.content.Context
import com.dskja.betterstreamflix.BuildConfig
import com.dskja.betterstreamflix.R
import com.google.android.material.color.DynamicColors

/**
 * Lumina — off-by-default cinematic mobile shell.
 * Experimental layouts keep the same view IDs so existing ViewBindings can `.bind(view)`.
 */
object ExperimentalMobileDesign {

    enum class Accent(val key: String) {
        CRIMSON("crimson"),
        EMBER("ember"),
        AURORA("aurora"),
        SLATE("slate");

        companion object {
            fun fromKey(raw: String?): Accent =
                entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: CRIMSON
        }
    }

    fun isAvailable(): Boolean = BuildConfig.DEBUG

    fun enabled(): Boolean = isAvailable() && UserPreferences.experimentalNewAppDesign

    /** Call once at app start to clear stale Lumina prefs in release builds. */
    fun enforceAvailabilityGate() {
        if (!isAvailable() && UserPreferences.experimentalNewAppDesign) {
            UserPreferences.experimentalNewAppDesign = false
        }
    }
    fun layout(defaultRes: Int, experimentalRes: Int): Int =
        if (enabled()) experimentalRes else defaultRes

    fun accent(): Accent = Accent.fromKey(UserPreferences.experimentalLuminaAccent)

    fun pureBlack(): Boolean = enabled() && UserPreferences.experimentalLuminaPureBlack

    fun dynamicColors(): Boolean = enabled() && UserPreferences.experimentalLuminaDynamicColors

    fun navAutoHide(): Boolean = enabled() && UserPreferences.experimentalLuminaNavAutoHide

    fun heroParallax(): Boolean = enabled() && UserPreferences.experimentalLuminaHeroParallax

    fun reducedGlass(): Boolean = enabled() && UserPreferences.experimentalLuminaReducedGlass

    /** Theme resource for [Activity.setTheme] when Lumina is on. */
    fun themeRes(): Int {
        val black = pureBlack()
        return when (accent()) {
            Accent.CRIMSON -> if (black) R.style.AppTheme_Mobile_Experimental_PureBlack
            else R.style.AppTheme_Mobile_Experimental
            Accent.EMBER -> if (black) R.style.AppTheme_Mobile_Experimental_Ember_PureBlack
            else R.style.AppTheme_Mobile_Experimental_Ember
            Accent.AURORA -> if (black) R.style.AppTheme_Mobile_Experimental_Aurora_PureBlack
            else R.style.AppTheme_Mobile_Experimental_Aurora
            Accent.SLATE -> if (black) R.style.AppTheme_Mobile_Experimental_Slate_PureBlack
            else R.style.AppTheme_Mobile_Experimental_Slate
        }
    }

    /**
     * Apply wallpaper-driven Material You tint when the user opted in.
     * Call after [Activity.setTheme] / [Activity.setContentView].
     */
    fun applyDynamicColors(activity: Activity) {
        if (!dynamicColors()) return
        runCatching { DynamicColors.applyToActivityIfAvailable(activity) }
    }

    fun summary(context: Context): String {
        if (!isAvailable()) {
            return context.getString(R.string.settings_experimental_broken_summary)
        }
        if (!enabled()) {
            return context.getString(R.string.settings_experimental_new_design_summary)
        }
        val accentLabel = when (accent()) {
            Accent.CRIMSON -> context.getString(R.string.exp_accent_crimson)
            Accent.EMBER -> context.getString(R.string.exp_accent_ember)
            Accent.AURORA -> context.getString(R.string.exp_accent_aurora)
            Accent.SLATE -> context.getString(R.string.exp_accent_slate)
        }
        val extras = buildList {
            if (pureBlack()) add(context.getString(R.string.exp_opt_pure_black_short))
            if (dynamicColors()) add(context.getString(R.string.exp_opt_dynamic_short))
        }.joinToString(" · ")
        return if (extras.isBlank()) {
            context.getString(R.string.settings_experimental_lumina_active_summary, accentLabel)
        } else {
            context.getString(R.string.settings_experimental_lumina_active_summary_extra, accentLabel, extras)
        }
    }
}
