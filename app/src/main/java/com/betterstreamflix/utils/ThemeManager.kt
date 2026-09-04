package com.betterstreamflix.utils

import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.compose.ui.graphics.toArgb
import com.betterstreamflix.R
import com.betterstreamflix.compose.theme.BsThemeCatalog

object ThemeManager {
    const val DEFAULT = "default"
    const val NERO_AMOLED_OLED = "nero_amoled_oled"
    const val SUNSET_CINEMA = "sunset_cinema"
    const val STEEL_BLUE = "steel_blue"
    const val FOREST_NIGHT = "forest_night"
    const val CRIMSON_NOIR = "crimson_noir"
    const val MIDNIGHT_VIOLET = "midnight_violet"
    const val NORD_FROST = "nord_frost"
    const val EMERALD_LUXE = "emerald_luxe"
    const val RETRO_NEON = "retro_neon"

    data class Palette(
        @ColorInt val mobileNavBackground: Int,
        @ColorInt val mobileNavActive: Int,
        @ColorInt val mobileNavInactive: Int,
        @ColorInt val systemBar: Int,
        @ColorInt val tvNavBackground: Int,
        @ColorInt val tvHeaderPrimary: Int,
        @ColorInt val tvHeaderSecondary: Int,
    )

    @StyleRes
    fun mobileThemeRes(theme: String): Int = when (theme) {
        NERO_AMOLED_OLED -> R.style.AppTheme_Mobile_NeroAmoledOled
        SUNSET_CINEMA -> R.style.AppTheme_Mobile_SunsetCinema
        STEEL_BLUE -> R.style.AppTheme_Mobile_SteelBlue
        FOREST_NIGHT -> R.style.AppTheme_Mobile_ForestNight
        CRIMSON_NOIR -> R.style.AppTheme_Mobile_CrimsonNoir
        MIDNIGHT_VIOLET -> R.style.AppTheme_Mobile_MidnightViolet
        NORD_FROST -> R.style.AppTheme_Mobile_NordFrost
        EMERALD_LUXE -> R.style.AppTheme_Mobile_EmeraldLuxe
        RETRO_NEON -> R.style.AppTheme_Mobile_RetroNeon
        else -> R.style.AppTheme_Mobile
    }

    @StyleRes
    fun tvThemeRes(theme: String): Int = when (theme) {
        NERO_AMOLED_OLED -> R.style.AppTheme_NeroAmoledOled
        SUNSET_CINEMA -> R.style.AppTheme_SunsetCinema
        STEEL_BLUE -> R.style.AppTheme_SteelBlue
        FOREST_NIGHT -> R.style.AppTheme_ForestNight
        CRIMSON_NOIR -> R.style.AppTheme_CrimsonNoir
        MIDNIGHT_VIOLET -> R.style.AppTheme_MidnightViolet
        NORD_FROST -> R.style.AppTheme_NordFrost
        EMERALD_LUXE -> R.style.AppTheme_EmeraldLuxe
        RETRO_NEON -> R.style.AppTheme_RetroNeon
        else -> R.style.AppTheme_Tv
    }

    @StringRes
    fun titleRes(theme: String): Int = when (theme) {
        NERO_AMOLED_OLED -> R.string.theme_nero_amoled_oled
        SUNSET_CINEMA -> R.string.theme_sunset_cinema
        STEEL_BLUE -> R.string.theme_steel_blue
        FOREST_NIGHT -> R.string.theme_forest_night
        CRIMSON_NOIR -> R.string.theme_crimson_noir
        MIDNIGHT_VIOLET -> R.string.theme_midnight_violet
        NORD_FROST -> R.string.theme_nord_frost
        EMERALD_LUXE -> R.string.theme_emerald_luxe
        RETRO_NEON -> R.string.theme_retro_neon
        else -> R.string.theme_default
    }

    fun palette(theme: String): Palette {
        val tokens = BsThemeCatalog.tokensFor(theme)
        return Palette(
            mobileNavBackground = tokens.Ink.toArgb(),
            mobileNavActive = tokens.Amber.toArgb(),
            mobileNavInactive = tokens.MistFaint.toArgb(),
            systemBar = tokens.Ink.toArgb(),
            tvNavBackground = tokens.Ink.toArgb(),
            tvHeaderPrimary = tokens.Mist.toArgb(),
            tvHeaderSecondary = tokens.MistDim.toArgb(),
        )
    }
}
