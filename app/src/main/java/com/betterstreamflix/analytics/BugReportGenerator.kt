package com.betterstreamflix.analytics

import android.content.Context
import com.betterstreamflix.utils.AppConfig

/**
 * Bug report generator — collects all diagnostic information into
 * a single report for submission.
 */
object BugReportGenerator {

    data class BugReport(
        val timestamp: Long,
        val deviceInfo: DeviceInfoCollector.DeviceInfo,
        val errorLog: List<com.betterstreamflix.resilience.ErrorLog.LogEntry>,
        val debugLog: String,
        val userDescription: String,
        val appVersion: String,
        val isDebugBuild: Boolean,
    )

    /**
     * Generate a bug report.
     */
    fun generate(
        context: Context,
        userDescription: String = "",
    ): BugReport {
        return BugReport(
            timestamp = System.currentTimeMillis(),
            deviceInfo = DeviceInfoCollector.collect(context),
            errorLog = com.betterstreamflix.resilience.ErrorLog.getEntries(context),
            debugLog = DebugLogger.exportLog(),
            userDescription = userDescription,
            appVersion = AppConfig.versionName,
            isDebugBuild = AppConfig.isDebug,
        )
    }

    /**
     * Format a bug report as a readable string.
     */
    fun formatReport(report: BugReport): String {
        return buildString {
            appendLine("=== BetterStreamflix Bug Report ===")
            appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(report.timestamp))}")
            appendLine("App Version: ${report.appVersion} (debug: ${report.isDebugBuild})")
            appendLine()
            appendLine("--- Device Info ---")
            appendLine(DeviceInfoCollector.formatDeviceInfo(report.deviceInfo))
            appendLine()
            appendLine("--- User Description ---")
            appendLine(report.userDescription.ifBlank { "(no description provided)" })
            appendLine()
            appendLine("--- Recent Errors ---")
            report.errorLog.take(10).forEach { entry ->
                appendLine("[${entry.level}] ${entry.tag}: ${entry.message}")
            }
            appendLine()
            appendLine("--- Debug Log (last 50 lines) ---")
            report.debugLog.lines().takeLast(50).forEach { appendLine(it) }
        }
    }

    /**
     * Save a bug report to a file.
     */
    fun saveReport(context: Context, report: BugReport): java.io.File {
        val dir = java.io.File(context.cacheDir, "bug_reports").apply { mkdirs() }
        val file = java.io.File(dir, "bugreport_${report.timestamp}.txt")
        file.writeText(formatReport(report))
        return file
    }
}
