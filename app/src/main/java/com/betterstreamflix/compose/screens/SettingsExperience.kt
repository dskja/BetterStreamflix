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
import androidx.compose.ui.platform.LocalContext
import com.betterstreamflix.accessibility.AccessibilityHelper
import com.betterstreamflix.accessibility.ReducedMotionHelper
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.utils.ParentalPinLogic
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
    val hasAdminPin: Boolean,
    val parentalLocked: Boolean,
    val parentalSessionLabel: String,
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
    val downloadNotifications: Boolean,
    val playbackNotifications: Boolean,
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

private val BsLanguageOptions = listOf("system", "en", "de", "it", "fr", "es", "ar", "pl")

private val BsDohOptionUrls = listOf(
    "",
    "https://dns.google/dns-query",
    "https://cloudflare-dns.com/dns-query",
    "https://doh.opendns.com/dns-query",
    "https://dns.quad9.net/dns-query",
    "https://doh.cleanbrowsing.org/doh/family-filter/",
    "https://dns.adguard-dns.com/dns-query",
    "https://unfiltered.joindns4.eu/dns-query",
    "https://ns0.fdn.fr/dns-query",
)

@Composable
private fun dohLabel(url: String): String = when (url) {
    "" -> stringResource(R.string.settings_doh_none)
    "https://dns.google/dns-query" -> "Google"
    "https://cloudflare-dns.com/dns-query" -> "Cloudflare"
    "https://doh.opendns.com/dns-query" -> "OpenDNS"
    "https://dns.quad9.net/dns-query" -> "Quad9"
    "https://doh.cleanbrowsing.org/doh/family-filter/" -> "CleanBrowsing"
    "https://dns.adguard-dns.com/dns-query" -> "AdGuard"
    "https://unfiltered.joindns4.eu/dns-query" -> "DNS4EU"
    "https://ns0.fdn.fr/dns-query" -> "FDN"
    else -> url
}

@Composable
private fun languageLabel(code: String): String = when (code) {
    "system", "" -> stringResource(R.string.settings_language_system_default)
    else -> {
        val locale = Locale(code)
        locale.getDisplayName(locale).replaceFirstChar { c -> c.uppercaseChar() }
    }
}

@Composable
private fun qualityLabel(height: String): String =
    if (height.isBlank()) stringResource(R.string.settings_quality_auto) else "${height}p"

@Composable
fun SettingsExperience(
    destination: SettingsDestination,
    onNavigate: (SettingsDestination) -> Unit,
    state: SettingsUiState,
    actions: SettingsActions,
    isTv: Boolean = false,
) {
    val horizontalPadding = if (isTv) 32.dp else 20.dp
    BsAtmosphere {
        when (destination) {
            SettingsDestination.Hub -> SettingsHubBody(
                onNavigate = onNavigate,
                state = state,
                actions = actions,
                horizontalPadding = horizontalPadding,
            )
            SettingsDestination.Content -> SettingsContentSection(state = state, actions = actions, onBack = actions.onBack, horizontalPadding = horizontalPadding)
            SettingsDestination.Playback -> SettingsPlaybackSection(state = state, actions = actions, onBack = actions.onBack, horizontalPadding = horizontalPadding)
            SettingsDestination.Appearance -> SettingsAppearanceSection(state = state, actions = actions, onBack = actions.onBack, horizontalPadding = horizontalPadding)
            SettingsDestination.Network -> SettingsNetworkSection(state = state, actions = actions, onBack = actions.onBack, horizontalPadding = horizontalPadding)
            SettingsDestination.Provider -> SettingsProviderSection(state = state, actions = actions, onBack = actions.onBack, horizontalPadding = horizontalPadding)
            SettingsDestination.Cloud -> SettingsCloudSection(state = state, actions = actions, onBack = actions.onBack, horizontalPadding = horizontalPadding)
            SettingsDestination.Backup -> SettingsBackupSection(state = state, actions = actions, onBack = actions.onBack, horizontalPadding = horizontalPadding)
            SettingsDestination.About -> SettingsAboutSection(state = state, actions = actions, onBack = actions.onBack, horizontalPadding = horizontalPadding)
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
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
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
            subtitle = stringResource(R.string.settings_hub_playback_summary),
            onClick = { onNavigate(SettingsDestination.Playback) },
        ),
        HubEntry(
            title = stringResource(R.string.settings_category_appearance),
            subtitle = stringResource(R.string.settings_hub_appearance_summary),
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
            subtitle = stringResource(R.string.settings_hub_cloud_summary),
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
        com.betterstreamflix.compose.components.BsTopBar(
            title = stringResource(R.string.main_menu_settings),
            showBrand = true,
            subtitle = stringResource(R.string.settings_about_version_name, state.versionName),
            horizontalPadding = horizontalPadding,
            modifier = Modifier.alpha(headerAlpha),
        )

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
                BsSettingsSectionLabel(title = stringResource(R.string.settings_quick_access))
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
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        com.betterstreamflix.compose.components.BsTopBar(
            title = title,
            onBack = onBack,
            horizontalPadding = horizontalPadding,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsPlaybackSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit, horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp) {
    var showQualityDialog by rememberSaveable { mutableStateOf(false) }
    var showBufferDialog by rememberSaveable { mutableStateOf(false) }

    SettingsSectionScaffold(title = stringResource(R.string.player_settings), onBack = onBack, horizontalPadding = horizontalPadding) {
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_section_autoplay)) }
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
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_section_buffering_quality)) }
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
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_section_controls)) }
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
                title = stringResource(R.string.settings_disable_auto_subtitles_title),
                subtitle = stringResource(R.string.settings_disable_auto_subtitles_summary),
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
private fun SettingsAppearanceSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit, horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp) {
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val reducedMotionOn = ReducedMotionHelper.isReducedMotion(context)
    val fontScale = AccessibilityHelper.getFontScale(context)

    SettingsSectionScaffold(title = stringResource(R.string.settings_category_appearance), onBack = onBack, horizontalPadding = horizontalPadding) {
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_theme_gallery_title), horizontalPadding = horizontalPadding) }
        item {
            androidx.compose.material3.Text(
                text = stringResource(R.string.settings_theme_gallery_subtitle),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = BsColors.MistDim,
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 4.dp),
            )
        }
        item {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = horizontalPadding,
                    vertical = 10.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(BsThemeOptions.size) { index ->
                    val themeId = BsThemeOptions[index]
                    val palette = ThemeManager.palette(themeId)
                    com.betterstreamflix.compose.components.BsThemeGalleryCard(
                        title = stringResourceSafe(ThemeManager.titleRes(themeId)),
                        selected = state.themeId == themeId,
                        accent = androidx.compose.ui.graphics.Color(palette.mobileNavActive),
                        canvas = androidx.compose.ui.graphics.Color(palette.mobileNavBackground),
                        soft = androidx.compose.ui.graphics.Color(palette.tvHeaderSecondary),
                        onClick = { actions.onThemeSelected(themeId) },
                    )
                }
            }
        }
        item {
            com.betterstreamflix.compose.components.BsSettingsFeatureCard(
                title = stringResource(R.string.settings_immersive_mode),
                subtitle = stringResource(R.string.settings_immersive_mode_summary),
                checked = state.immersiveMode,
                onCheckedChange = { actions.onToggle("immersiveMode", it) },
                horizontalPadding = horizontalPadding,
            )
        }
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_section_language), horizontalPadding = horizontalPadding) }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_app_language_title),
                subtitle = stringResource(R.string.settings_app_language_summary),
                valueLabel = state.appLanguageLabel,
                onClick = { showLanguageDialog = true },
            )
        }
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_section_accessibility), horizontalPadding = horizontalPadding) }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_reduced_motion_title),
                subtitle = if (reducedMotionOn) {
                    stringResource(R.string.settings_reduced_motion_on)
                } else {
                    stringResource(R.string.settings_reduced_motion_off)
                },
                valueLabel = if (reducedMotionOn) {
                    stringResource(R.string.settings_autoplay_on)
                } else {
                    stringResource(R.string.settings_autoplay_off)
                },
                onClick = { actions.onAction("openAccessibilitySettings") },
            )
        }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_font_scale_title),
                subtitle = stringResource(R.string.settings_font_scale_open_summary),
                valueLabel = String.format(Locale.getDefault(), "%.2f", fontScale),
                onClick = { actions.onAction("openDisplaySettings") },
            )
        }
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
private fun SettingsContentSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit, horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp) {
    var showTmdbDialog by rememberSaveable { mutableStateOf(false) }
    var parentalPinStep by rememberSaveable { mutableStateOf<String?>(null) }
    var showParentalAgeDialog by rememberSaveable { mutableStateOf(false) }
    var parentalError by remember { mutableStateOf<String?>(null) }

    val hardLockedMsg = stringResource(R.string.settings_parental_locked_hard)
    val tempLockedMsg = stringResource(
        R.string.settings_parental_locked_temporary,
        ParentalPinLogic.lockRemainingMinutes(),
    )
    val invalidPinMsg = stringResource(R.string.settings_parental_invalid_pin)
    val invalidAdminMsg = stringResource(R.string.settings_parental_invalid_admin_pin)
    val tooShortMsg = stringResource(R.string.settings_parental_pin_too_short)
    val setPinFirstMsg = stringResource(R.string.settings_parental_set_pin_first)
    val requiresTmdbMsg = stringResource(R.string.settings_parental_requires_tmdb)
    val noAdminMsg = stringResource(R.string.settings_parental_set_admin_pin_first)

    fun resolveParentalError(code: String?): String? = when (code) {
        null -> null
        "HARD_LOCKED" -> hardLockedMsg
        "TEMP_LOCKED" -> tempLockedMsg
        "INVALID" -> invalidPinMsg
        "INVALID_ADMIN" -> invalidAdminMsg
        "TOO_SHORT" -> tooShortMsg
        "SET_PIN_FIRST" -> setPinFirstMsg
        "REQUIRES_TMDB" -> requiresTmdbMsg
        "NO_ADMIN" -> noAdminMsg
        else -> code
    }

    SettingsSectionScaffold(title = stringResource(R.string.settings_section_content_title), onBack = onBack, horizontalPadding = horizontalPadding) {
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_metadata_section)) }
        item {
            BsSettingsToggleRow(
                title = stringResource(R.string.settings_enable_tmdb_title),
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
                title = stringResource(R.string.settings_parental_session_title),
                subtitle = state.parentalSessionLabel,
                onClick = {},
            )
        }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_parental_pin_title),
                subtitle = if (state.hasParentalPin) {
                    stringResource(R.string.settings_parental_pin_set)
                } else {
                    stringResource(R.string.settings_parental_pin_not_set)
                },
                onClick = {
                    parentalError = null
                    parentalPinStep = if (state.hasParentalPin) "verify" else "set"
                },
            )
        }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_parental_admin_pin_title),
                subtitle = if (state.hasAdminPin) {
                    stringResource(R.string.settings_parental_admin_pin_set)
                } else {
                    stringResource(R.string.settings_parental_admin_pin_not_set)
                },
                onClick = {
                    parentalError = null
                    parentalPinStep = if (state.hasAdminPin) "adminVerify" else "adminSet"
                },
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
        if (state.parentalLocked) {
            item {
                BsSettingsActionRow(
                    title = stringResource(R.string.settings_parental_unlock_title),
                    subtitle = stringResource(R.string.settings_parental_unlock_summary),
                    onClick = {
                        parentalError = null
                        parentalPinStep = "unlock"
                    },
                )
            }
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

    when (parentalPinStep) {
        "verify" -> BsSettingsTextFieldDialog(
            title = stringResource(R.string.settings_parental_enter_current_pin_title),
            subtitle = parentalError
                ?: stringResource(R.string.settings_parental_enter_current_pin_message),
            initial = "",
            isPassword = true,
            onConfirm = {
                val err = resolveParentalError(ParentalPinLogic.verifyCurrentPin(it))
                if (err == null) {
                    parentalError = null
                    parentalPinStep = "set"
                    actions.onRefresh()
                } else {
                    parentalError = err
                    actions.onRefresh()
                }
            },
            onDismiss = { parentalPinStep = null },
        )
        "set" -> BsSettingsTextFieldDialog(
            title = stringResource(R.string.settings_parental_pin_title),
            subtitle = parentalError
                ?: stringResource(
                    if (state.hasParentalPin) R.string.settings_parental_change_pin_message
                    else R.string.settings_parental_set_new_pin_message,
                ),
            initial = "",
            isPassword = true,
            onConfirm = {
                val err = resolveParentalError(ParentalPinLogic.setParentalPin(it))
                if (err == null) {
                    parentalPinStep = null
                    parentalError = null
                    actions.onRefresh()
                } else {
                    parentalError = err
                }
            },
            onDismiss = { parentalPinStep = null },
        )
        "adminVerify" -> BsSettingsTextFieldDialog(
            title = stringResource(R.string.settings_parental_enter_admin_pin_title),
            subtitle = parentalError
                ?: stringResource(R.string.settings_parental_enter_admin_pin_message),
            initial = "",
            isPassword = true,
            onConfirm = {
                val err = resolveParentalError(ParentalPinLogic.verifyAdminPin(it))
                if (err == null) {
                    parentalError = null
                    parentalPinStep = "adminSet"
                    actions.onRefresh()
                } else {
                    parentalError = err
                }
            },
            onDismiss = { parentalPinStep = null },
        )
        "adminSet" -> BsSettingsTextFieldDialog(
            title = stringResource(R.string.settings_parental_admin_pin_title),
            subtitle = parentalError
                ?: stringResource(
                    if (state.hasAdminPin) R.string.settings_parental_change_admin_pin_message
                    else R.string.settings_parental_set_new_admin_pin_message,
                ),
            initial = "",
            isPassword = true,
            onConfirm = {
                val err = resolveParentalError(ParentalPinLogic.setAdminPin(it))
                if (err == null) {
                    parentalPinStep = null
                    parentalError = null
                    actions.onRefresh()
                } else {
                    parentalError = err
                }
            },
            onDismiss = { parentalPinStep = null },
        )
        "unlock" -> BsSettingsTextFieldDialog(
            title = stringResource(R.string.settings_parental_unlock_title),
            subtitle = parentalError
                ?: stringResource(R.string.settings_parental_enter_admin_pin_message),
            initial = "",
            isPassword = true,
            onConfirm = {
                val err = resolveParentalError(ParentalPinLogic.verifyAdminPin(it))
                if (err == null) {
                    parentalPinStep = null
                    parentalError = null
                    actions.onRefresh()
                } else {
                    parentalError = err
                }
            },
            onDismiss = { parentalPinStep = null },
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
                ParentalPinLogic.setMaxAge(it)
                showParentalAgeDialog = false
                actions.onRefresh()
            },
            onDismiss = { showParentalAgeDialog = false },
        )
    }
}

@Composable
private fun SettingsNetworkSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit, horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp) {
    var showDohDialog by rememberSaveable { mutableStateOf(false) }
    var showSubdlDialog by rememberSaveable { mutableStateOf(false) }

    SettingsSectionScaffold(title = stringResource(R.string.settings_category_network_title), onBack = onBack, horizontalPadding = horizontalPadding) {
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_dns_section)) }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_category_streamingcommunity_dnsOverHttps),
                valueLabel = state.dohLabel,
                onClick = { showDohDialog = true },
            )
        }
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_tv_bypass_section)) }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.settings_scan_resolver_qr_title),
                subtitle = stringResource(R.string.settings_scan_resolver_qr_summary),
                onClick = actions.onScanResolverQr,
            )
        }
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_section_subtitles)) }
        item {
            BsSettingsValueRow(
                title = stringResource(R.string.settings_subdl_api_key),
                subtitle = stringResource(R.string.settings_subdl_api_key_summary),
                valueLabel = state.subdlApiKeyMasked,
                onClick = { showSubdlDialog = true },
            )
        }
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_section_notifications)) }
        item {
            BsSettingsToggleRow(
                title = stringResource(R.string.settings_download_notifications),
                subtitle = stringResource(R.string.settings_download_notifications_summary),
                checked = state.downloadNotifications,
                onCheckedChange = { actions.onToggle("downloadNotifications", it) },
            )
        }
        item {
            BsSettingsToggleRow(
                title = stringResource(R.string.settings_playback_notifications),
                subtitle = stringResource(R.string.settings_playback_notifications_summary),
                checked = state.playbackNotifications,
                onCheckedChange = { actions.onToggle("playbackNotifications", it) },
            )
        }
    }

    if (showDohDialog) {
        BsSettingsChoiceDialog(
            title = stringResource(R.string.settings_category_streamingcommunity_dnsOverHttps),
            options = BsDohOptionUrls.map { it to dohLabel(it) },
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
private fun SettingsProviderSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit, horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp) {
    var editingField by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsSectionScaffold(title = stringResource(R.string.settings_category_provider_title), onBack = onBack, horizontalPadding = horizontalPadding) {
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
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_section_domains)) }
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
private fun SettingsCloudSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit, horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp) {
    SettingsSectionScaffold(title = stringResource(R.string.cloud_sync_title), onBack = onBack, horizontalPadding = horizontalPadding) {
        item { BsSettingsSectionLabel(title = stringResource(R.string.trakt_settings_title)) }
        item {
            BsSettingsActionRow(
                title = stringResource(R.string.trakt_enabled_title),
                subtitle = stringResource(R.string.trakt_enabled_summary),
                onClick = { actions.onAction("openTraktSync") },
            )
        }
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_section_maintenance)) }
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
private fun SettingsBackupSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit, horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp) {
    SettingsSectionScaffold(title = stringResource(R.string.backup_category_title), onBack = onBack, horizontalPadding = horizontalPadding) {
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_section_user_data)) }
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
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_section_full_database)) }
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
private fun SettingsAboutSection(state: SettingsUiState, actions: SettingsActions, onBack: () -> Unit, horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp) {
    SettingsSectionScaffold(title = stringResource(R.string.settings_about), onBack = onBack, horizontalPadding = horizontalPadding) {
        item { BsSettingsSectionLabel(title = stringResource(R.string.settings_section_about_brand)) }
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
