package com.dskja.betterstreamflix.platform

import com.dskja.betterstreamflix.platform.debrid.DebridResolver
import com.dskja.betterstreamflix.platform.jellyfin.JellyfinApi
import com.dskja.betterstreamflix.platform.plex.PlexApi
import com.dskja.betterstreamflix.platform.simkl.SimklClient
import com.dskja.betterstreamflix.platform.simkl.SimklConfig
import com.dskja.betterstreamflix.platform.subtitles.OpenSubtitlesV1Client
import com.dskja.betterstreamflix.platform.trakt.TraktConfig
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Live connection probes for Integrations settings. Never throws to callers.
 */
object IntegrationProbes {

    data class ProbeResult(
        val ok: Boolean,
        val message: String,
    )

    suspend fun jellyfin(): ProbeResult = withContext(Dispatchers.IO) {
        runCatching {
            val api = JellyfinApi()
            if (!api.configured()) {
                return@runCatching ProbeResult(false, "Jellyfin URL / user / token missing")
            }
            val info = api.ping()
            if (info.isNullOrBlank()) {
                ProbeResult(false, "Jellyfin did not return a user profile")
            } else {
                ProbeResult(true, "Jellyfin OK · $info")
            }
        }.getOrElse { ProbeResult(false, it.message ?: "Jellyfin probe failed") }
    }

    suspend fun plex(): ProbeResult = withContext(Dispatchers.IO) {
        runCatching {
            val api = PlexApi()
            if (!api.configured()) {
                return@runCatching ProbeResult(false, "Plex URL / token missing")
            }
            val identity = api.ping()
            if (identity.isNullOrBlank()) {
                ProbeResult(false, "Plex identity check failed")
            } else {
                val sections = api.librarySections().length()
                ProbeResult(true, "Plex OK · $identity · $sections libraries")
            }
        }.getOrElse { ProbeResult(false, it.message ?: "Plex probe failed") }
    }

    suspend fun debrid(): ProbeResult = withContext(Dispatchers.IO) {
        runCatching {
            if (!UserPreferences.debridEnabled) {
                return@runCatching ProbeResult(false, "Debrid is disabled")
            }
            val service = DebridResolver.activeService()
                ?: return@runCatching ProbeResult(false, "No API key for active debrid provider")
            if (service.isAuthenticated()) {
                ProbeResult(true, "${service.name} account OK")
            } else {
                ProbeResult(false, "${service.name} authentication failed")
            }
        }.getOrElse { ProbeResult(false, it.message ?: "Debrid probe failed") }
    }

    suspend fun simkl(): ProbeResult = withContext(Dispatchers.IO) {
        runCatching {
            if (!SimklConfig.configured()) {
                return@runCatching ProbeResult(false, "Simkl not configured")
            }
            val name = SimklClient.pingUser()
            if (name.isNullOrBlank()) {
                ProbeResult(false, "Simkl token rejected")
            } else {
                ProbeResult(true, "Simkl OK · $name")
            }
        }.getOrElse { ProbeResult(false, it.message ?: "Simkl probe failed") }
    }

    suspend fun openSubtitles(): ProbeResult = withContext(Dispatchers.IO) {
        runCatching {
            if (!OpenSubtitlesV1Client.configured()) {
                return@runCatching ProbeResult(false, "OpenSubtitles API key missing")
            }
            val detail = OpenSubtitlesV1Client.ping()
            if (detail == null) {
                ProbeResult(false, "OpenSubtitles API key rejected")
            } else {
                ProbeResult(true, detail)
            }
        }.getOrElse { ProbeResult(false, it.message ?: "OpenSubtitles probe failed") }
    }

    fun traktLocal(): ProbeResult = when {
        !TraktConfig.hasAppCredentials() ->
            ProbeResult(false, "Trakt VIP credentials unavailable")
        !UserPreferences.traktEnabled ->
            ProbeResult(false, "Trakt disabled")
        TraktConfig.isSignedIn() ->
            ProbeResult(true, "Trakt signed in")
        else ->
            ProbeResult(false, "Trakt not signed in")
    }
}
