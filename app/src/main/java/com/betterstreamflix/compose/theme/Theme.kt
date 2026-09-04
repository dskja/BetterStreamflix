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

private fun cinemaScheme(tokens: BsColorScheme) = darkColorScheme(
    primary = tokens.Amber,
    onPrimary = tokens.Ink,
    primaryContainer = tokens.Amber.copy(alpha = 0.22f),
    onPrimaryContainer = tokens.Mist,
    secondary = tokens.SeaGlass,
    onSecondary = tokens.Ink,
    background = tokens.Ink,
    onBackground = tokens.Mist,
    surface = tokens.InkElevated,
    onSurface = tokens.Mist,
    surfaceVariant = tokens.InkPanel,
    onSurfaceVariant = tokens.MistDim,
    outline = tokens.Hairline,
    error = tokens.Danger,
    onError = Color(0xFFFFFFFF),
)

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
    val PlayerFade = tween<Float>(durationMillis = 220)

    /** Instant tween used when reduced motion is preferred. */
    val Reduced = tween<Float>(durationMillis = 0)

    @Composable
    fun rememberMotionEnabled(): Boolean {
        val context = androidx.compose.ui.platform.LocalContext.current
        return !com.betterstreamflix.accessibility.ReducedMotionHelper.isReducedMotion(context)
    }

    @Composable
    fun focusSpec(): androidx.compose.animation.core.FiniteAnimationSpec<Float> =
        if (rememberMotionEnabled()) FocusSpring else Reduced

    @Composable
    fun pressSpec(): androidx.compose.animation.core.FiniteAnimationSpec<Float> =
        if (rememberMotionEnabled()) PressSpring else Reduced
}

@Composable
fun BetterStreamflixTheme(
    themeId: String = if (UserPreferences.isReady()) UserPreferences.selectedTheme else ThemeManager.DEFAULT,
    content: @Composable () -> Unit,
) {
    val tokens = remember(themeId) { BsThemeCatalog.tokensFor(themeId) }
    val scheme = remember(tokens) { cinemaScheme(tokens) }
    CompositionLocalProvider(
        LocalBsThemeId provides themeId,
        LocalBsColors provides tokens,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = BsTypography,
            content = content,
        )
    }
}
