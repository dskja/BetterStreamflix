package com.betterstreamflix.compose.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.betterstreamflix.utils.ThemeManager
import com.betterstreamflix.utils.UserPreferences

val LocalBsThemeId = staticCompositionLocalOf { ThemeManager.DEFAULT }

private fun cinemaScheme(
    primary: Color,
    onPrimary: Color,
    secondary: Color,
    background: Color,
    surface: Color,
    surfaceVariant: Color,
    onBackground: Color,
    onSurfaceVariant: Color,
) = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primary.copy(alpha = 0.22f),
    onPrimaryContainer = onBackground,
    secondary = secondary,
    onSecondary = onPrimary,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onBackground,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = onBackground.copy(alpha = 0.18f),
    error = BsColors.Danger,
    onError = Color(0xFFFFFFFF),
)

private fun schemeFor(themeId: String) = when (themeId) {
    ThemeManager.NERO_AMOLED_OLED -> cinemaScheme(
        primary = Color(0xFFFFFFFF),
        onPrimary = Color(0xFF000000),
        secondary = Color(0xFFBDBDBD),
        background = Color(0xFF000000),
        surface = Color(0xFF050505),
        surfaceVariant = Color(0xFF121212),
        onBackground = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFFBDBDBD),
    )
    ThemeManager.SUNSET_CINEMA -> cinemaScheme(
        primary = Color(0xFFF39A5B),
        onPrimary = Color(0xFF2B1812),
        secondary = Color(0xFFD7B7A4),
        background = Color(0xFF2B1812),
        surface = Color(0xFF352019),
        surfaceVariant = Color(0xFF3F281F),
        onBackground = Color(0xFFFFF0E5),
        onSurfaceVariant = Color(0xFFD7B7A4),
    )
    ThemeManager.STEEL_BLUE -> cinemaScheme(
        primary = Color(0xFF7DB3E8),
        onPrimary = Color(0xFF1A2430),
        secondary = Color(0xFFADC5DA),
        background = Color(0xFF1A2430),
        surface = Color(0xFF222E3C),
        surfaceVariant = Color(0xFF2B3949),
        onBackground = Color(0xFFEAF4FF),
        onSurfaceVariant = Color(0xFFADC5DA),
    )
    ThemeManager.FOREST_NIGHT -> cinemaScheme(
        primary = Color(0xFF65D7C2),
        onPrimary = Color(0xFF13221E),
        secondary = Color(0xFFA8D1C7),
        background = Color(0xFF13221E),
        surface = Color(0xFF1A2C27),
        surfaceVariant = Color(0xFF223832),
        onBackground = Color(0xFFE6FFF8),
        onSurfaceVariant = Color(0xFFA8D1C7),
    )
    ThemeManager.CRIMSON_NOIR -> cinemaScheme(
        primary = Color(0xFFD86A7A),
        onPrimary = Color(0xFF241015),
        secondary = Color(0xFFD6B2BA),
        background = Color(0xFF241015),
        surface = Color(0xFF2E171C),
        surfaceVariant = Color(0xFF3A1E24),
        onBackground = Color(0xFFFFECEF),
        onSurfaceVariant = Color(0xFFD6B2BA),
    )
    ThemeManager.MIDNIGHT_VIOLET -> cinemaScheme(
        primary = Color(0xFFAFA3FF),
        onPrimary = Color(0xFF181726),
        secondary = Color(0xFFBFB9DD),
        background = Color(0xFF181726),
        surface = Color(0xFF211F33),
        surfaceVariant = Color(0xFF2B2840),
        onBackground = Color(0xFFF1EEFF),
        onSurfaceVariant = Color(0xFFBFB9DD),
    )
    ThemeManager.NORD_FROST -> cinemaScheme(
        primary = Color(0xFF8ED0E8),
        onPrimary = Color(0xFF18212A),
        secondary = Color(0xFFB1C9D6),
        background = Color(0xFF18212A),
        surface = Color(0xFF212C38),
        surfaceVariant = Color(0xFF2A3846),
        onBackground = Color(0xFFEAF7FD),
        onSurfaceVariant = Color(0xFFB1C9D6),
    )
    ThemeManager.EMERALD_LUXE -> cinemaScheme(
        primary = Color(0xFF7ED6A3),
        onPrimary = Color(0xFF13211B),
        secondary = Color(0xFFB6D0C0),
        background = Color(0xFF13211B),
        surface = Color(0xFF1A2B23),
        surfaceVariant = Color(0xFF22362C),
        onBackground = Color(0xFFEDF9F2),
        onSurfaceVariant = Color(0xFFB6D0C0),
    )
    ThemeManager.RETRO_NEON -> cinemaScheme(
        primary = Color(0xFF4DE0D7),
        onPrimary = Color(0xFF18181E),
        secondary = Color(0xFFCFB7DA),
        background = Color(0xFF18181E),
        surface = Color(0xFF212129),
        surfaceVariant = Color(0xFF2C2C36),
        onBackground = Color(0xFFF4F7FF),
        onSurfaceVariant = Color(0xFFCFB7DA),
    )
    else -> cinemaScheme(
        primary = BsColors.Amber,
        onPrimary = BsColors.Ink,
        secondary = BsColors.SeaGlass,
        background = BsColors.Ink,
        surface = BsColors.InkElevated,
        surfaceVariant = BsColors.InkPanel,
        onBackground = BsColors.Mist,
        onSurfaceVariant = BsColors.MistDim,
    )
}

object BsMotion {
    val SoftEnter = tween<Float>(durationMillis = 480)
    val SoftEnterSlow = tween<Float>(durationMillis = 680)
    val FocusSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val PressSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val HeroFade = tween<Float>(durationMillis = 720)
    val HeroRise = tween<Float>(durationMillis = 820)
    val BrandPulse = tween<Float>(durationMillis = 1800)
    val TabSelect = tween<Float>(durationMillis = 280)
    val ContentCrossfade = tween<Float>(durationMillis = 360)
}

@Composable
fun BetterStreamflixTheme(
    themeId: String = if (UserPreferences.isReady()) UserPreferences.selectedTheme else ThemeManager.DEFAULT,
    content: @Composable () -> Unit,
) {
    val scheme = remember(themeId) { schemeFor(themeId) }
    CompositionLocalProvider(LocalBsThemeId provides themeId) {
        MaterialTheme(
            colorScheme = scheme,
            typography = BsTypography,
            content = content,
        )
    }
}
