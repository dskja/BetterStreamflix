package com.dskja.betterstreamflix.platform

import android.content.Context
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.platform.debrid.DebridProviderId
import com.dskja.betterstreamflix.platform.debrid.DebridResolver
import com.dskja.betterstreamflix.platform.playerbackend.ExternalMpvBackend
import com.dskja.betterstreamflix.platform.playerbackend.PlayerBackendKind
import com.dskja.betterstreamflix.platform.playerbackend.PlayerBackendSelector
import com.dskja.betterstreamflix.platform.plugins.PluginRegistry
import com.dskja.betterstreamflix.platform.simkl.SimklConfig
import com.dskja.betterstreamflix.platform.subtitles.OpenSubtitlesV1Client
import com.dskja.betterstreamflix.platform.trakt.TraktConfig
import com.dskja.betterstreamflix.utils.UserPreferences

/**
 * Live connection snapshots for the Integrations hub and preference screens.
 * Pure preference reads — no network. Pair with [IntegrationProbes] for live tests.
 */
object IntegrationStatus {

    enum class Level {
        UNAVAILABLE,
        DISABLED,
        NOT_CONFIGURED,
        READY,
        SIGNED_IN,
    }

    data class Snapshot(
        val level: Level,
        /** Optional short detail (username, provider id, package name). */
        val detail: String? = null,
    ) {
        val isHealthy: Boolean get() = level == Level.READY || level == Level.SIGNED_IN
    }

    fun trakt(): Snapshot = when {
        !TraktConfig.hasAppCredentials() -> Snapshot(Level.UNAVAILABLE)
        !UserPreferences.traktEnabled -> Snapshot(Level.DISABLED)
        TraktConfig.isSignedIn() -> Snapshot(Level.SIGNED_IN)
        else -> Snapshot(Level.NOT_CONFIGURED)
    }

    fun jellyfin(): Snapshot {
        val url = UserPreferences.jellyfinBaseUrl.trim()
        val token = UserPreferences.jellyfinAccessToken.trim()
        val user = UserPreferences.jellyfinUserId.trim()
        return when {
            url.isBlank() -> Snapshot(Level.NOT_CONFIGURED)
            token.isBlank() || user.isBlank() -> Snapshot(Level.NOT_CONFIGURED, hostOf(url))
            else -> Snapshot(Level.SIGNED_IN, hostOf(url))
        }
    }

    fun plex(): Snapshot {
        val url = UserPreferences.plexBaseUrl.trim()
        val token = UserPreferences.plexToken.trim()
        return when {
            url.isBlank() -> Snapshot(Level.NOT_CONFIGURED)
            token.isBlank() -> Snapshot(Level.NOT_CONFIGURED, hostOf(url))
            else -> Snapshot(Level.SIGNED_IN, hostOf(url))
        }
    }

    fun debrid(): Snapshot {
        if (!UserPreferences.debridEnabled) return Snapshot(Level.DISABLED)
        val provider = DebridProviderId.fromId(UserPreferences.debridProvider)
        return if (DebridResolver.activeService() != null) {
            Snapshot(Level.READY, provider.displayName)
        } else {
            Snapshot(Level.NOT_CONFIGURED, provider.displayName)
        }
    }

    fun simkl(): Snapshot = when {
        !UserPreferences.simklEnabled -> Snapshot(Level.DISABLED)
        SimklConfig.configured() -> Snapshot(Level.SIGNED_IN)
        SimklConfig.clientId().isBlank() -> Snapshot(Level.NOT_CONFIGURED)
        else -> Snapshot(Level.NOT_CONFIGURED)
    }

    fun openSubtitles(): Snapshot {
        if (!OpenSubtitlesV1Client.configured()) return Snapshot(Level.NOT_CONFIGURED)
        val jwt = runCatching { UserPreferences.openSubtitlesJwt.isNotBlank() }.getOrDefault(false)
        return if (jwt) Snapshot(Level.SIGNED_IN) else Snapshot(Level.READY)
    }

    fun player(context: Context? = null): Snapshot {
        return when (PlayerBackendSelector.preferredKind()) {
            PlayerBackendKind.EXO -> Snapshot(Level.READY, "ExoPlayer")
            PlayerBackendKind.EXTERNAL_MPV -> {
                val pkg = context?.let { ExternalMpvBackend.preferredInstalledPackage(it) }
                if (pkg != null) Snapshot(Level.READY, pkg)
                else Snapshot(Level.NOT_CONFIGURED, "MPV")
            }
        }
    }

    fun plugins(): Snapshot {
        val count = runCatching { PluginRegistry.size }.getOrDefault(0)
        return if (count > 0) Snapshot(Level.READY, count.toString())
        else Snapshot(Level.NOT_CONFIGURED)
    }

    fun tmdb(): Snapshot = when {
        !UserPreferences.enableTmdb -> Snapshot(Level.DISABLED)
        !com.dskja.betterstreamflix.utils.TMDb3.hasApiKey() -> Snapshot(Level.NOT_CONFIGURED)
        else -> Snapshot(Level.READY)
    }

    fun hubOverview(context: Context): String {
        val snapshots = listOf(
            trakt(), jellyfin(), plex(), debrid(), simkl(), openSubtitles(), player(context), plugins(), tmdb(),
        )
        val connected = snapshots.count { it.isHealthy }
        return context.getString(R.string.platform_hub_overview, connected, snapshots.size)
    }

    fun label(context: Context, snapshot: Snapshot): String {
        val base = when (snapshot.level) {
            Level.UNAVAILABLE -> context.getString(R.string.platform_status_unavailable)
            Level.DISABLED -> context.getString(R.string.platform_status_disabled)
            Level.NOT_CONFIGURED -> context.getString(R.string.platform_status_not_configured)
            Level.READY -> context.getString(R.string.platform_status_ready)
            Level.SIGNED_IN -> context.getString(R.string.platform_status_signed_in)
        }
        val detail = snapshot.detail?.takeIf { it.isNotBlank() } ?: return base
        return context.getString(R.string.platform_status_with_detail, base, detail)
    }

    fun forScreen(screenKey: String, context: Context? = null): Snapshot = when (screenKey) {
        "screen_platform_trakt" -> trakt()
        "screen_platform_jellyfin" -> jellyfin()
        "screen_platform_plex" -> plex()
        "screen_platform_debrid" -> debrid()
        "screen_platform_simkl" -> simkl()
        "screen_platform_subtitles" -> openSubtitles()
        "screen_platform_player" -> player(context)
        "screen_platform_plugins" -> plugins()
        else -> Snapshot(Level.NOT_CONFIGURED)
    }

    private fun hostOf(url: String): String? =
        runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
}
