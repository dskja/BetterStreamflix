package com.betterstreamflix.compose.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsBrandMark
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsSettingsActionRow
import com.betterstreamflix.compose.components.BsSettingsChoiceDialog
import com.betterstreamflix.compose.components.BsSettingsNavTile
import com.betterstreamflix.compose.components.BsSettingsSectionLabel
import com.betterstreamflix.compose.components.BsSettingsTextFieldDialog
import com.betterstreamflix.compose.components.BsSettingsToggleRow
import com.betterstreamflix.compose.components.BsSettingsValueRow
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.utils.ThemeManager
import java.util.Locale

enum class SettingsDestination {
    Hub,
    Content,
    Playback,
    Appearance,
    Network,
    Provider,
    Cloud,
    Backup,
    About,
}

data class SettingsUiState(
    val autoplay: Boolean,
    val autoplayBufferSec: String,
    val forceExtraBuffering: Boolean,
    val playerGestures: Boolean,
    val keepScreenOnWhenPaused: Boolean,
    val qualityHeight: String,
    val serverAutoSubtitlesDisabled: Boolean,
    val themeId: String,
    val themeLabel: String,
    val appLanguage: String,
    val appLanguageLabel: String,
    val immersiveMode: Boolean,
    val enableTmdb: Boolean,
    val tmdbApiKeyMasked: String,
    val parentalMaxAgeLabel: String,
    val hasParentalPin: Boolean,
    val dohLabel: String,
    val dohValue: String,
    val subdlApiKeyMasked: String,
    val providerName: String,
    val providerUrl: String,
    val providerAutoupdate: Boolean,
    val streamingcommunityDomain: String,
    val serienstreamDomain: String,
    val aniworldDomain: String,
    val moflixDomain: String,
    val cuevanaDomain: String,
    val poseidonDomain: String,
    val updateCheckEnabled: Boolean,
    val newContentNotifications: Boolean,
    val versionName: String,
)

data class SettingsActions(
    val onBack: () -> Unit,
    val onOpenDownloads: () -> Unit,
    val onOpenAbout: () -> Unit = {},
    val onOpenHelp: () -> Unit = {},
    val onOpenTelegram: () -> Unit = {},
    val onScanResolverQr: () -> Unit = {},
    val onExportBackup: () -> Unit = {},
    val onImportBackup: () -> Unit = {},
    val onExportDb: () -> Unit = {},
    val onImportDb: () -> Unit = {},
    val onClearCache: () -> Unit = {},
    val onThemeSelected: (String) -> Unit,
    val onLanguageSelected: (String) -> Unit,
    val onDohSelected: (String) -> Unit,
    val onQualitySelected: (String) -> Unit,
    val onToggle: (key: String, value: Boolean) -> Unit,
    val onEditText: (key: String, value: String) -> Unit,
    val onAction: (key: String) -> Unit,
    val onRefresh: () -> Unit = {},
)

private val BsThemeOptions = listOf(
    ThemeManager.DEFAULT,
    ThemeManager.NERO_AMOLED_OLED,
    ThemeManager.SUNSET_CINEMA,
    ThemeManager.STEEL_BLUE,
    ThemeManager.FOREST_NIGHT,
    ThemeManager.CRIMSON_NOIR,
    ThemeManager.MIDNIGHT_VIOLET,
    ThemeManager.NORD_FROST,
    ThemeManager.EMERALD_LUXE,
    ThemeManager.RETRO_NEON,
)

private val BsQualityOptions = listOf("", "360", "480", "720", "1080")

private val BsLanguageOptions = listOf("system", "en", "de", "it", "fr", "es", "ar")

private val BsDohOptions = listOf(
    "" to "None",
    "https://dns.google/dns-query" to "Google",
    "https://cloudflare-dns.com/dns-query" to "Cloudflare",
    "https://doh.opendns.com/dns-query" to "OpenDNS",
    "https://dns.quad9.net/dns-query" to "Quad9",
    "https://doh.cleanbrowsing.org/doh/family-filter/" to "CleanBrowsing",
    "https://dns.adguard-dns.com/dns-query" to "AdGuard",
    "https://unfiltered.joindns4.eu/dns-query" to "DNS4EU",
    "https://ns0.fdn.fr/dns-query" to "FDN",
)

private fun languageLabel(code: String): String = when (code) {
    "system" -> "System default"
    "" -> "System default"
    else -> {
        val locale = Locale(code)
        locale.getDisplayName(locale).replaceFirstChar { c -> c.uppercaseChar() }
    }
}

private fun qualityLabel(height: String): String = if (height.isBlank()) "Auto" else "${height}p"

@Composable
fun SettingsExperience(
    destination: SettingsDestination,
    onNavigate: (SettingsDestination) -> Unit,
    state: SettingsUiState,
    actions: SettingsActions,
    isTv: Boolean = false,
) {
    BsAtmosphere {
        when (destination) {
            SettingsDestination.Hub -> SettingsHubBody(onNavigate = onNavigate, state = state, actions = actions)
            SettingsDestination.Content -> SettingsContentSection(state = state, actions = actions, onBack = actions.onBack)
            SettingsDestination.Playback -> SettingsPlaybackSection(state = state, actions = actions, onBack = actions.onBack)
            SettingsDestination.Appearance -> SettingsAppearanceSection(state = state, actions = actions, onBack = actions.onBack)
            SettingsDestination.Network -> SettingsNetworkSection(state = state, actions = actions, onBack = actions.onBack)
            SettingsDestination.Provider -> SettingsProviderSection(state = state, actions = actions, onBack = actions.onBack)
            SettingsDestination.Cloud -> SettingsCloudSection(state = state, actions = actions, onBack = actions.onBack)
            SettingsDestination.Backup -> SettingsBackupSection(state = state, actions = actions, onBack = actions.onBack)
            SettingsDestination.About -> SettingsAboutSection(state = state, actions = actions, onBack = actions.onBack)
        }
    }
}

private data class HubEntry(
    val title: String,
    val subtitle: String,
    val accentHint: String? = null,
    val onClick: () -> Unit,
)

@Composable
private fun SettingsHubBody(
    onNavigate: (SettingsDestination) -> Unit,
    state: SettingsUiState,
    actions: SettingsActions,
) {
    var visible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { visible = true }
    val headerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "settingsHeaderAlpha",
    )

    val entries = listOf(
        HubEntry(
            title = stringResource(R.string.settings_section_content_title),
            subtitle = stringResource(R.string.settings_screen_content_summary),
            onClick = { onNavigate(SettingsDestination.Content) },
        ),
        HubEntry(
            title = stringResource(R.string.player_settings),
            subtitle = "Autoplay, buffering, quality and gestures",
            onClick = { onNavigate(SettingsDestination.Playback) },
        ),
        HubEntry(
            title = stringResource(R.string.settings_category_appearance),
            subtitle = "Theme, app language and immersive mode",
            onClick = { onNavigate(SettingsDestination.Appearance) },
        ),
        HubEntry(
            title = stringResource(R.string.settings_category_network_title),
            subtitle = stringResource(R.string.settings_screen_network_summary),
            onClick = { onNavigate(SettingsDestination.Network) },
        ),
        HubEntry(
            title = stringResource(R.string.settings_category_provider_title),
            subtitle = state.providerName,
            onClick = { onNavigate(SettingsDestination.Provider) },
        ),
        HubEntry(
            title = stringResource(R.string.cloud_sync_title),
            subtitle = "Account, Trakt and cloud sync",
            onClick = { onNavigate(SettingsDestination.Cloud) },
        ),
        HubEntry(
            title = stringResource(R.string.backup_category_title),
            subtitle = stringResource(R.string.settings_screen_backup_summary),
            onClick = { onNavigate(SettingsDestination.Backup) },
        ),
        HubEntry(
            title = stringResource(R.string.settings_about),
            subtitle = stringResource(R.string.settings_about_version_name, state.versionName),
            onClick = { onNavigate(SettingsDestination.About) },
        ),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .alpha(headerAlpha),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                BsBrandMark(compact = true)
                Spacer(modifier = Modifier.height(10.dp))
                androidx.compose.material3.Text(
                    text = "Settings",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    color = BsColors.Mist,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        ) {
            items(entries.size) { index ->
                val entry = entries[index]
                var tileVisible by remember { mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(Unit) { tileVisible = true }
                val tileAlpha by animateFloatAsState(
                    targetValue = if (tileVisible) 1f else 0f,
                    animationSpec = tween(durationMillis = 360, delayMillis = index * 45),
                    label = "tileAlpha$index",
                )
                BsSettingsNavTile(
                    title = entry.title,
                    subtitle = entry.subtitle,
                    accentHint = entry.accentHint,
                    onClick = entry.onClick,
                    modifier = Modifier.alpha(tileAlpha),
                )
            }
            item {
                BsSettingsSectionLabel(title = "Quick access")
            }
            item {
                BsSettingsNavTile(
                    title = stringResource(R.string.downloads_title),
                    subtitle = stringResource(R.string.downloads_settings_subtitle),
                    accentHint = "↓",
                    onClick = actions.onOpenDownloads,
                )
            }
            item {
                BsSettingsToggleRow(
                    title = stringResource(R.string.settings_new_content_notifications),
                    subtitle = stringResource(R.string.settings_new_content_notifications_summary),
                    checked = state.newContentNotifications,
                    onCheckedChange = { actions.onToggle("newContentNotifications", it) },
                )
            }
            item {
                BsSettingsActionRow(
                    title = stringResource(R.string.settings_new_content_clear_history),
                    subtitle = stringResource(R.string.settings_new_content_clear_history_summary),
                    onClick = { actions.onAction("clearNewContentHistory") },
                )
            }
            item {
                BsSettingsToggleRow(
                    title = stringResource(R.string.settings_update_check),
                    subtitle = stringResource(R.string.settings_update_check_summary),
                    checked = state.updateCheckEnabled,
                    onCheckedChange = { actions.onToggle("updateCheckEnabled", it) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionScaffold(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BsGhostButton(text = "\u2039 Back", onClick = onBack)
            Spacer(modifier = Modifier.width(4.dp))
            androidx.compose.material3.Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                color = BsColors.Mist,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsPlaybackSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit) {
    var showQualityDialog by rememberSaveable { mutableStateOf(false) }
    var showBufferDialog by rememberSaveable { mutableStateOf(false) }

    SettingsSectionScaffold(title = stringResource(R.string.player_settings), onBack = onBack) {
        item { BsSettingsSectionLabel(title = "Autoplay") }
        item {
            BsSettingsToggleRow(
                title = stringResource(R.string.settings_autoplay),
                checked = state.autoplay,
                onCheckedChange = { actions.onToggle("autoplay", it) },
            )
        }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_autoplay_buffer_title),
                valueLabel = "${state.autoplayBufferSec}s",
                onClick = { showBufferDialog = true },
            )
        }
        item { BsSettingsSectionLabel(title = "Buffering & quality") }
        item {
            BsSettingsToggleRow(
                title = stringResource(R.string.settings_force_extra_buffer_title),
                subtitle = stringResource(R.string.settings_force_extra_buffer_summary),
                checked = state.forceExtraBuffering,
                onCheckedChange = { actions.onToggle("forceExtraBuffering", it) },
            )
        }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_default_quality_title),
                subtitle = stringResource(R.string.settings_default_quality_summary),
                valueLabel = qualityLabel(state.qualityHeight),
                onClick = { showQualityDialog = true },
            )
        }
        item { BsSettingsSectionLabel(title = "Controls") }
        item {
            BsSettingsToggleRow(
                title = stringResource(R.string.settings_player_gestures),
                checked = state.playerGestures,
                onCheckedChange = { actions.onToggle("playerGestures", it) },
            )
        }
        item {
            BsSettingsToggleRow(
                title = stringResource(R.string.settings_keep_screen_on_when_paused),
                subtitle = stringResource(R.string.settings_keep_screen_on_when_paused_summary),
                checked = state.keepScreenOnWhenPaused,
                onCheckedChange = { actions.onToggle("keepScreenOnWhenPaused", it) },
            )
        }
        item {
            BsSettingsToggleRow(
                title = "Disable provider auto subtitles",
                subtitle = "Skip subtitles a server enables automatically",
                checked = state.serverAutoSubtitlesDisabled,
                onCheckedChange = { actions.onToggle("serverAutoSubtitlesDisabled", it) },
            )
        }
    }

    if (showQualityDialog) {
        BsSettingsChoiceDialog(
            title = stringResource(R.string.settings_default_quality_title),
            options = BsQualityOptions.map { it to qualityLabel(it) },
            selectedValue = state.qualityHeight,
            onSelect = {
                actions.onQualitySelected(it)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false },
        )
    }

    if (showBufferDialog) {
        BsSettingsTextFieldDialog(
            title = stringResource(R.string.settings_autoplay_buffer_title),
            subtitle = stringResource(R.string.settings_autoplay_buffer_dialog_message),
            initial = state.autoplayBufferSec,
            onConfirm = {
                actions.onEditText("autoplayBufferSec", it)
                showBufferDialog = false
            },
            onDismiss = { showBufferDialog = false },
        )
    }
}

@Composable
private fun SettingsAppearanceSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit) {
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }

    SettingsSectionScaffold(title = stringResource(R.string.settings_category_appearance), onBack = onBack) {
        item { BsSettingsSectionLabel(title = "Look & feel") }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_category_appearance),
                valueLabel = state.themeLabel,
                onClick = { showThemeDialog = true },
            )
        }
        item {
            BsSettingsToggleRow(
                title = stringResource(R.string.settings_immersive_mode),
                subtitle = stringResource(R.string.settings_immersive_mode_summary),
                checked = state.immersiveMode,
                onCheckedChange = { actions.onToggle("immersiveMode", it) },
            )
        }
        item { BsSettingsSectionLabel(title = "Language") }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_app_language_title),
                subtitle = stringResource(R.string.settings_app_language_summary),
                valueLabel = state.appLanguageLabel,
                onClick = { showLanguageDialog = true },
            )
        }
    }

    if (showThemeDialog) {
        BsSettingsChoiceDialog(
            title = stringResource(R.string.settings_category_appearance),
            options = BsThemeOptions.map { it to stringResourceSafe(ThemeManager.titleRes(it)) },
            selectedValue = state.themeId,
            onSelect = {
                actions.onThemeSelected(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showLanguageDialog) {
        BsSettingsChoiceDialog(
            title = stringResource(R.string.settings_app_language_title),
            options = BsLanguageOptions.map { it to languageLabel(it) },
            selectedValue = state.appLanguage,
            onSelect = {
                actions.onLanguageSelected(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
}

@Composable
private fun stringResourceSafe(resId: Int): String = stringResource(resId)

@Composable
private fun SettingsContentSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit) {
    var showTmdbDialog by rememberSaveable { mutableStateOf(false) }
    var showParentalPinDialog by rememberSaveable { mutableStateOf(false) }
    var showParentalAgeDialog by rememberSaveable { mutableStateOf(false) }

    SettingsSectionScaffold(title = stringResource(R.string.settings_section_content_title), onBack = onBack) {
        item { BsSettingsSectionLabel(title = "Metadata") }
        item {
            BsSettingsToggleRow(
                title = "Enable TMDb metadata",
                checked = state.enableTmdb,
                onCheckedChange = { actions.onToggle("enableTmdb", it) },
            )
        }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_tmdb_api_key),
                subtitle = stringResource(R.string.settings_tmdb_api_key_summary),
                valueLabel = state.tmdbApiKeyMasked,
                onClick = { showTmdbDialog = true },
            )
        }
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_category_parental_control)) }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_parental_pin_title),
                subtitle = if (state.hasParentalPin) {
                    stringResource(R.string.settings_parental_pin_set)
                } else {
                    stringResource(R.string.settings_parental_pin_not_set)
                },
                onClick = { showParentalPinDialog = true },
            )
        }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_parental_max_age_title),
                subtitle = stringResource(R.string.settings_parental_max_age_summary),
                valueLabel = state.parentalMaxAgeLabel,
                onClick = { showParentalAgeDialog = true },
            )
        }
    }

    if (showTmdbDialog) {
        BsSettingsTextFieldDialog(
            title = stringResource(R.string.settings_tmdb_api_key),
            subtitle = stringResource(R.string.settings_tmdb_api_key_summary),
            initial = "",
            isPassword = true,
            onConfirm = {
                actions.onEditText("tmdbApiKey", it)
                showTmdbDialog = false
            },
            onDismiss = { showTmdbDialog = false },
        )
    }

    if (showParentalPinDialog) {
        BsSettingsTextFieldDialog(
            title = stringResource(R.string.settings_parental_pin_title),
            subtitle = stringResource(R.string.settings_parental_pin_hint),
            initial = "",
            isPassword = true,
            onConfirm = {
                actions.onEditText("parentalPin", it)
                showParentalPinDialog = false
            },
            onDismiss = { showParentalPinDialog = false },
        )
    }

    if (showParentalAgeDialog) {
        val disabledLabel = stringResource(R.string.settings_parental_max_age_disabled)
        val ageOptions = listOf(
            disabledLabel to disabledLabel,
            "7+" to "7+",
            "13+" to "13+",
            "16+" to "16+",
            "18+" to "18+",
        )
        BsSettingsChoiceDialog(
            title = stringResource(R.string.settings_parental_max_age_title),
            options = ageOptions,
            selectedValue = state.parentalMaxAgeLabel,
            onSelect = {
                actions.onEditText("parentalMaxAge", it)
                showParentalAgeDialog = false
            },
            onDismiss = { showParentalAgeDialog = false },
        )
    }
}

@Composable
private fun SettingsNetworkSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit) {
    var showDohDialog by rememberSaveable { mutableStateOf(false) }
    var showSubdlDialog by rememberSaveable { mutableStateOf(false) }

    SettingsSectionScaffold(title = stringResource(R.string.settings_category_network_title), onBack = onBack) {
        item { BsSettingsSectionLabel(title = "DNS") }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_category_streamingcommunity_dnsOverHttps),
                valueLabel = state.dohLabel,
                onClick = { showDohDialog = true },
            )
        }
        item { BsSettingsSectionLabel(title = "TV bypass") }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_scan_resolver_qr_title),
                subtitle = stringResource(R.string.settings_scan_resolver_qr_summary),
                onClick = actions.onScanResolverQr,
            )
        }
        item { BsSettingsSectionLabel(title = "Subtitles") }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_subdl_api_key),
                subtitle = stringResource(R.string.settings_subdl_api_key_summary),
                valueLabel = state.subdlApiKeyMasked,
                onClick = { showSubdlDialog = true },
            )
        }
    }

    if (showDohDialog) {
        BsSettingsChoiceDialog(
            title = stringResource(R.string.settings_category_streamingcommunity_dnsOverHttps),
            options = BsDohOptions,
            selectedValue = state.dohValue,
            onSelect = {
                actions.onDohSelected(it)
                showDohDialog = false
            },
            onDismiss = { showDohDialog = false },
        )
    }

    if (showSubdlDialog) {
        BsSettingsTextFieldDialog(
            title = stringResource(R.string.settings_subdl_api_key),
            subtitle = stringResource(R.string.settings_subdl_api_key_summary),
            initial = "",
            isPassword = true,
            onConfirm = {
                actions.onEditText("subdlApiKey", it)
                showSubdlDialog = false
            },
            onDismiss = { showSubdlDialog = false },
        )
    }
}

@Composable
private fun SettingsProviderSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit) {
    var editingField by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsSectionScaffold(title = stringResource(R.string.settings_category_provider_title), onBack = onBack) {
        item { BsSettingsSectionLabel(title = state.providerName) }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_category_provider_url),
                valueLabel = state.providerUrl,
                onClick = { editingField = "providerUrl" },
            )
        }
        item {
            BsSettingsToggleRow(
                title = stringResource(R.string.settings_autoupdate),
                checked = state.providerAutoupdate,
                onCheckedChange = { actions.onToggle("providerAutoupdate", it) },
            )
        }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_autoupdate_now),
                onClick = { actions.onAction("refreshProviderUrl") },
            )
        }
        item { BsSettingsSectionLabel(title = "Domains") }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_category_streamingcommunity_domain),
                valueLabel = state.streamingcommunityDomain,
                onClick = { editingField = "streamingcommunityDomain" },
            )
        }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_streamingcommunity_domain_reset),
                subtitle = stringResource(R.string.settings_streamingcommunity_domain_reset_summary),
                onClick = { actions.onAction("resetStreamingcommunityDomain") },
            )
        }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_category_serienstream_domain),
                valueLabel = state.serienstreamDomain,
                onClick = { editingField = "serienstreamDomain" },
            )
        }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_serienstream_domain_reset),
                subtitle = stringResource(R.string.settings_serienstream_domain_reset_summary),
                onClick = { actions.onAction("resetSerienstreamDomain") },
            )
        }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_category_aniworld_domain),
                valueLabel = state.aniworldDomain,
                onClick = { editingField = "aniworldDomain" },
            )
        }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_aniworld_domain_reset),
                subtitle = stringResource(R.string.settings_aniworld_domain_reset_summary),
                onClick = { actions.onAction("resetAniworldDomain") },
            )
        }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_category_moflix_domain),
                valueLabel = state.moflixDomain,
                onClick = { editingField = "moflixDomain" },
            )
        }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_moflix_domain_reset),
                subtitle = stringResource(R.string.settings_moflix_domain_reset_summary),
                onClick = { actions.onAction("resetMoflixDomain") },
            )
        }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_category_cuevana_domain),
                valueLabel = state.cuevanaDomain,
                onClick = { editingField = "cuevanaDomain" },
            )
        }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_cuevana_domain_reset),
                subtitle = stringResource(R.string.settings_cuevana_domain_reset_summary),
                onClick = { actions.onAction("resetCuevanaDomain") },
            )
        }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_category_poseidon_domain),
                valueLabel = state.poseidonDomain,
                onClick = { editingField = "poseidonDomain" },
            )
        }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_poseidon_domain_reset),
                subtitle = stringResource(R.string.settings_poseidon_domain_reset_summary),
                onClick = { actions.onAction("resetPoseidonDomain") },
            )
        }
    }

    val field = editingField
    if (field != null) {
        val title = when (field) {
            "providerUrl" -> stringResource(R.string.settings_category_provider_url)
            "streamingcommunityDomain" -> stringResource(R.string.settings_category_streamingcommunity_domain)
            "serienstreamDomain" -> stringResource(R.string.settings_category_serienstream_domain)
            "aniworldDomain" -> stringResource(R.string.settings_category_aniworld_domain)
            "moflixDomain" -> stringResource(R.string.settings_category_moflix_domain)
            "cuevanaDomain" -> stringResource(R.string.settings_category_cuevana_domain)
            "poseidonDomain" -> stringResource(R.string.settings_category_poseidon_domain)
            else -> field
        }
        val initial = when (field) {
            "providerUrl" -> state.providerUrl
            "streamingcommunityDomain" -> state.streamingcommunityDomain
            "serienstreamDomain" -> state.serienstreamDomain
            "aniworldDomain" -> state.aniworldDomain
            "moflixDomain" -> state.moflixDomain
            "cuevanaDomain" -> state.cuevanaDomain
            "poseidonDomain" -> state.poseidonDomain
            else -> ""
        }
        BsSettingsTextFieldDialog(
            title = title,
            initial = initial,
            onConfirm = {
                actions.onEditText(field, it)
                editingField = null
            },
            onDismiss = { editingField = null },
        )
    }
}

@Composable
private fun SettingsCloudSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit) {
    SettingsSectionScaffold(title = stringResource(R.string.cloud_sync_title), onBack = onBack) {
        item { BsSettingsSectionLabel(title = stringResource(R.string.trakt_settings_title)) }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.trakt_enabled_title),
                subtitle = stringResource(R.string.trakt_enabled_summary),
                onClick = { actions.onAction("openTraktSync") },
            )
        }
        item { BsSettingsSectionLabel(title = "Maintenance") }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_refresh_cache_title),
                subtitle = stringResource(R.string.settings_refresh_cache_summary),
                onClick = actions.onRefresh,
            )
        }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.loading_error_clear_cache),
                destructive = true,
                onClick = actions.onClearCache,
            )
        }
    }
}

@Composable
private fun SettingsBackupSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit) {
    SettingsSectionScaffold(title = stringResource(R.string.backup_category_title), onBack = onBack) {
        item { BsSettingsSectionLabel(title = "User data") }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.backup_export_title),
                subtitle = stringResource(R.string.backup_export_summary),
                onClick = actions.onExportBackup,
            )
        }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.backup_import_title),
                subtitle = stringResource(R.string.backup_import_summary),
                onClick = actions.onImportBackup,
            )
        }
        item { BsSettingsSectionLabel(title = "Full database") }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.backup_db_export_title),
                subtitle = stringResource(R.string.backup_db_export_summary),
                onClick = actions.onExportDb,
            )
        }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.backup_db_import_title),
                subtitle = stringResource(R.string.backup_db_import_summary),
                onClick = actions.onImportDb,
            )
        }
    }
}

@Composable
private fun SettingsAboutSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit) {
    SettingsSectionScaffold(title = stringResource(R.string.settings_about), onBack = onBack) {
        item { BsSettingsSectionLabel(title = "BetterStreamflix") }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_about_version_name, state.versionName),
                onClick = actions.onOpenAbout,
            )
        }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_help),
                onClick = actions.onOpenHelp,
            )
        }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_telegram),
                onClick = actions.onOpenTelegram,
            )
        }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_about_share_diagnostics),
                subtitle = stringResource(R.string.settings_about_share_diagnostics_summary),
                onClick = { actions.onAction("shareDiagnostics") },
            )
        }
    }
}
