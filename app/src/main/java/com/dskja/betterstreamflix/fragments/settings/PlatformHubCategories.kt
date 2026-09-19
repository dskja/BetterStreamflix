package com.dskja.betterstreamflix.fragments.settings

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.platform.IntegrationStatus

/** Cards for the experimental Integrations hub (screen_platform root). */
internal object PlatformHubCategories {

    data class Card(
        val screenKey: String,
        @StringRes val titleRes: Int,
        @StringRes val summaryRes: Int,
        @DrawableRes val iconRes: Int,
    )

    fun cards(): List<Card> = listOf(
        Card(
            screenKey = "screen_platform_trakt",
            titleRes = R.string.platform_trakt_category,
            summaryRes = R.string.platform_trakt_enabled_summary,
            iconRes = R.drawable.ic_watched,
        ),
        Card(
            screenKey = "screen_platform_jellyfin",
            titleRes = R.string.platform_jellyfin_category,
            summaryRes = R.string.platform_jellyfin_url_summary,
            iconRes = R.drawable.ic_menu_home,
        ),
        Card(
            screenKey = "screen_platform_plex",
            titleRes = R.string.platform_plex_category,
            summaryRes = R.string.platform_plex_url_summary,
            iconRes = R.drawable.ic_menu_tv,
        ),
        Card(
            screenKey = "screen_platform_debrid",
            titleRes = R.string.platform_debrid_category,
            summaryRes = R.string.platform_debrid_enabled_summary,
            iconRes = R.drawable.ic_offline_badge,
        ),
        Card(
            screenKey = "screen_platform_simkl",
            titleRes = R.string.platform_simkl_category,
            summaryRes = R.string.platform_simkl_enabled_summary,
            iconRes = R.drawable.ic_favorite_enable,
        ),
        Card(
            screenKey = "screen_platform_subtitles",
            titleRes = R.string.platform_opensubtitles_category,
            summaryRes = R.string.platform_opensubtitles_key_summary,
            iconRes = R.drawable.ic_player_settings_subtitle_on,
        ),
        Card(
            screenKey = "screen_platform_player",
            titleRes = R.string.platform_player_category,
            summaryRes = R.string.platform_player_backend_summary,
            iconRes = R.drawable.ic_exp_play,
        ),
        Card(
            screenKey = "screen_platform_plugins",
            titleRes = R.string.platform_plugins_category,
            summaryRes = R.string.platform_plugin_catalog_summary,
            iconRes = R.drawable.ic_providers_language,
        ),
    )

    fun liveSummary(context: Context, screenKey: String): String =
        IntegrationStatus.label(context, IntegrationStatus.forScreen(screenKey, context))

    fun hubSubtitle(context: Context): String {
        val connected = cards()
            .map { IntegrationStatus.forScreen(it.screenKey, context) }
            .count { it.isHealthy }
        return context.getString(R.string.platform_hub_overview, connected, cards().size)
    }
}
