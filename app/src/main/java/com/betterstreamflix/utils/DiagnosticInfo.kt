package com.betterstreamflix.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.betterstreamflix.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic info collector for bug reports.
 * Gathers system info, app version, and runtime state.
 */
object DiagnosticInfo {

    /**
     * Collect all diagnostic information as a formatted string.
     */
    fun collect(context: Context): String {
        return buildString {
            appendLine("=== BetterStreamflix Diagnostic Report ===")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine()
            appendLine("== App Info ==")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build Type: ${if (BuildConfig.DEBUG) "debug" else "release"}")
            appendLine("Application ID: ${BuildConfig.APPLICATION_ID}")
            appendLine()
            appendLine("== Device Info ==")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine()
            appendLine("== Features ==")
            appendLine("TV (Leanback): ${context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)}")
            appendLine("Touchscreen: ${context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)}")
            appendLine()
            appendLine("== Provider Info ==")
            appendLine("Current Provider: ${UserPreferences.currentProvider?.name ?: "None"}")
            appendLine("Provider URL: ${UserPreferences.providerUrl ?: "N/A"}")
            appendLine()
            appendLine("== Settings ==")
            appendLine("Autoplay: ${UserPreferences.autoplay}")
            appendLine("Autoplay Buffer: ${UserPreferences.autoplayBuffer}s")
            appendLine("Quality Height: ${UserPreferences.qualityHeight ?: "Auto"}")
            appendLine("App Language: ${UserPreferences.appLanguage}")
            appendLine()
            appendLine("== Health Stats ==")
            com.betterstreamflix.providers.ProviderHealthMonitor.getAllStats().forEach { (name, stats) ->
                appendLine("  $name: ${stats.totalRequests} requests, ${stats.totalFailures} failures, " +
                    "${(stats.failureRate * 100).toInt()}% failure rate, ${stats.consecutiveFailures} consecutive failures")
            }
        }
    }

    /**
     * Log diagnostic info to logcat.
     */
    fun log(context: Context) {
        Log.i("DiagnosticInfo", collect(context))
    }
}
