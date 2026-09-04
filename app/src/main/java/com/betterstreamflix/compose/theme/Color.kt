package com.betterstreamflix.compose.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Liquid Glass Cinema — premium streaming surfaces.
 * Deep ink canvas, translucent glass panels, champagne amber accent.
 * Specular hairlines + soft luminescence instead of heavy blur (Android-friendly).
 */
object BsColors {
    val Ink = Color(0xFF05070A)
    val InkElevated = Color(0xFF0A1018)
    val InkPanel = Color(0xFF121A24)
    val InkSoft = Color(0xFF1A2430)
    val InkDeep = Color(0xFF030508)

    /** Translucent glass fills (approximate liquid glass without RenderEffect blur). */
    val Glass = Color(0xB30E1520)
    val GlassStrong = Color(0xCC121A26)
    val GlassSoft = Color(0x80101822)
    val GlassLift = Color(0xD9182230)
    val GlassFrost = Color(0x33F1F4F8)
    /** Alias kept for existing call sites. */
    val InkGlass = Glass

    val Amber = Color(0xFFE9B04A)
    val AmberBright = Color(0xFFF6D07A)
    val AmberMuted = Color(0xFFA6792C)
    val AmberDeep = Color(0xFF6B4A14)
    val AmberVeil = Color(0x28E9B04A)
    val AmberGlass = Color(0x33E9B04A)

    val Mist = Color(0xFFF1F4F8)
    val MistDim = Color(0xFFB0BAC8)
    val MistFaint = Color(0xFF6F7B8E)

    val SeaGlass = Color(0xFF3F9694)
    val SeaGlassSoft = Color(0xFF2A6462)
    val Danger = Color(0xFFE35F5F)
    val Success = Color(0xFF4DB787)
    val Warning = Color(0xFFE0A045)

    val ScrimTop = Color(0xCC05070A)
    val ScrimBottom = Color(0xF505070A)
    val Hairline = Color(0x2EF1F4F8)
    val HairlineStrong = Color(0x48F1F4F8)
    val Specular = Color(0x55FFFFFF)
    val SpecularSoft = Color(0x22FFFFFF)
    val FocusRing = Color(0x88E9B04A)

    val Atmosphere = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0C141E),
            Color(0xFF070B11),
            Color(0xFF090E16),
            Color(0xFF04060A),
        ),
    )

    val AtmosphereSheen = Brush.linearGradient(
        colors = listOf(
            Color(0x0005070A),
            Color(0x1AE9B04A),
            Color(0x123F9694),
            Color(0x0005070A),
        ),
    )

    val GlassPanel = Brush.verticalGradient(
        colors = listOf(
            Color(0xCC1A2433),
            Color(0xB3121A26),
            Color(0xA80E1520),
        ),
    )

    val GlassPanelSelected = Brush.verticalGradient(
        colors = listOf(
            Color(0xDD243044),
            Color(0xC8182230),
            Color(0xB8121A26),
        ),
    )

    val GlassTopBar = Brush.verticalGradient(
        colors = listOf(
            Color(0xE6121A26),
            Color(0xB80A1018),
            Color(0x0005070A),
        ),
    )

    val GlassBottomChrome = Brush.verticalGradient(
        colors = listOf(
            Color(0x0005070A),
            Color(0xCC0A1018),
            Color(0xF0121A26),
        ),
    )

    val SpecularEdge = Brush.verticalGradient(
        colors = listOf(
            Color(0x55FFFFFF),
            Color(0x18FFFFFF),
            Color(0x00FFFFFF),
        ),
    )

    val HeroWash = Brush.verticalGradient(
        colors = listOf(
            Color(0x1005070A),
            Color(0x5505070A),
            Color(0xBB05070A),
            Color(0xF805070A),
        ),
    )

    val HeroSideWash = Brush.horizontalGradient(
        colors = listOf(
            Color(0xAA05070A),
            Color(0x4405070A),
            Color(0x0005070A),
        ),
    )

    val AmberGlow = Brush.horizontalGradient(
        colors = listOf(AmberDeep, AmberMuted, Amber, AmberBright),
    )

    val PanelGlow = Brush.verticalGradient(
        colors = listOf(
            Color(0x331A2433),
            Color(0x000A1018),
        ),
    )

    val BannerStrip = Brush.horizontalGradient(
        colors = listOf(
            Color(0x44E9B04A),
            Color(0x18E9B04A),
            Color(0x00E9B04A),
        ),
    )

    val CinemaVignette = Brush.radialGradient(
        colors = listOf(
            Color(0x0005070A),
            Color(0x6605070A),
            Color(0xCC05070A),
        ),
    )

    val PlayerGlass = Brush.verticalGradient(
        colors = listOf(
            Color(0x0005070A),
            Color(0x880A1018),
            Color(0xEE121A26),
            Color(0xF805070A),
        ),
    )
}
