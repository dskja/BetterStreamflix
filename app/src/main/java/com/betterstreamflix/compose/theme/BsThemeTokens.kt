package com.betterstreamflix.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.betterstreamflix.utils.ThemeManager

/**
 * Semantic Arc tokens for one active look.
 * Accent channel is named Amber historically; values are theme accents (vermilion in Arc).
 * Compose UI paints from [LocalBsColors] so theme changes restyle the whole app.
 */
data class BsColorScheme(
    val Ink: Color,
    val InkElevated: Color,
    val InkPanel: Color,
    val InkSoft: Color,
    val InkDeep: Color,
    val Glass: Color,
    val GlassStrong: Color,
    val GlassSoft: Color,
    val GlassLift: Color,
    val Amber: Color,
    val AmberBright: Color,
    val AmberMuted: Color,
    val AmberDeep: Color,
    val AmberVeil: Color,
    val Mist: Color,
    val MistDim: Color,
    val MistFaint: Color,
    val SeaGlass: Color,
    val SeaGlassSoft: Color,
    val Danger: Color,
    val Success: Color,
    val Warning: Color,
    val Hairline: Color,
    val HairlineStrong: Color,
    val Specular: Color,
    val SpecularSoft: Color,
    val FocusRing: Color,
    val ScrimTop: Color,
    val ScrimBottom: Color,
) {
    val InkGlass get() = Glass
    val GlassFrost get() = Mist.copy(alpha = 0.14f)
    val AmberGlass get() = Amber.copy(alpha = 0.18f)

    val AmberGlow: Brush
        get() = Brush.horizontalGradient(listOf(AmberDeep, AmberMuted, Amber, AmberBright))

    val Atmosphere: Brush
        get() = Brush.verticalGradient(
            listOf(
                InkElevated,
                Ink,
                InkDeep,
            ),
        )

    val AtmosphereSheen: Brush
        get() = Brush.linearGradient(
            listOf(
                Color.Transparent,
                Amber.copy(alpha = 0.06f),
                SeaGlass.copy(alpha = 0.04f),
                Color.Transparent,
            ),
        )

    val GlassPanel: Brush
        get() = Brush.verticalGradient(
            listOf(
                InkPanel.copy(alpha = 0.96f),
                InkElevated.copy(alpha = 0.94f),
            ),
        )

    val GlassPanelSelected: Brush
        get() = Brush.verticalGradient(
            listOf(
                InkSoft.copy(alpha = 0.98f),
                InkPanel.copy(alpha = 0.96f),
            ),
        )

    val GlassTopBar: Brush
        get() = Brush.verticalGradient(
            listOf(
                InkPanel.copy(alpha = 0.94f),
                InkElevated.copy(alpha = 0.72f),
                Color.Transparent,
            ),
        )

    val SpecularEdge: Brush
        get() = Brush.verticalGradient(listOf(SpecularSoft, Color.Transparent))

    val HeroWash: Brush
        get() = Brush.verticalGradient(
            listOf(
                Ink.copy(alpha = 0.06f),
                Ink.copy(alpha = 0.12f),
                Ink.copy(alpha = 0.46f),
                Ink.copy(alpha = 0.94f),
            ),
        )

    val HeroSideWash: Brush
        get() = Brush.horizontalGradient(
            listOf(
                Ink.copy(alpha = 0.48f),
                Ink.copy(alpha = 0.14f),
                Color.Transparent,
            ),
        )

    val BannerStrip: Brush
        get() = Brush.horizontalGradient(
            listOf(
                Amber.copy(alpha = 0.22f),
                Amber.copy(alpha = 0.08f),
                Color.Transparent,
            ),
        )

    val CinemaVignette: Brush
        get() = Brush.radialGradient(
            listOf(
                Color.Transparent,
                Ink.copy(alpha = 0.35f),
                Ink.copy(alpha = 0.78f),
            ),
        )

    val PlayerGlass: Brush
        get() = Brush.verticalGradient(
            listOf(
                Color.Transparent,
                InkElevated.copy(alpha = 0.45f),
                InkPanel.copy(alpha = 0.90f),
                InkDeep.copy(alpha = 0.98f),
            ),
        )

    val PlayerTopGlass: Brush
        get() = Brush.verticalGradient(
            listOf(
                InkDeep.copy(alpha = 0.90f),
                InkPanel.copy(alpha = 0.48f),
                Color.Transparent,
            ),
        )

    val PanelGlow: Brush
        get() = Brush.verticalGradient(listOf(InkPanel.copy(alpha = 0.14f), Color.Transparent))
}

object BsThemeCatalog {
    fun tokensFor(themeId: String): BsColorScheme = when (themeId) {
        ThemeManager.NERO_AMOLED_OLED -> amoled
        ThemeManager.SUNSET_CINEMA -> sunset
        ThemeManager.STEEL_BLUE -> steel
        ThemeManager.FOREST_NIGHT -> forest
        ThemeManager.CRIMSON_NOIR -> crimson
        ThemeManager.MIDNIGHT_VIOLET -> violet
        ThemeManager.NORD_FROST -> nord
        ThemeManager.EMERALD_LUXE -> emerald
        ThemeManager.RETRO_NEON -> retro
        else -> arc
    }

    /** Default Arc — near-black cinema surfaces + vivid vermilion. */
    val arc = scheme(
        ink = Color(0xFF08090B),
        elevated = Color(0xFF0D0F12),
        panel = Color(0xFF111317),
        soft = Color(0xFF1A1D22),
        deep = Color(0xFF050608),
        accent = Color(0xFFFF5A36),
        accentBright = Color(0xFFFF795C),
        accentMuted = Color(0xFFB93B21),
        accentDeep = Color(0xFF6F2415),
        text = Color(0xFFF7F7F4),
        textDim = Color(0xFF9B9DA2),
        textFaint = Color(0xFF66696F),
        secondary = Color(0xFF80D6A2),
        secondarySoft = Color(0xFF285A3C),
    )

    /** Back-compat alias used by [BsColors]. */
    val obsidian get() = arc

    val amoled = scheme(
        ink = Color(0xFF000000),
        elevated = Color(0xFF0A0A0A),
        panel = Color(0xFF141414),
        soft = Color(0xFF1C1C1C),
        deep = Color(0xFF000000),
        accent = Color(0xFFFF5A36),
        accentBright = Color(0xFFFF795C),
        accentMuted = Color(0xFF8A8A8A),
        accentDeep = Color(0xFF6F2415),
        text = Color(0xFFFFFFFF),
        textDim = Color(0xFFBDBDBD),
        textFaint = Color(0xFF7A7A7A),
        secondary = Color(0xFF80D6A2),
        secondarySoft = Color(0xFF444444),
    )

    val sunset = scheme(
        ink = Color(0xFF1A1210),
        elevated = Color(0xFF241814),
        panel = Color(0xFF2E201A),
        soft = Color(0xFF3A2A22),
        deep = Color(0xFF100C0A),
        accent = Color(0xFFE08A5A),
        accentBright = Color(0xFFF0A878),
        accentMuted = Color(0xFFB07858),
        accentDeep = Color(0xFF8A4E2A),
        text = Color(0xFFFFF0E5),
        textDim = Color(0xFFD7B7A4),
        textFaint = Color(0xFFA88878),
        secondary = Color(0xFF3DE0C5),
        secondarySoft = Color(0xFF2A6A5E),
    )

    val steel = scheme(
        ink = Color(0xFF101820),
        elevated = Color(0xFF182028),
        panel = Color(0xFF222C38),
        soft = Color(0xFF2C3846),
        deep = Color(0xFF0A1016),
        accent = Color(0xFF5BB8E8),
        accentBright = Color(0xFF8AD0F5),
        accentMuted = Color(0xFF6A8AA8),
        accentDeep = Color(0xFF2A6A9E),
        text = Color(0xFFEAF4FF),
        textDim = Color(0xFFADC5DA),
        textFaint = Color(0xFF7A92A8),
        secondary = Color(0xFFE08A5A),
        secondarySoft = Color(0xFF4A657A),
    )

    val forest = scheme(
        ink = Color(0xFF0E1814),
        elevated = Color(0xFF15201C),
        panel = Color(0xFF1C2C26),
        soft = Color(0xFF263830),
        deep = Color(0xFF080E0C),
        accent = Color(0xFF3DE0C5),
        accentBright = Color(0xFF6FF0DA),
        accentMuted = Color(0xFF6A9E92),
        accentDeep = Color(0xFF1A6B5E),
        text = Color(0xFFE6FFF8),
        textDim = Color(0xFFA8D1C7),
        textFaint = Color(0xFF6A9088),
        secondary = Color(0xFFE08A5A),
        secondarySoft = Color(0xFF3A6A60),
    )

    val crimson = scheme(
        ink = Color(0xFF160E10),
        elevated = Color(0xFF1E1418),
        panel = Color(0xFF2A1A20),
        soft = Color(0xFF362428),
        deep = Color(0xFF0C080A),
        accent = Color(0xFFE07080),
        accentBright = Color(0xFFF0909E),
        accentMuted = Color(0xFFA07078),
        accentDeep = Color(0xFF8A3544),
        text = Color(0xFFFFECEF),
        textDim = Color(0xFFD6B2BA),
        textFaint = Color(0xFF907078),
        secondary = Color(0xFF3DE0C5),
        secondarySoft = Color(0xFF6A3A44),
    )

    val violet = scheme(
        ink = Color(0xFF121018),
        elevated = Color(0xFF1A1822),
        panel = Color(0xFF242030),
        soft = Color(0xFF2E2A3C),
        deep = Color(0xFF0A0810),
        accent = Color(0xFF9A90E8),
        accentBright = Color(0xFFB8B0F5),
        accentMuted = Color(0xFF7870A0),
        accentDeep = Color(0xFF4A4888),
        text = Color(0xFFF1EEFF),
        textDim = Color(0xFFBFB9DD),
        textFaint = Color(0xFF7870A0),
        secondary = Color(0xFF3DE0C5),
        secondarySoft = Color(0xFF4A4670),
    )

    val nord = scheme(
        ink = Color(0xFF10161C),
        elevated = Color(0xFF181E26),
        panel = Color(0xFF222A34),
        soft = Color(0xFF2C3642),
        deep = Color(0xFF0A0E12),
        accent = Color(0xFF6AC0D8),
        accentBright = Color(0xFF90D8EC),
        accentMuted = Color(0xFF6A8898),
        accentDeep = Color(0xFF2A6A80),
        text = Color(0xFFEAF7FD),
        textDim = Color(0xFFB1C9D6),
        textFaint = Color(0xFF708898),
        secondary = Color(0xFFE08A5A),
        secondarySoft = Color(0xFF3A5566),
    )

    val emerald = scheme(
        ink = Color(0xFF0E1612),
        elevated = Color(0xFF141E18),
        panel = Color(0xFF1C2A22),
        soft = Color(0xFF26362C),
        deep = Color(0xFF080E0A),
        accent = Color(0xFF4ED89A),
        accentBright = Color(0xFF78E8B4),
        accentMuted = Color(0xFF6A9880),
        accentDeep = Color(0xFF2A7850),
        text = Color(0xFFEDF9F2),
        textDim = Color(0xFFB6D0C0),
        textFaint = Color(0xFF708878),
        secondary = Color(0xFFE08A5A),
        secondarySoft = Color(0xFF3A6A52),
    )

    val retro = scheme(
        ink = Color(0xFF101014),
        elevated = Color(0xFF18181E),
        panel = Color(0xFF22222A),
        soft = Color(0xFF2E2E38),
        deep = Color(0xFF08080C),
        accent = Color(0xFF3DE0C5),
        accentBright = Color(0xFF6FF0DA),
        accentMuted = Color(0xFF8A70A0),
        accentDeep = Color(0xFF1A6B5E),
        text = Color(0xFFF4F7FF),
        textDim = Color(0xFFCFB7DA),
        textFaint = Color(0xFF8A70A0),
        secondary = Color(0xFFE08A5A),
        secondarySoft = Color(0xFF5A3A6A),
    )

    private fun scheme(
        ink: Color,
        elevated: Color,
        panel: Color,
        soft: Color,
        deep: Color,
        accent: Color,
        accentBright: Color,
        accentMuted: Color,
        accentDeep: Color,
        text: Color,
        textDim: Color,
        textFaint: Color,
        secondary: Color,
        secondarySoft: Color,
    ) = BsColorScheme(
        Ink = ink,
        InkElevated = elevated,
        InkPanel = panel,
        InkSoft = soft,
        InkDeep = deep,
        Glass = elevated.copy(alpha = 0.88f),
        GlassStrong = panel.copy(alpha = 0.94f),
        GlassSoft = elevated.copy(alpha = 0.62f),
        GlassLift = soft.copy(alpha = 0.92f),
        Amber = accent,
        AmberBright = accentBright,
        AmberMuted = accentMuted,
        AmberDeep = accentDeep,
        AmberVeil = accent.copy(alpha = 0.14f),
        Mist = text,
        MistDim = textDim,
        MistFaint = textFaint,
        SeaGlass = secondary,
        SeaGlassSoft = secondarySoft,
        Danger = Color(0xFFE35F5F),
        Success = Color(0xFF4DB787),
        Warning = Color(0xFFE08A5A),
        Hairline = text.copy(alpha = 0.12f),
        HairlineStrong = text.copy(alpha = 0.22f),
        Specular = Color(0x33FFFFFF),
        SpecularSoft = Color(0x14FFFFFF),
        FocusRing = accent.copy(alpha = 0.55f),
        ScrimTop = ink.copy(alpha = 0.82f),
        ScrimBottom = ink.copy(alpha = 0.96f),
    )
}

val LocalBsColors = staticCompositionLocalOf { BsThemeCatalog.arc }

object BsTheme {
    val colors: BsColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalBsColors.current
}
