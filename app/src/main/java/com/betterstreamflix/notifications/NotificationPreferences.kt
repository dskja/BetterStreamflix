package com.betterstreamflix.notifications

import android.content.Context
import androidx.core.content.edit

/**
 * Notification preferences — manages user's notification settings.
 */
object NotificationPreferences {

    private const val PREFS_NAME = "notification_prefs"

    /**
     * Check if download notifications are enabled.
     */
    fun isDownloadNotificationsEnabled(context: Context): Boolean {
        return getBoolPref(context, "download_notifications", true)
    }

    /**
     * Set download notifications enabled.
     */
    fun setDownloadNotificationsEnabled(context: Context, enabled: Boolean) {
        setBoolPref(context, "download_notifications", enabled)
    }

    /**
     * Check if update notifications are enabled.
     */
    fun isUpdateNotificationsEnabled(context: Context): Boolean {
        return getBoolPref(context, "update_notifications", true)
    }

    /**
     * Set update notifications enabled.
     */
    fun setUpdateNotificationsEnabled(context: Context, enabled: Boolean) {
        setBoolPref(context, "update_notifications", enabled)
    }

    /**
     * Check if new content notifications are enabled.
     */
    fun isNewContentNotificationsEnabled(context: Context): Boolean {
        return getBoolPref(context, "new_content_notifications", false)
    }

    /**
     * Set new content notifications enabled.
     */
    fun setNewContentNotificationsEnabled(context: Context, enabled: Boolean) {
        setBoolPref(context, "new_content_notifications", enabled)
    }

    /**
     * Check if playback notifications are enabled.
     */
    fun isPlaybackNotificationsEnabled(context: Context): Boolean {
        return getBoolPref(context, "playback_notifications", true)
    }

    /**
     * Set playback notifications enabled.
     */
    fun setPlaybackNotificationsEnabled(context: Context, enabled: Boolean) {
        setBoolPref(context, "playback_notifications", enabled)
    }

    private fun getBoolPref(context: Context, key: String, default: Boolean): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(key, default)
    }

    private fun setBoolPref(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(key, value) }
    }
}
