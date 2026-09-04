package com.betterstreamflix.fragments.settings

import android.content.Context
import com.betterstreamflix.BuildConfig
import com.betterstreamflix.R
import com.betterstreamflix.compose.screens.SettingsUiState
import com.betterstreamflix.notifications.NotificationPreferences
import com.betterstreamflix.providers.ProviderConfigUrl
import com.betterstreamflix.sync.CloudAccountStore
import com.betterstreamflix.sync.CloudSyncManager
import com.betterstreamflix.sync.SupabaseProvider
import com.betterstreamflix.sync.TraktSettings
import com.betterstreamflix.utils.AppLanguageManager
import com.betterstreamflix.utils.ParentalPinLogic
import com.betterstreamflix.utils.ThemeManager
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.UserProfiles
import java.text.DateFormat
import java.util.Date

internal object SettingsComposeBridge {

    private val dohLabels = mapOf(
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

    fun buildState(context: Context): SettingsUiState {
        val themeId = UserPreferences.selectedTheme
        val language = AppLanguageManager.getSelectedLanguage(context)
        val quality = UserPreferences.qualityHeight?.toString().orEmpty()
        val doh = UserPreferences.dohProviderUrl
        val maxAge = UserPreferences.parentalControlMaxAge
        val provider = UserPreferences.currentProvider
        return SettingsUiState(
            autoplay = UserPreferences.autoplay,
            autoplayBufferSec = UserPreferences.autoplayBuffer.toString(),
            forceExtraBuffering = UserPreferences.forceExtraBuffering,
            playerGestures = UserPreferences.playerGestures,
            keepScreenOnWhenPaused = UserPreferences.keepScreenOnWhenPaused,
            qualityHeight = quality,
            serverAutoSubtitlesDisabled = UserPreferences.serverAutoSubtitlesDisabled,
            themeId = themeId,
            themeLabel = context.getString(ThemeManager.titleRes(themeId)),
            appLanguage = language,
            appLanguageLabel = languageLabel(context, language),
            immersiveMode = UserPreferences.immersiveMode,
            enableTmdb = UserPreferences.enableTmdb,
            tmdbApiKeyMasked = maskSecret(UserPreferences.tmdbApiKey),
            parentalMaxAgeLabel = maxAge?.let { "$it+" } ?: context.getString(R.string.settings_parental_max_age_disabled),
            hasParentalPin = UserPreferences.parentalControlPin.isNotBlank(),
            hasAdminPin = UserPreferences.parentalControlAdminPin.isNotBlank(),
            parentalLocked = ParentalPinLogic.isLocked(),
            parentalSessionLabel = when {
                !UserPreferences.isParentalControlActive ->
                    context.getString(R.string.settings_parental_session_inactive)
                UserPreferences.parentalControlHardLocked ->
                    context.getString(R.string.settings_parental_locked_hard)
                UserPreferences.isParentalControlTemporarilyLocked ->
                    context.getString(
                        R.string.settings_parental_locked_temporary,
                        ParentalPinLogic.lockRemainingMinutes(),
                    )
                else -> context.getString(R.string.settings_parental_session_active)
            },
            dohLabel = dohLabels[doh] ?: doh.ifBlank { "None" },
            dohValue = doh,
            subdlApiKeyMasked = maskSecret(UserPreferences.subdlApiKey),
            providerName = provider?.name ?: "—",
            providerUrl = when {
                UserPreferences.providerUrl.isNotBlank() -> UserPreferences.providerUrl
                provider is ProviderConfigUrl -> provider.defaultBaseUrl
                provider != null -> provider.baseUrl
                else -> ""
            },
            providerAutoupdate = provider?.let {
                UserPreferences.getProviderCache(it, UserPreferences.PROVIDER_AUTOUPDATE)
                    .ifBlank { "true" } != "false"
            } ?: true,
            streamingcommunityDomain = UserPreferences.streamingcommunityDomain,
            serienstreamDomain = UserPreferences.serienstreamDomain,
            aniworldDomain = UserPreferences.aniworldDomain,
            moflixDomain = UserPreferences.moflixDomain,
            cuevanaDomain = UserPreferences.cuevanaDomain,
            poseidonDomain = UserPreferences.poseidonDomain,
            updateCheckEnabled = UserPreferences.updateCheckEnabled,
            newContentNotifications = NotificationPreferences.isNewContentNotificationsEnabled(context),
            downloadNotifications = NotificationPreferences.isDownloadNotificationsEnabled(context),
            playbackNotifications = NotificationPreferences.isPlaybackNotificationsEnabled(context),
            versionName = BuildConfig.VERSION_NAME,
            traktEnabled = TraktSettings.isEnabled(context),
            cloudConfigured = SupabaseProvider.isConfigured,
            cloudSignedIn = CloudSyncManager.currentUserEmail(UserProfiles.active().id) != null ||
                CloudAccountStore.activeUserEmail(context, UserProfiles.active().id) != null,
            cloudStatusLabel = cloudStatusLabel(context),
        )
    }

    private fun cloudStatusLabel(context: Context): String {
        val profile = UserProfiles.active()
        val profileLine = context.getString(R.string.cloud_sync_profile_line, profile.name)
        if (!SupabaseProvider.isConfigured) {
            return profileLine + "\n" + context.getString(R.string.cloud_sync_signed_out)
        }
        val email = CloudSyncManager.currentUserEmail(profile.id)
            ?: CloudAccountStore.activeUserEmail(context, profile.id)
        if (email == null) {
            return profileLine + "\n" + context.getString(R.string.cloud_sync_signed_out)
        }
        val lastSynced = CloudSyncManager.lastSyncedAtMillis(context, profile.id)
        val accountLine = if (lastSynced > 0L) {
            val formatted = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
            ).format(Date(lastSynced))
            context.getString(R.string.cloud_sync_signed_in_as, email) +
                "\n" + context.getString(R.string.sync_last_synced, formatted)
        } else {
            context.getString(R.string.cloud_sync_signed_in_as, email) +
                "\n" + context.getString(R.string.sync_status_ok)
        }
        return profileLine + "\n" + accountLine
    }

    fun applyToggle(context: Context, key: String, value: Boolean) {
        when (key) {
            "autoplay" -> UserPreferences.autoplay = value
            "forceExtraBuffering" -> UserPreferences.forceExtraBuffering = value
            "playerGestures" -> UserPreferences.playerGestures = value
            "keepScreenOnWhenPaused" -> UserPreferences.keepScreenOnWhenPaused = value
            "serverAutoSubtitlesDisabled" -> UserPreferences.serverAutoSubtitlesDisabled = value
            "immersiveMode" -> UserPreferences.immersiveMode = value
            "enableTmdb" -> UserPreferences.enableTmdb = value
            "updateCheckEnabled" -> UserPreferences.updateCheckEnabled = value
            "newContentNotifications" -> NotificationPreferences.setNewContentNotificationsEnabled(context, value)
            "downloadNotifications" -> NotificationPreferences.setDownloadNotificationsEnabled(context, value)
            "playbackNotifications" -> NotificationPreferences.setPlaybackNotificationsEnabled(context, value)
            "providerAutoupdate" -> {
                val provider = UserPreferences.currentProvider ?: return
                UserPreferences.setProviderCache(
                    provider,
                    UserPreferences.PROVIDER_AUTOUPDATE,
                    value.toString(),
                )
            }
            "traktEnabled" -> TraktSettings.setEnabled(context, value)
        }
    }

    fun applyEditText(key: String, value: String): String? {
        when (key) {
            "autoplayBufferSec" -> UserPreferences.autoplayBuffer = value.toLongOrNull()?.coerceAtLeast(0L) ?: 3L
            "tmdbApiKey" -> UserPreferences.tmdbApiKey = value.trim()
            "subdlApiKey" -> UserPreferences.subdlApiKey = value.trim()
            "parentalPin" -> return ParentalPinLogic.setParentalPin(value)
            "parentalVerifyPin" -> return ParentalPinLogic.verifyCurrentPin(value)
            "parentalAdminPin" -> return ParentalPinLogic.setAdminPin(value)
            "parentalVerifyAdminPin" -> return ParentalPinLogic.verifyAdminPin(value)
            "parentalMaxAge" -> return ParentalPinLogic.setMaxAge(value)
            "providerUrl" -> UserPreferences.providerUrl = value.trim()
            "streamingcommunityDomain", "editStreamingcommunityDomain" ->
                UserPreferences.streamingcommunityDomain = value.trim()
            "serienstreamDomain", "editSerienstreamDomain" ->
                UserPreferences.serienstreamDomain = value.trim()
            "aniworldDomain", "editAniworldDomain" ->
                UserPreferences.aniworldDomain = value.trim()
            "moflixDomain", "editMoflixDomain" ->
                UserPreferences.moflixDomain = value.trim()
            "cuevanaDomain", "editCuevanaDomain" ->
                UserPreferences.cuevanaDomain = value.trim()
            "poseidonDomain", "editPoseidonDomain" ->
                UserPreferences.poseidonDomain = value.trim()
        }
        return null
    }

    fun applyAction(key: String) {
        when (key) {
            "resetStreamingcommunityDomain" -> UserPreferences.streamingcommunityDomain = "streamingunity.cc"
            "resetSerienstreamDomain" -> UserPreferences.serienstreamDomain = "186.2.175.5"
            "resetAniworldDomain" -> UserPreferences.aniworldDomain = "aniworld.to"
            "resetMoflixDomain" -> UserPreferences.moflixDomain = "moflix-stream.xyz"
            "resetCuevanaDomain" -> UserPreferences.cuevanaDomain = "cuevana3.eu"
            "resetPoseidonDomain" -> UserPreferences.poseidonDomain = "poseidonhd2.com"
            "refreshProviderUrl" -> {
                val provider = UserPreferences.currentProvider
                when {
                    provider is ProviderConfigUrl -> UserPreferences.providerUrl = provider.defaultBaseUrl
                    provider != null -> UserPreferences.providerUrl = provider.baseUrl
                }
            }
        }
    }

    fun maskSecret(value: String): String {
        if (value.isBlank()) return "—"
        if (value.length <= 4) return "••••"
        return "••••" + value.takeLast(4)
    }

    private fun languageLabel(context: Context, code: String): String {
        val values = AppLanguageManager.buildLanguageValues(context)
        val entries = AppLanguageManager.buildLanguageEntries(context)
        val index = values.indexOf(code)
        return entries.getOrNull(index)?.toString()
            ?: context.getString(R.string.settings_app_language_system)
    }
}
