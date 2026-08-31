package com.betterstreamflix.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.betterstreamflix.utils.ThemeManager
import com.betterstreamflix.utils.UserPreferences

val LocalBsThemeId = staticCompositionLocalOf { ThemeManager.DEFAULT }

private val DarkScheme = darkColorScheme(
    primary = BsPrimary,
    onPrimary = BsOnPrimary,
    background = BsBackground,
    onBackground = BsOnBackground,
    surface = BsSurface,
    onSurface = BsOnSurface,
    onSurfaceVariant = BsOnSurfaceVariant,
    error = BsError,
)

@Composable
fun BetterStreamflixTheme(
    themeId: String = if (UserPreferences.isReady()) UserPreferences.selectedTheme else ThemeManager.DEFAULT,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme() || themeId == ThemeManager.NERO_AMOLED_OLED
    CompositionLocalProvider(LocalBsThemeId provides themeId) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else DarkScheme,
            typography = BsTypography,
            content = content,
        )
    }
}
