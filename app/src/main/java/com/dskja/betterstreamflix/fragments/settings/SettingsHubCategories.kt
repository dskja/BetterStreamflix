package com.dskja.betterstreamflix.fragments.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.dskja.betterstreamflix.R

/**
 * Card model for the experimental Settings hub (Support-style cinematic UI).
 */
internal data class SettingsHubCard(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    @DrawableRes val iconRes: Int,
    val target: SettingsHubTarget,
)

internal sealed class SettingsHubTarget {
    data class PreferenceScreen(val key: String) : SettingsHubTarget()
    data object Support : SettingsHubTarget()
    data object About : SettingsHubTarget()
}

internal object SettingsHubCategories {

    val featuredScreenKey = "screen_platform"

    fun appCards(): List<SettingsHubCard> = listOf(
        SettingsHubCard(
            id = "content",
            titleRes = R.string.settings_section_content_title,
            summaryRes = R.string.settings_screen_content_summary,
            iconRes = R.drawable.ic_menu_movie,
            target = SettingsHubTarget.PreferenceScreen("screen_content"),
        ),
        SettingsHubCard(
            id = "playback",
            titleRes = R.string.player_settings,
            summaryRes = R.string.settings_screen_playback_summary,
            iconRes = R.drawable.ic_exp_play,
            target = SettingsHubTarget.PreferenceScreen("screen_playback"),
        ),
        SettingsHubCard(
            id = "downloads",
            titleRes = R.string.settings_screen_downloads,
            summaryRes = R.string.settings_screen_downloads_summary,
            iconRes = R.drawable.ic_menu_downloads,
            target = SettingsHubTarget.PreferenceScreen("screen_downloads"),
        ),
        SettingsHubCard(
            id = "appearance",
            titleRes = R.string.settings_category_appearance,
            summaryRes = R.string.settings_screen_appearance_summary,
            iconRes = R.drawable.ic_brightness,
            target = SettingsHubTarget.PreferenceScreen("screen_appearance"),
        ),
        SettingsHubCard(
            id = "network",
            titleRes = R.string.settings_category_network_title,
            summaryRes = R.string.settings_screen_network_summary,
            iconRes = R.drawable.ic_providers_language,
            target = SettingsHubTarget.PreferenceScreen("screen_network"),
        ),
        SettingsHubCard(
            id = "provider",
            titleRes = R.string.settings_category_provider_title,
            summaryRes = R.string.settings_screen_provider_summary,
            iconRes = R.drawable.ic_provider_default_logo,
            target = SettingsHubTarget.PreferenceScreen("screen_provider"),
        ),
    )

    fun accountCards(): List<SettingsHubCard> = listOf(
        SettingsHubCard(
            id = "cloud",
            titleRes = R.string.cloud_sync_title,
            summaryRes = R.string.cloud_sync_screen_summary,
            iconRes = R.drawable.ic_refresh,
            target = SettingsHubTarget.PreferenceScreen("screen_cloud_sync"),
        ),
        SettingsHubCard(
            id = "serienstream_auth",
            titleRes = R.string.serienstream_auth_category_title,
            summaryRes = R.string.settings_serienstream_session_login_summary,
            iconRes = R.drawable.ic_providers_language,
            target = SettingsHubTarget.PreferenceScreen("screen_serienstream_auth"),
        ),
        SettingsHubCard(
            id = "watchlist",
            titleRes = R.string.settings_watchlist_import_title,
            summaryRes = R.string.settings_watchlist_import_summary,
            iconRes = R.drawable.ic_favorite_enable,
            target = SettingsHubTarget.PreferenceScreen("screen_watchlist_import"),
        ),
        SettingsHubCard(
            id = "backup",
            titleRes = R.string.backup_category_title,
            summaryRes = R.string.settings_screen_backup_summary,
            iconRes = R.drawable.ic_player_settings_download,
            target = SettingsHubTarget.PreferenceScreen("screen_backup"),
        ),
    )

    fun projectCards(): List<SettingsHubCard> = listOf(
        SettingsHubCard(
            id = "support",
            titleRes = R.string.support_settings_entry_title,
            summaryRes = R.string.support_settings_entry_summary,
            iconRes = R.drawable.ic_support_heart,
            target = SettingsHubTarget.Support,
        ),
        SettingsHubCard(
            id = "about",
            titleRes = R.string.settings_about,
            summaryRes = R.string.settings_screen_misc_summary,
            iconRes = R.drawable.ic_settings_about,
            target = SettingsHubTarget.About,
        ),
        SettingsHubCard(
            id = "more",
            titleRes = R.string.miscelaneous_title,
            summaryRes = R.string.settings_hub_more_summary,
            iconRes = R.drawable.ic_settings_help,
            target = SettingsHubTarget.PreferenceScreen("screen_more"),
        ),
    )
}
