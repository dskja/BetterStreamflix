package com.betterstreamflix.security

import android.content.Context
import androidx.core.content.edit

/**
 * Network security manager — manages TLS/SSL settings, certificate
 * pinning, and DoH preferences.
 */
object NetworkSecurityManager {

    private const val PREFS_NAME = "network_security"

    /**
     * Check if DNS-over-HTTPS is enabled.
     */
    fun isDohEnabled(context: Context): Boolean {
        return getBoolPref(context, "doh_enabled", false)
    }

    /**
     * Set DoH enabled.
     */
    fun setDohEnabled(context: Context, enabled: Boolean) {
        setBoolPref(context, "doh_enabled", enabled)
    }

    /**
     * Check if TLS verification is strict.
     */
    fun isStrictTls(context: Context): Boolean {
        return getBoolPref(context, "strict_tls", true)
    }

    /**
     * Set strict TLS mode.
     */
    fun setStrictTls(context: Context, strict: Boolean) {
        setBoolPref(context, "strict_tls", strict)
    }

    /**
     * Check if certificate pinning is enabled.
     */
    fun isCertPinningEnabled(context: Context): Boolean {
        return getBoolPref(context, "cert_pinning", false)
    }

    /**
     * Set certificate pinning enabled.
     */
    fun setCertPinningEnabled(context: Context, enabled: Boolean) {
        setBoolPref(context, "cert_pinning", enabled)
    }

    /**
     * Get the DoH provider URL.
     */
    fun getDohUrl(context: Context): String? {
        return getStringPref(context, "doh_url")
    }

    /**
     * Set the DoH provider URL.
     */
    fun setDohUrl(context: Context, url: String) {
        setStringPref(context, "doh_url", url)
    }

    /**
     * Get security summary for diagnostics.
     */
    fun getSecuritySummary(context: Context): String {
        return buildString {
            appendLine("Network Security:")
            appendLine("  DoH: ${if (isDohEnabled(context)) "Enabled" else "Disabled"}")
            appendLine("  Strict TLS: ${if (isStrictTls(context)) "Yes" else "No"}")
            appendLine("  Cert Pinning: ${if (isCertPinningEnabled(context)) "Yes" else "No"}")
            appendLine("  Device Rooted: ${if (SecurityChecker.isDeviceRooted()) "Yes" else "No"}")
            appendLine("  Emulator: ${if (SecurityChecker.isEmulator()) "Yes" else "No"}")
        }
    }

    private fun getBoolPref(context: Context, key: String, default: Boolean): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(key, default)
    }

    private fun setBoolPref(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(key, value) }
    }

    private fun getStringPref(context: Context, key: String): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key, null)
    }

    private fun setStringPref(context: Context, key: String, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(key, value) }
    }
}
