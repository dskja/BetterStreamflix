package com.betterstreamflix.compose.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Obsidian Stream — cinematic ink canvas with luminous amber accent.
 * Intentionally not Netflix-red, not purple-gradient, not cream/terracotta.
 */
object BsColors {
    val Ink = Color(0xFF07090D)
    val InkElevated = Color(0xFF10151C)
    val InkPanel = Color(0xFF161D27)
    val InkSoft = Color(0xFF1E2733)

    val Amber = Color(0xFFE8A838)
    val AmberBright = Color(0xFFF2C15A)
    val AmberMuted = Color(0xFF9A6E24)
    val AmberDeep = Color(0xFF6E4C16)

    val Mist = Color(0xFFE8EDF4)
    val MistDim = Color(0xFFA8B3C4)
    val MistFaint = Color(0xFF6B778A)

    val SeaGlass = Color(0xFF3D8B8A)
    val SeaGlassSoft = Color(0xFF2A5F5E)
    val Danger = Color(0xFFE05A5A)
    val Success = Color(0xFF4CAF7A)

    val ScrimTop = Color(0xCC07090D)
    val ScrimBottom = Color(0xF207090D)
    val Hairline = Color(0x22E8EDF4)
    val HairlineStrong = Color(0x33E8EDF4)
    val FocusRing = Color(0x66E8A838)

    val Atmosphere = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0E1620),
            Color(0xFF07090D),
            Color(0xFF090E14),
            Color(0xFF06080C),
        ),
    )

    val AtmosphereSheen = Brush.linearGradient(
        colors = listOf(
            Color(0x0007090D),
            Color(0x14E8A838),
            Color(0x0007090D),
        ),
    )

    val HeroWash = Brush.verticalGradient(
        colors = listOf(
            Color(0x2207090D),
            Color(0x6607090D),
            Color(0xCC07090D),
            Color(0xF707090D),
        ),
    )

    val HeroSideWash = Brush.horizontalGradient(
        colors = listOf(
            Color(0x9907090D),
            Color(0x3307090D),
            Color(0x0007090D),
        ),
    )

    val AmberGlow = Brush.horizontalGradient(
        colors = listOf(AmberDeep, AmberMuted, Amber, AmberBright),
    )

    val PanelGlow = Brush.verticalGradient(
        colors = listOf(
            Color(0x22161D27),
            Color(0x0010151C),
        ),
    )

    val BannerStrip = Brush.horizontalGradient(
        colors = listOf(
            Color(0x33E8A838),
            Color(0x14E8A838),
            Color(0x00E8A838),
        ),
    )
}
