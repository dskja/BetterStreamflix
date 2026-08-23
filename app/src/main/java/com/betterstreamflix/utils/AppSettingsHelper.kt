package com.betterstreamflix.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * App settings helper — provides quick access to system settings
 * relevant to the app (notifications, storage, battery, etc.)
 */
object AppSettingsHelper {

    /**
     * Open app notification settings.
     */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    }

    /**
     * Open app details settings page.
     */
    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    /**
     * Open system display settings.
     */
    fun openDisplaySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }

    /**
     * Open system language settings.
     */
    fun openLanguageSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS))
    }

    /**
     * Open system storage settings.
     */
    fun openStorageSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
    }

    /**
     * Open system date and time settings.
     */
    fun openDateTimeSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
    }

    /**
     * Open WiFi settings.
     */
    fun openWifiSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }
}
