package com.betterstreamflix.utils

import com.betterstreamflix.BuildConfig

/**
 * Centralized build configuration access.
 * Wraps BuildConfig fields with typed accessors and provides
 * runtime configuration values.
 */
object AppConfig {
    // === Build Info ===
    val isDebug: Boolean get() = BuildConfig.DEBUG
    val applicationId: String get() = BuildConfig.APPLICATION_ID
    val versionName: String get() = BuildConfig.VERSION_NAME
    val versionCode: Int get() = BuildConfig.VERSION_CODE

    // === Feature Flags ===
    val enableLogging: Boolean get() = BuildConfig.DEBUG
    val enableCrashReporting: Boolean get() = !BuildConfig.DEBUG
    val enableAnalytics: Boolean get() = !BuildConfig.DEBUG
    val enableStrictMode: Boolean get() = BuildConfig.DEBUG

    // === API Keys (from BuildConfig) ===    val tmdbApiKey: String get() = BuildConfig.TMDB_API_KEY ?: ""
    val subdlApiKey: String get() = BuildConfig.SUBDL_API_KEY ?: ""
    val rabbitstreamSourceApi: String get() = BuildConfig.RABBITSTREAM_SOURCE_API ?: ""

    // === Runtime Config ===
    val isTv: Boolean get() = StreamFlixApp.instance.packageManager
        .hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)

    val cacheThresholdMb: Long get() = if (isTv) Constants.CACHE_THRESHOLD_TV_MB else Constants.CACHE_THRESHOLD_MOBILE_MB

    // === Version Helpers ===
    fun isVersionAtLeast(minVersion: String): Boolean {
        val current = versionName.split(".").map { it.toIntOrNull() ?: 0 }
        val min = minVersion.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(current.size, min.size)) {
            val c = current.getOrElse(i) { 0 }
            val m = min.getOrElse(i) { 0 }
            if (c > m) return true
            if (c < m) return false
        }
        return true
    }
}
