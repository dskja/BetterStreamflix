package com.betterstreamflix.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.betterstreamflix.utils.ThemeManager

/**
 * Semantic Liquid-Glass tokens for one active look.
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
    val GlassFrost get() = Mist.copy(alpha = 0.20f)
    val AmberGlass get() = Amber.copy(alpha = 0.20f)

    val AmberGlow: Brush
        get() = Brush.horizontalGradient(listOf(AmberDeep, AmberMuted, Amber, AmberBright))

    val Atmosphere: Brush
        get() = Brush.verticalGradient(
            listOf(
                InkElevated.lerpToward(Amber, 0.06f),
                Ink,
                InkSoft.lerpToward(Ink, 0.55f),
                InkDeep,
            ),
        )

    val AtmosphereSheen: Brush
        get() = Brush.linearGradient(
            listOf(
                Color.Transparent,
                Amber.copy(alpha = 0.12f),
                SeaGlass.copy(alpha = 0.08f),
                Color.Transparent,
            ),
        )

    val GlassPanel: Brush
        get() = Brush.verticalGradient(
            listOf(
                InkPanel.copy(alpha = 0.82f),
                InkElevated.copy(alpha = 0.72f),
                Glass,
            ),
        )

    val GlassPanelSelected: Brush
        get() = Brush.verticalGradient(
            listOf(
                InkSoft.copy(alpha = 0.88f),
                InkPanel.copy(alpha = 0.78f),
                InkElevated.copy(alpha = 0.72f),
            ),
        )

    val GlassTopBar: Brush
        get() = Brush.verticalGradient(
            listOf(
                InkPanel.copy(alpha = 0.92f),
                InkElevated.copy(alpha = 0.72f),
                Color.Transparent,
            ),
        )

    val SpecularEdge: Brush
        get() = Brush.verticalGradient(listOf(Specular, SpecularSoft, Color.Transparent))

    val HeroWash: Brush
        get() = Brush.verticalGradient(
            listOf(
                Ink.copy(alpha = 0.08f),
                Ink.copy(alpha = 0.35f),
                Ink.copy(alpha = 0.75f),
                Ink.copy(alpha = 0.97f),
            ),
        )

    val HeroSideWash: Brush
        get() = Brush.horizontalGradient(
            listOf(
                Ink.copy(alpha = 0.72f),
                Ink.copy(alpha = 0.28f),
                Color.Transparent,
            ),
        )

    val BannerStrip: Brush
        get() = Brush.horizontalGradient(
            listOf(
                Amber.copy(alpha = 0.28f),
                Amber.copy(alpha = 0.10f),
                Color.Transparent,
            ),
        )

    val CinemaVignette: Brush
        get() = Brush.radialGradient(
            listOf(
                Color.Transparent,
                Ink.copy(alpha = 0.40f),
                Ink.copy(alpha = 0.80f),
            ),
        )

    val PlayerGlass: Brush
        get() = Brush.verticalGradient(
            listOf(
                Color.Transparent,
                InkElevated.copy(alpha = 0.55f),
                InkPanel.copy(alpha = 0.92f),
                InkDeep.copy(alpha = 0.98f),
            ),
        )

    val PlayerTopGlass: Brush
        get() = Brush.verticalGradient(
            listOf(
                InkDeep.copy(alpha = 0.92f),
                InkPanel.copy(alpha = 0.55f),
                Color.Transparent,
            ),
        )

    val PanelGlow: Brush
        get() = Brush.verticalGradient(listOf(InkPanel.copy(alpha = 0.22f), Color.Transparent))
}

private fun Color.lerpToward(other: Color, t: Float): Color {
    val amount = t.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * amount,
        green = green + (other.green - green) * amount,
        blue = blue + (other.blue - blue) * amount,
        alpha = alpha + (other.alpha - alpha) * amount,
    )
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
        else -> obsidian
    }

    val obsidian = scheme(
        ink = Color(0xFF05070A),
        elevated = Color(0xFF0A1018),
        panel = Color(0xFF121A24),
        soft = Color(0xFF1A2430),
        deep = Color(0xFF030508),
        accent = Color(0xFFE9B04A),
        accentBright = Color(0xFFF6D07A),
        accentMuted = Color(0xFFA6792C),
        accentDeep = Color(0xFF6B4A14),
        text = Color(0xFFF1F4F8),
        textDim = Color(0xFFB0BAC8),
        textFaint = Color(0xFF6F7B8E),
        secondary = Color(0xFF3F9694),
        secondarySoft = Color(0xFF2A6462),
    )

    val amoled = scheme(
        ink = Color(0xFF000000),
        elevated = Color(0xFF050505),
        panel = Color(0xFF121212),
        soft = Color(0xFF1A1A1A),
        deep = Color(0xFF000000),
        accent = Color(0xFFFFFFFF),
        accentBright = Color(0xFFFFFFFF),
        accentMuted = Color(0xFFBDBDBD),
        accentDeep = Color(0xFF7A7A7A),
        text = Color(0xFFFFFFFF),
        textDim = Color(0xFFBDBDBD),
        textFaint = Color(0xFF7A7A7A),
        secondary = Color(0xFFBDBDBD),
        secondarySoft = Color(0xFF444444),
    )

    val sunset = scheme(
        ink = Color(0xFF2B1812),
        elevated = Color(0xFF352019),
        panel = Color(0xFF3F281F),
        soft = Color(0xFF4A3228),
        deep = Color(0xFF1C0F0C),
        accent = Color(0xFFF39A5B),
        accentBright = Color(0xFFFFB884),
        accentMuted = Color(0xFFC49B86),
        accentDeep = Color(0xFF8A4E2A),
        text = Color(0xFFFFF0E5),
        textDim = Color(0xFFD7B7A4),
        textFaint = Color(0xFFC49B86),
        secondary = Color(0xFFD7B7A4),
        secondarySoft = Color(0xFF8A5E4A),
    )

    val steel = scheme(
        ink = Color(0xFF1A2430),
        elevated = Color(0xFF222E3C),
        panel = Color(0xFF2B3949),
        soft = Color(0xFF344455),
        deep = Color(0xFF101820),
        accent = Color(0xFF7DB3E8),
        accentBright = Color(0xFFA8D0F5),
        accentMuted = Color(0xFF8CA2B8),
        accentDeep = Color(0xFF3A6F9E),
        text = Color(0xFFEAF4FF),
        textDim = Color(0xFFADC5DA),
        textFaint = Color(0xFF8CA2B8),
        secondary = Color(0xFFADC5DA),
        secondarySoft = Color(0xFF4A657A),
    )

    val forest = scheme(
        ink = Color(0xFF13221E),
        elevated = Color(0xFF1A2C27),
        panel = Color(0xFF223832),
        soft = Color(0xFF2B453D),
        deep = Color(0xFF0B1613),
        accent = Color(0xFF65D7C2),
        accentBright = Color(0xFF95F0DE),
        accentMuted = Color(0xFF8DB3AA),
        accentDeep = Color(0xFF2E8A7A),
        text = Color(0xFFE6FFF8),
        textDim = Color(0xFFA8D1C7),
        textFaint = Color(0xFF8DB3AA),
        secondary = Color(0xFFA8D1C7),
        secondarySoft = Color(0xFF3A6A60),
    )

    val crimson = scheme(
        ink = Color(0xFF241015),
        elevated = Color(0xFF2E171C),
        panel = Color(0xFF3A1E24),
        soft = Color(0xFF472830),
        deep = Color(0xFF16090C),
        accent = Color(0xFFD86A7A),
        accentBright = Color(0xFFF08A98),
        accentMuted = Color(0xFFA88891),
        accentDeep = Color(0xFF8A3544),
        text = Color(0xFFFFECEF),
        textDim = Color(0xFFD6B2BA),
        textFaint = Color(0xFFA88891),
        secondary = Color(0xFFD6B2BA),
        secondarySoft = Color(0xFF6A3A44),
    )

    val violet = scheme(
        ink = Color(0xFF181726),
        elevated = Color(0xFF211F33),
        panel = Color(0xFF2B2840),
        soft = Color(0xFF35324C),
        deep = Color(0xFF0F0E1A),
        accent = Color(0xFFAFA3FF),
        accentBright = Color(0xFFCDC4FF),
        accentMuted = Color(0xFF8F8AAE),
        accentDeep = Color(0xFF6A5FC0),
        text = Color(0xFFF1EEFF),
        textDim = Color(0xFFBFB9DD),
        textFaint = Color(0xFF8F8AAE),
        secondary = Color(0xFFBFB9DD),
        secondarySoft = Color(0xFF4A4670),
    )

    val nord = scheme(
        ink = Color(0xFF18212A),
        elevated = Color(0xFF212C38),
        panel = Color(0xFF2A3846),
        soft = Color(0xFF344554),
        deep = Color(0xFF0E151C),
        accent = Color(0xFF8ED0E8),
        accentBright = Color(0xFFB5E4F5),
        accentMuted = Color(0xFF8EA2B0),
        accentDeep = Color(0xFF3A7F98),
        text = Color(0xFFEAF7FD),
        textDim = Color(0xFFB1C9D6),
        textFaint = Color(0xFF8EA2B0),
        secondary = Color(0xFFB1C9D6),
        secondarySoft = Color(0xFF3A5566),
    )

    val emerald = scheme(
        ink = Color(0xFF13211B),
        elevated = Color(0xFF1A2B23),
        panel = Color(0xFF22362C),
        soft = Color(0xFF2B4337),
        deep = Color(0xFF0A1511),
        accent = Color(0xFF7ED6A3),
        accentBright = Color(0xFFA8EBC2),
        accentMuted = Color(0xFF98AE9F),
        accentDeep = Color(0xFF3A8A5E),
        text = Color(0xFFEDF9F2),
        textDim = Color(0xFFB6D0C0),
        textFaint = Color(0xFF98AE9F),
        secondary = Color(0xFFB6D0C0),
        secondarySoft = Color(0xFF3A6A52),
    )

    val retro = scheme(
        ink = Color(0xFF18181E),
        elevated = Color(0xFF212129),
        panel = Color(0xFF2C2C36),
        soft = Color(0xFF383844),
        deep = Color(0xFF0E0E12),
        accent = Color(0xFF4DE0D7),
        accentBright = Color(0xFF7FF0E9),
        accentMuted = Color(0xFFA56FBD),
        accentDeep = Color(0xFF1F9E96),
        text = Color(0xFFF4F7FF),
        textDim = Color(0xFFCFB7DA),
        textFaint = Color(0xFFA56FBD),
        secondary = Color(0xFFCFB7DA),
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
        Glass = elevated.copy(alpha = 0.70f),
        GlassStrong = panel.copy(alpha = 0.80f),
        GlassSoft = elevated.copy(alpha = 0.50f),
        GlassLift = soft.copy(alpha = 0.85f),
        Amber = accent,
        AmberBright = accentBright,
        AmberMuted = accentMuted,
        AmberDeep = accentDeep,
        AmberVeil = accent.copy(alpha = 0.16f),
        Mist = text,
        MistDim = textDim,
        MistFaint = textFaint,
        SeaGlass = secondary,
        SeaGlassSoft = secondarySoft,
        Danger = Color(0xFFE35F5F),
        Success = Color(0xFF4DB787),
        Warning = Color(0xFFE0A045),
        Hairline = text.copy(alpha = 0.18f),
        HairlineStrong = text.copy(alpha = 0.28f),
        Specular = Color(0x55FFFFFF),
        SpecularSoft = Color(0x22FFFFFF),
        FocusRing = accent.copy(alpha = 0.55f),
        ScrimTop = ink.copy(alpha = 0.80f),
        ScrimBottom = ink.copy(alpha = 0.96f),
    )
}

val LocalBsColors = staticCompositionLocalOf { BsThemeCatalog.obsidian }

object BsTheme {
    val colors: BsColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalBsColors.current
}
