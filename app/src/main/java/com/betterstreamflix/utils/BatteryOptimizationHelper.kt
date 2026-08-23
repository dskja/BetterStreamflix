package com.betterstreamflix.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Battery optimization helper — checks and requests battery optimization exemption
 * for background work like sync and updates.
 */
object BatteryOptimizationHelper {

    /**
     * Check if the app is exempt from battery optimizations.
     */
    fun isExemptFromBatteryOptimizations(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    /**
     * Get intent to request battery optimization exemption.
     */
    fun getBatteryOptimizationRequestIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
    }
}

/**
 * Doze mode helper for adapting behavior when device is in doze.
 */
object DozeModeHelper {

    /**
     * Check if device is currently in doze mode.
     */
    fun isDeviceIdleMode(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            powerManager?.isDeviceIdleMode ?: false
        } else {
            false
        }
    }

    /**
     * Check if the device supports doze mode.
     */
    fun supportsDoze(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
    }
}

