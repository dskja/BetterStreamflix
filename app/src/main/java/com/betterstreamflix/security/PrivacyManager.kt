package com.betterstreamflix.security

import android.content.Context
import androidx.core.content.edit

/**
 * Privacy manager — manages privacy settings like data collection,
 * crash reporting, and analytics opt-in.
 */
object PrivacyManager {

    private const val PREFS_NAME = "privacy_settings"

    /**
     * Check if analytics is enabled.
     */
    fun isAnalyticsEnabled(context: Context): Boolean {
        return getPrivacyPref(context, "analytics_enabled", false)
    }

    /**
     * Set analytics enabled.
     */
    fun setAnalyticsEnabled(context: Context, enabled: Boolean) {
        setPrivacyPref(context, "analytics_enabled", enabled)
    }

    /**
     * Check if crash reporting is enabled.
     */
    fun isCrashReportingEnabled(context: Context): Boolean {
        return getPrivacyPref(context, "crash_reporting_enabled", false)
    }

    /**
     * Set crash reporting enabled.
     */
    fun setCrashReportingEnabled(context: Context, enabled: Boolean) {
        setPrivacyPref(context, "crash_reporting_enabled", enabled)
    }

    /**
     * Check if usage tracking is enabled.
     */
    fun isUsageTrackingEnabled(context: Context): Boolean {
        return getPrivacyPref(context, "usage_tracking_enabled", true)
    }

    /**
     * Set usage tracking enabled.
     */
    fun setUsageTrackingEnabled(context: Context, enabled: Boolean) {
        setPrivacyPref(context, "usage_tracking_enabled", enabled)
    }

    /**
     * Check if personalized recommendations are enabled.
     */
    fun isPersonalizedRecommendationsEnabled(context: Context): Boolean {
        return getPrivacyPref(context, "personalized_recs", true)
    }

    /**
     * Set personalized recommendations enabled.
     */
    fun setPersonalizedRecommendationsEnabled(context: Context, enabled: Boolean) {
        setPrivacyPref(context, "personalized_recs", enabled)
    }

    /**
     * Clear all user data (for privacy compliance).
     */
    fun clearAllUserData(context: Context) {
        // Clear caches
        com.betterstreamflix.performance.DiskCacheManager.clearCache(context)
        com.betterstreamflix.performance.MemoryCacheManager.clearAll()
        com.betterstreamflix.security.CookieSecurityManager.clearAllCookies()

        // Clear error logs
        com.betterstreamflix.resilience.ErrorLog.clear(context)
        com.betterstreamflix.resilience.ErrorReporter.clearReports(context)
    }

    private fun getPrivacyPref(context: Context, key: String, default: Boolean): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(key, default)
    }

    private fun setPrivacyPref(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(key, value)
        }
    }
}
