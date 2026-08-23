package com.betterstreamflix.analytics

import android.content.Context
import android.os.Build
import com.betterstreamflix.utils.AppConfig

/**
 * Device info collector — gathers device specifications for diagnostics
 * and bug reports.
 */
object DeviceInfoCollector {

    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val brand: String,
        val device: String,
        val product: String,
        val androidVersion: String,
        val sdkLevel: Int,
        val isTv: Boolean,
        val screenDensity: Float,
        val screenWidthPx: Int,
        val screenHeightPx: Int,
        val screenWidthDp: Int,
        val screenHeightDp: Int,
        val abis: List<String>,
        val totalMemoryMb: Long,
        val availableMemoryMb: Long,
        val appVersionName: String,
        val appVersionCode: Int,
    )

    /**
     * Collect device information.
     */
    fun collect(context: Context): DeviceInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val metrics = context.resources.displayMetrics
        val config = context.resources.configuration

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            brand = Build.BRAND,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            androidVersion = Build.VERSION.RELEASE ?: "unknown",
            sdkLevel = Build.VERSION.SDK_INT,
            isTv = AppConfig.isTv,
            screenDensity = metrics.density,
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            screenWidthDp = config.screenWidthDp,
            screenHeightDp = config.screenHeightDp,
            abis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Build.SUPPORTED_ABIS.toList()
            } else emptyList(),
            totalMemoryMb = memoryInfo.totalMem / (1024 * 1024),
            availableMemoryMb = memoryInfo.availMem / (1024 * 1024),
            appVersionName = AppConfig.versionName,
            appVersionCode = AppConfig.versionCode,
        )
    }

    /**
     * Format device info as a readable string.
     */
    fun formatDeviceInfo(info: DeviceInfo): String {
        return buildString {
            appendLine("Device: ${info.manufacturer} ${info.model}")
            appendLine("Android: ${info.androidVersion} (SDK ${info.sdkLevel})")
            appendLine("Screen: ${info.screenWidthPx}x${info.screenHeightPx} (${info.screenWidthDp}x${info.screenHeightDp}dp, density ${info.screenDensity})")
            appendLine("TV: ${info.isTv}")
            appendLine("ABIs: ${info.abis.joinToString(", ")}")
            appendLine("Memory: ${info.availableMemoryMb}MB / ${info.totalMemoryMb}MB available")
            appendLine("App: v${info.appVersionName} (${info.appVersionCode})")
        }
    }
}
