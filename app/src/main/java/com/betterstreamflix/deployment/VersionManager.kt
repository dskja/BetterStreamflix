package com.betterstreamflix.deployment

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Version manager — manages app version information and version
 * comparison for update checks.
 */
object VersionManager {

    /**
     * Get the current app version name.
     */
    fun getVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }

    /**
     * Get the current app version code.
     */
    fun getVersionCode(context: Context): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            0L
        }
    }

    /**
     * Compare two version strings.
     * @return -1 if v1 < v2, 0 if equal, 1 if v1 > v2
     */
    fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 < p2) return -1
            if (p1 > p2) return 1
        }
        return 0
    }

    /**
     * Check if an update is available.
     */
    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        return compareVersions(currentVersion, latestVersion) < 0
    }

    /**
     * Get version info as a formatted string.
     */
    fun getVersionInfo(context: Context): String {
        val name = getVersionName(context)
        val code = getVersionCode(context)
        return "v$name ($code)"
    }

    /**
     * Check if this is a beta/alpha version.
     */
    fun isBetaVersion(context: Context): Boolean {
        val version = getVersionName(context).lowercase()
        return "beta" in version || "alpha" in version || "rc" in version || "dev" in version
    }

    /**
     * Parse a semantic version string.
     */
    fun parseSemanticVersion(version: String): SemanticVersion? {
        val regex = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([a-zA-Z0-9.]+))?(?:\+([a-zA-Z0-9.]+))?$""")
        val match = regex.matchEntire(version) ?: return null
        return SemanticVersion(
            major = match.groupValues[1].toInt(),
            minor = match.groupValues[2].toInt(),
            patch = match.groupValues[3].toInt(),
            preRelease = match.groupValues[4].ifEmpty { null },
            buildMetadata = match.groupValues[5].ifEmpty { null },
        )
    }

    data class SemanticVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String?,
        val buildMetadata: String?,
    )
}
