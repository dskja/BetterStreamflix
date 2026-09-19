package com.dskja.betterstreamflix.platform.trakt

import android.content.Context
import android.net.Uri
import com.dskja.betterstreamflix.utils.UserPreferences

object TraktConfig {
    const val API_BASE = "https://api.trakt.tv"
    const val API_VERSION = "2"
    const val OAUTH_SCHEME = "betterstreamflix"
    const val OAUTH_HOST = "trakt"
    const val OAUTH_PATH = "oauth"
    /** Must be registered exactly in the Trakt API application settings. */
    const val OAUTH_REDIRECT_URI = "$OAUTH_SCHEME://$OAUTH_HOST/$OAUTH_PATH"
    private const val AUTHORIZE_BASE = "https://trakt.tv/oauth/authorize"

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

    fun authorizeUrl(state: String): String =
        Uri.parse(AUTHORIZE_BASE).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId())
            .appendQueryParameter("redirect_uri", OAUTH_REDIRECT_URI)
            .appendQueryParameter("state", state)
            .build()
            .toString()

    /** Device-code OAuth helper URL for settings UI (TV / fallback). */
    fun authorizeHelpUrl(context: Context): String =
        "https://trakt.tv/oauth/devices"
}
