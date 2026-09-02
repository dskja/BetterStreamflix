package com.betterstreamflix.compose.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.betterstreamflix.utils.ThemeManager
import com.betterstreamflix.utils.UserPreferences

val LocalBsThemeId = staticCompositionLocalOf { ThemeManager.DEFAULT }

private val ObsidianScheme = darkColorScheme(
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

object BsMotion {
    val SoftEnter = tween<Float>(durationMillis = 520)
    val FocusSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val HeroFade = tween<Float>(durationMillis = 700)
}

@Composable
fun BetterStreamflixTheme(
    themeId: String = if (UserPreferences.isReady()) UserPreferences.selectedTheme else ThemeManager.DEFAULT,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBsThemeId provides themeId) {
        MaterialTheme(
            colorScheme = ObsidianScheme,
            typography = BsTypography,
            content = content,
        )
    }
}
