package com.betterstreamflix.compose.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Obsidian Cinema — premium ink canvas with luminous amber accent.
 * Deep blacks, soft graphite panels, champagne amber — not purple, not cream/terracotta.
 */
object BsColors {
    val Ink = Color(0xFF05070A)
    val InkElevated = Color(0xFF0C1118)
    val InkPanel = Color(0xFF141B24)
    val InkSoft = Color(0xFF1C2531)
    val InkGlass = Color(0xCC0C1118)

    val Amber = Color(0xFFE9B04A)
    val AmberBright = Color(0xFFF6D07A)
    val AmberMuted = Color(0xFFA6792C)
    val AmberDeep = Color(0xFF6B4A14)
    val AmberVeil = Color(0x28E9B04A)

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
    val Hairline = Color(0x28F1F4F8)
    val HairlineStrong = Color(0x3DF1F4F8)
    val FocusRing = Color(0x88E9B04A)

    val Atmosphere = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0B121B),
            Color(0xFF06080C),
            Color(0xFF080C12),
            Color(0xFF04060A),
        ),
    )

    val AtmosphereSheen = Brush.linearGradient(
        colors = listOf(
            Color(0x0005070A),
            Color(0x18E9B04A),
            Color(0x0A3F9694),
            Color(0x0005070A),
        ),
    )

    val HeroWash = Brush.verticalGradient(
        colors = listOf(
            Color(0x1405070A),
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
            Color(0x2A141B24),
            Color(0x000C1118),
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
}
