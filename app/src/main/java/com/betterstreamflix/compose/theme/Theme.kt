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

private fun schemeFor(themeId: String) = when (themeId) {
    ThemeManager.NERO_AMOLED_OLED -> darkColorScheme(
        primary = Color(0xFFFFFFFF),
        onPrimary = Color(0xFF000000),
        secondary = Color(0xFFBDBDBD),
        onSecondary = Color(0xFF000000),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF050505),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF121212),
        onSurfaceVariant = Color(0xFFBDBDBD),
        outline = Color(0x33FFFFFF),
        error = BsColors.Danger,
        onError = Color(0xFFFFFFFF),
    )
    ThemeManager.SUNSET_CINEMA -> darkColorScheme(
        primary = Color(0xFFF39A5B),
        onPrimary = Color(0xFF2B1812),
        secondary = Color(0xFFD7B7A4),
        onSecondary = Color(0xFF2B1812),
        background = Color(0xFF2B1812),
        onBackground = Color(0xFFFFF0E5),
        surface = Color(0xFF352019),
        onSurface = Color(0xFFFFF0E5),
        surfaceVariant = Color(0xFF3F281F),
        onSurfaceVariant = Color(0xFFD7B7A4),
        outline = Color(0x33FFF0E5),
        error = BsColors.Danger,
        onError = Color(0xFFFFF0E5),
    )
    ThemeManager.STEEL_BLUE -> darkColorScheme(
        primary = Color(0xFF7DB3E8),
        onPrimary = Color(0xFF1A2430),
        secondary = Color(0xFFADC5DA),
        onSecondary = Color(0xFF1A2430),
        background = Color(0xFF1A2430),
        onBackground = Color(0xFFEAF4FF),
        surface = Color(0xFF222E3C),
        onSurface = Color(0xFFEAF4FF),
        surfaceVariant = Color(0xFF2B3949),
        onSurfaceVariant = Color(0xFFADC5DA),
        outline = Color(0x33EAF4FF),
        error = BsColors.Danger,
        onError = Color(0xFFEAF4FF),
    )
    ThemeManager.FOREST_NIGHT -> darkColorScheme(
        primary = Color(0xFF65D7C2),
        onPrimary = Color(0xFF13221E),
        secondary = Color(0xFFA8D1C7),
        onSecondary = Color(0xFF13221E),
        background = Color(0xFF13221E),
        onBackground = Color(0xFFE6FFF8),
        surface = Color(0xFF1A2C27),
        onSurface = Color(0xFFE6FFF8),
        surfaceVariant = Color(0xFF223832),
        onSurfaceVariant = Color(0xFFA8D1C7),
        outline = Color(0x33E6FFF8),
        error = BsColors.Danger,
        onError = Color(0xFFE6FFF8),
    )
    ThemeManager.CRIMSON_NOIR -> darkColorScheme(
        primary = Color(0xFFD86A7A),
        onPrimary = Color(0xFF241015),
        secondary = Color(0xFFD6B2BA),
        onSecondary = Color(0xFF241015),
        background = Color(0xFF241015),
        onBackground = Color(0xFFFFECEF),
        surface = Color(0xFF2E171C),
        onSurface = Color(0xFFFFECEF),
        surfaceVariant = Color(0xFF3A1E24),
        onSurfaceVariant = Color(0xFFD6B2BA),
        outline = Color(0x33FFECEF),
        error = BsColors.Danger,
        onError = Color(0xFFFFECEF),
    )
    else -> darkColorScheme(
        primary = BsColors.Amber,
        onPrimary = BsColors.Ink,
        primaryContainer = BsColors.AmberMuted,
        onPrimaryContainer = BsColors.Mist,
        secondary = BsColors.SeaGlass,
        onSecondary = BsColors.Mist,
        background = BsColors.Ink,
        onBackground = BsColors.Mist,
        surface = BsColors.InkElevated,
        onSurface = BsColors.Mist,
        surfaceVariant = BsColors.InkPanel,
        onSurfaceVariant = BsColors.MistDim,
        outline = BsColors.Hairline,
        error = BsColors.Danger,
        onError = BsColors.Mist,
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
