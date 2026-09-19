package com.dskja.betterstreamflix.platform.trakt

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.dskja.betterstreamflix.utils.UserPreferences
import java.util.UUID

/**
 * Browser OAuth (authorization code) for Trakt.
 * Redirect URI must match the API app on trakt.tv exactly:
 * [TraktConfig.OAUTH_REDIRECT_URI]
 */
object TraktOAuth {
    private const val TAG = "TraktOAuth"
    private const val PREF_STATE = "trakt_oauth_pending_state"

    fun authorizeIntent(context: Context): Intent? {
        if (!TraktConfig.hasAppCredentials()) return null
        val clientId = TraktConfig.clientId()
        if (clientId.isBlank()) return null
        val state = UUID.randomUUID().toString()
        context.getSharedPreferences("trakt_oauth", Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_STATE, state)
            .apply()
        val url = TraktConfig.authorizeUrl(state)
        return Intent(Intent.ACTION_VIEW, url.toUri())
    }

    fun consumeCallback(context: Context, uri: Uri?): Result {
        if (uri == null) return Result.Ignored
        if (uri.scheme != TraktConfig.OAUTH_SCHEME || uri.host != TraktConfig.OAUTH_HOST) {
            return Result.Ignored
        }
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            return Result.Failed(error)
        }
        val code = uri.getQueryParameter("code").orEmpty()
        val state = uri.getQueryParameter("state").orEmpty()
        val expected = context.getSharedPreferences("trakt_oauth", Context.MODE_PRIVATE)
            .getString(PREF_STATE, null)
        context.getSharedPreferences("trakt_oauth", Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_STATE)
            .apply()
        if (code.isBlank()) return Result.Failed("missing_code")
        if (expected.isNullOrBlank() || state != expected) {
            Log.w(TAG, "OAuth state mismatch")
            return Result.Failed("state_mismatch")
        }
        return Result.Code(code)
    }

    sealed class Result {
        data object Ignored : Result()
        data class Code(val code: String) : Result()
        data class Failed(val reason: String) : Result()
    }
}
