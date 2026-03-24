package com.streamflixreborn.streamflix.utils

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.streamflixreborn.streamflix.R

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

    fun palette(theme: String): Palette = when (theme) {
        NERO_AMOLED_OLED -> Palette(
            mobileNavBackground = color("#000000"),
            mobileNavActive = color("#FFFFFF"),
            mobileNavInactive = color("#7A7A7A"),
            systemBar = color("#000000"),
            tvNavBackground = color("#050505"),
            tvHeaderPrimary = color("#FFFFFF"),
            tvHeaderSecondary = color("#BDBDBD"),
        )
        SUNSET_CINEMA -> Palette(
            mobileNavBackground = color("#2B1812"),
            mobileNavActive = color("#F39A5B"),
            mobileNavInactive = color("#C49B86"),
            systemBar = color("#2B1812"),
            tvNavBackground = color("#2B1812"),
            tvHeaderPrimary = color("#FFF0E5"),
            tvHeaderSecondary = color("#D7B7A4"),
        )
        STEEL_BLUE -> Palette(
            mobileNavBackground = color("#1A2430"),
            mobileNavActive = color("#7DB3E8"),
            mobileNavInactive = color("#8CA2B8"),
            systemBar = color("#1A2430"),
            tvNavBackground = color("#1A2430"),
            tvHeaderPrimary = color("#EAF4FF"),
            tvHeaderSecondary = color("#ADC5DA"),
        )
        FOREST_NIGHT -> Palette(
            mobileNavBackground = color("#13221E"),
            mobileNavActive = color("#65D7C2"),
            mobileNavInactive = color("#8DB3AA"),
            systemBar = color("#13221E"),
            tvNavBackground = color("#13221E"),
            tvHeaderPrimary = color("#E6FFF8"),
            tvHeaderSecondary = color("#A8D1C7"),
        )
        CRIMSON_NOIR -> Palette(
            mobileNavBackground = color("#241015"),
            mobileNavActive = color("#D86A7A"),
            mobileNavInactive = color("#A88891"),
            systemBar = color("#241015"),
            tvNavBackground = color("#241015"),
            tvHeaderPrimary = color("#FFECEF"),
            tvHeaderSecondary = color("#D6B2BA"),
        )
        MIDNIGHT_VIOLET -> Palette(
            mobileNavBackground = color("#181726"),
            mobileNavActive = color("#AFA3FF"),
            mobileNavInactive = color("#8F8AAE"),
            systemBar = color("#181726"),
            tvNavBackground = color("#181726"),
            tvHeaderPrimary = color("#F1EEFF"),
            tvHeaderSecondary = color("#BFB9DD"),
        )
        NORD_FROST -> Palette(
            mobileNavBackground = color("#18212A"),
            mobileNavActive = color("#8ED0E8"),
            mobileNavInactive = color("#8EA2B0"),
            systemBar = color("#18212A"),
            tvNavBackground = color("#18212A"),
            tvHeaderPrimary = color("#EAF7FD"),
            tvHeaderSecondary = color("#B1C9D6"),
        )
        EMERALD_LUXE -> Palette(
            mobileNavBackground = color("#13211B"),
            mobileNavActive = color("#7ED6A3"),
            mobileNavInactive = color("#98AE9F"),
            systemBar = color("#13211B"),
            tvNavBackground = color("#13211B"),
            tvHeaderPrimary = color("#EDF9F2"),
            tvHeaderSecondary = color("#B6D0C0"),
        )
        RETRO_NEON -> Palette(
            mobileNavBackground = color("#18181E"),
            mobileNavActive = color("#4DE0D7"),
            mobileNavInactive = color("#A56FBD"),
            systemBar = color("#18181E"),
            tvNavBackground = color("#18181E"),
            tvHeaderPrimary = color("#F4F7FF"),
            tvHeaderSecondary = color("#CFB7DA"),
        )
        else -> Palette(
            mobileNavBackground = color("#1E2129"),
            mobileNavActive = color("#C6C6C6"),
            mobileNavInactive = color("#808080"),
            systemBar = color("#1E2129"),
            tvNavBackground = color("#181818"),
            tvHeaderPrimary = color("#FFFFFF"),
            tvHeaderSecondary = color("#B3FFFFFF"),
        )
    }

    @ColorInt
    private fun color(hex: String): Int = Color.parseColor(hex)
}
