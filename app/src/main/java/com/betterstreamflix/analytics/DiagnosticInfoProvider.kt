package com.betterstreamflix.analytics

import android.content.Context
import com.betterstreamflix.utils.AppConfig

/**
 * Diagnostic info provider — aggregates all diagnostic information
 * for the debug/settings panel.
 */
object DiagnosticInfoProvider {

    /**
     * Get a complete diagnostic report.
     */
    fun getDiagnosticReport(context: Context): String {
        return buildString {
            appendLine("=== BetterStreamflix Diagnostics ===")
            appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
            appendLine()

            appendLine("--- App Info ---")
            appendLine("Version: ${AppConfig.versionName} (${AppConfig.versionCode})")
            appendLine("Debug build: ${AppConfig.isDebug}")
            appendLine("TV: ${AppConfig.isTv}")
            appendLine()

            appendLine("--- Device Info ---")
            appendLine(DeviceInfoCollector.formatDeviceInfo(DeviceInfoCollector.collect(context)))
            appendLine()

            appendLine("--- Memory ---")
            appendLine(com.betterstreamflix.performance.MemoryMonitor.getMemoryReport(context))
            appendLine()

            appendLine("--- Cache ---")
            appendLine("Disk cache: ${com.betterstreamflix.performance.DiskCacheManager.getCacheSizeFormatted(context)}")
            appendLine("Memory cache entries: ${com.betterstreamflix.performance.MemoryCacheManager.totalSize()}")
            appendLine()

            appendLine("--- Network ---")
            appendLine(com.betterstreamflix.security.NetworkSecurityManager.getSecuritySummary(context))
            appendLine("Connection: ${com.betterstreamflix.resilience.ConnectionStateManager.connectionQuality.value}")
            appendLine("Online: ${com.betterstreamflix.resilience.ConnectionStateManager.isOnline.value}")
            appendLine()

            appendLine("--- Performance ---")
            appendLine(com.betterstreamflix.performance.PerformanceProfiler.getReport())
            appendLine()

            appendLine("--- Recent Errors ---")
            com.betterstreamflix.resilience.ErrorLog.getEntriesByLevel(context, com.betterstreamflix.resilience.ErrorLog.Level.WARNING)
                .take(10).forEach { entry ->
                    appendLine("[${entry.level}] ${entry.tag}: ${entry.message}")
                }
            appendLine()

            appendLine("--- Provider Stats ---")
            com.betterstreamflix.analytics.ProviderPerformanceTracker.getAllStats(
                context,
                com.betterstreamflix.providers.Provider.providers.keys.map { it.name },
            ).forEach { stats ->
                appendLine("${stats.providerName}: ${stats.successfulRequests}/${stats.totalRequests} (${String.format("%.1f", stats.successRate * 100)}%), avg ${stats.averageResponseTimeMs}ms")
            }
        }
    }

    /**
     * Copy diagnostic report to clipboard.
     */
    fun copyToClipboard(context: Context) {
        val report = getDiagnosticReport(context)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Diagnostics", report))
    }
}

