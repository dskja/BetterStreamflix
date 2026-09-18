package com.dskja.betterstreamflix.platform.trakt

import android.content.Context
import com.dskja.betterstreamflix.utils.UserPreferences

object TraktConfig {
    const val API_BASE = "https://api.trakt.tv"
    const val API_VERSION = "2"

    fun isEnabled(): Boolean = UserPreferences.traktEnabled

    fun clientId(): String = UserPreferences.traktClientId.trim()

    fun accessToken(): String = UserPreferences.traktAccessToken.trim()

    fun configured(): Boolean =
        isEnabled() && clientId().isNotEmpty() && accessToken().isNotEmpty()

    fun authHeaders(): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        put("trakt-api-version", API_VERSION)
        put("trakt-api-key", clientId())
        val token = accessToken()
        if (token.isNotEmpty()) {
            put("Authorization", "Bearer $token")
        }
    }

    /** Device-code OAuth helper URL for settings UI. */
    fun authorizeHelpUrl(context: Context): String =
        "https://trakt.tv/oauth/devices"
}
