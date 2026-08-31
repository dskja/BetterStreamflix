package com.betterstreamflix.sync

import android.content.Context
import androidx.core.content.edit

/**
 * Optional Trakt integration settings (OAuth scaffold for v1.1).
 */
object TraktSettings {

    private const val PREFS = "trakt_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ACCESS_TOKEN = "access_token"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    fun getAccessToken(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACCESS_TOKEN, null)

    fun saveAccessToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_ACCESS_TOKEN, token) }
    }
}
