package com.betterstreamflix.compose.theme

import androidx.compose.ui.graphics.Brush

/**
 * Default Signal Night fallback for non-composable call sites.
 * Compose UI must use [BsTheme.colors] so selected themes actually restyle screens.
 */
object BsColors {
    private val t = BsThemeCatalog.signalNight

    val Ink = t.Ink
    val InkElevated = t.InkElevated
    val InkPanel = t.InkPanel
    val InkSoft = t.InkSoft
    val InkDeep = t.InkDeep
    val InkGlass = t.InkGlass

    val Glass = t.Glass
    val GlassStrong = t.GlassStrong
    val GlassSoft = t.GlassSoft
    val GlassLift = t.GlassLift
    val GlassFrost = t.GlassFrost

    val Amber = t.Amber
    val AmberBright = t.AmberBright
    val AmberMuted = t.AmberMuted
    val AmberDeep = t.AmberDeep
    val AmberVeil = t.AmberVeil
    val AmberGlass = t.AmberGlass

    val Mist = t.Mist
    val MistDim = t.MistDim
    val MistFaint = t.MistFaint

    val SeaGlass = t.SeaGlass
    val SeaGlassSoft = t.SeaGlassSoft
    val Danger = t.Danger
    val Success = t.Success
    val Warning = t.Warning

    val ScrimTop = t.ScrimTop
    val ScrimBottom = t.ScrimBottom
    val Hairline = t.Hairline
    val HairlineStrong = t.HairlineStrong
    val Specular = t.Specular
    val SpecularSoft = t.SpecularSoft
    val FocusRing = t.FocusRing

    val Atmosphere: Brush get() = t.Atmosphere
    val AtmosphereSheen: Brush get() = t.AtmosphereSheen
    val GlassPanel: Brush get() = t.GlassPanel
    val GlassPanelSelected: Brush get() = t.GlassPanelSelected
    val GlassTopBar: Brush get() = t.GlassTopBar
    val SpecularEdge: Brush get() = t.SpecularEdge
    val HeroWash: Brush get() = t.HeroWash
    val HeroSideWash: Brush get() = t.HeroSideWash
    val AmberGlow: Brush get() = t.AmberGlow
    val PanelGlow: Brush get() = t.PanelGlow
    val BannerStrip: Brush get() = t.BannerStrip
    val CinemaVignette: Brush get() = t.CinemaVignette
    val PlayerGlass: Brush get() = t.PlayerGlass
}
