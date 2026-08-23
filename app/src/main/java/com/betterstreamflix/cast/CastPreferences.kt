package com.betterstreamflix.cast

import android.content.Context
import androidx.core.content.edit

/**
 * Cast preferences — manages user preferences for casting behavior.
 */
object CastPreferences {

    private const val PREFS_NAME = "cast_prefs"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_LAST_DEVICE = "last_device"
    private const val KEY_QUALITY = "quality"
    private const val KEY_NOTIFICATIONS = "notifications"

    /**
     * Check if auto-connect is enabled.
     */
    fun isAutoConnectEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_CONNECT, false)
    }

    /**
     * Set auto-connect.
     */
    fun setAutoConnect(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_CONNECT, enabled).apply()
    }

    /**
     * Get last connected device ID.
     */
    fun getLastDeviceId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_DEVICE, null)
    }

    /**
     * Set last connected device.
     */
    fun setLastDeviceId(context: Context, deviceId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_DEVICE, deviceId).apply()
    }

    /**
     * Get cast quality preference.
     */
    fun getCastQuality(context: Context): CastQuality {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_QUALITY, "AUTO")
        return CastQuality.entries.find { it.name == value } ?: CastQuality.AUTO
    }

    /**
     * Set cast quality.
     */
    fun setCastQuality(context: Context, quality: CastQuality) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_QUALITY, quality.name).apply()
    }

    /**
     * Check if cast notifications are enabled.
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATIONS, true)
    }

    /**
     * Set notifications enabled.
     */
    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    }

    enum class CastQuality { AUTO, LOW, MEDIUM, HIGH, MAX }
}
