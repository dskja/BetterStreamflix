package com.betterstreamflix.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Root/jailbreak detector — checks if the device is rooted,
 * which may affect security posture.
 */
object SecurityChecker {

    /**
     * Check if the device is likely rooted.
     */
    fun isDeviceRooted(): Boolean {
        return checkRootFiles() || checkSuBinary() || checkRootPackages()
    }

    /**
     * Check if the app is running on an emulator.
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
            || "google_sdk" == Build.PRODUCT)
    }

    /**
     * Check if ADB debugging is enabled.
     */
    fun isAdbEnabled(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            pm.getApplicationInfo("com.android.settings", 0)
            android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.ADB_ENABLED,
                0,
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if the app is debuggable.
     */
    fun isDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun checkRootFiles(): Boolean {
        val paths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/data/local/su", "/su/bin/su",
        )
        return paths.any { java.io.File(it).exists() }
    }

    private fun checkSuBinary(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun checkRootPackages(): Boolean {
        val packages = listOf(
            "com.topjohnwu.magisk",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.kingouser.com",
            "com.kingroot.kinguser",
        )
        return packages.any { pkg ->
            try {
                val process = Runtime.getRuntime().exec(arrayOf("pm", "list", "packages", pkg))
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                output.contains(pkg)
            } catch (e: Exception) {
                false
            }
        }
    }
}
