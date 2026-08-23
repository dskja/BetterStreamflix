package com.betterstreamflix.download

import android.content.Context
import android.net.wifi.WifiManager
import androidx.core.content.edit

/**
 * Download policy manager — manages download policies like
 * WiFi-only downloads and battery level checks.
 */
object DownloadPolicyManager {

    private const val PREFS_NAME = "download_policy"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_MIN_BATTERY = "min_battery"
    private const val KEY_MAX_CONCURRENT = "max_concurrent"

    /**
     * Check if WiFi-only downloads are enabled.
     */
    fun isWifiOnly(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WIFI_ONLY, false)
    }

    /**
     * Set WiFi-only downloads.
     */
    fun setWifiOnly(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    /**
     * Get minimum battery level for downloads.
     */
    fun getMinBatteryLevel(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MIN_BATTERY, 20)
    }

    /**
     * Set minimum battery level.
     */
    fun setMinBatteryLevel(context: Context, level: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_MIN_BATTERY, level.coerceIn(0, 100)).apply()
    }

    /**
     * Check if downloads are allowed based on current conditions.
     */
    fun canDownload(context: Context): DownloadPermission {
        val wifiOnly = isWifiOnly(context)
        val networkType = com.betterstreamflix.network.NetworkCapabilityChecker.getNetworkType(context)

        if (wifiOnly && networkType != com.betterstreamflix.network.NetworkCapabilityChecker.NetworkType.WIFI
            && networkType != com.betterstreamflix.network.NetworkCapabilityChecker.NetworkType.ETHERNET) {
            return DownloadPermission.Denied("WiFi-only downloads enabled. Connect to WiFi to download.")
        }

        val batteryLevel = getBatteryLevel(context)
        val minBattery = getMinBatteryLevel(context)
        if (batteryLevel in 0..minBattery) {
            return DownloadPermission.Denied("Battery level too low ($batteryLevel%). Minimum is $minBattery%.")
        }

        if (!com.betterstreamflix.network.NetworkCapabilityChecker.hasInternet(context)) {
            return DownloadPermission.Denied("No internet connection.")
        }

        if (DownloadStorageChecker.isStorageCritical(context)) {
            return DownloadPermission.Denied("Storage is critically low.")
        }

        return DownloadPermission.Allowed
    }

    /**
     * Get current battery level.
     */
    fun getBatteryLevel(context: Context): Int {
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val battery = context.registerReceiver(null, filter) ?: return -1
        val level = battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) (level * 100) / scale else -1
    }

    /**
     * Check if device is charging.
     */
    fun isCharging(context: Context): Boolean {
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val battery = context.registerReceiver(null, filter) ?: return false
        val status = battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
            status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }

    sealed class DownloadPermission {
        data object Allowed : DownloadPermission()
        data class Denied(val reason: String) : DownloadPermission()
    }
}
