package com.betterstreamflix.deployment

/**
 * Build variant manager — manages build variants and their
 * specific configurations.
 */
object BuildVariantManager {

    enum class BuildVariant(
        val suffix: String,
        val isMinified: Boolean,
        val isDebuggable: Boolean,
        val supportsAnalytics: Boolean,
    ) {
        DEBUG(suffix = "-debug", isMinified = false, isDebuggable = true, supportsAnalytics = false),
        BETA(suffix = "-beta", isMinified = true, isDebuggable = false, supportsAnalytics = true),
        RELEASE(suffix = "-release", isMinified = true, isDebuggable = false, supportsAnalytics = true),
    }

    /**
     * Get the current build variant.
     */
    fun getCurrentVariant(): BuildVariant {
        return when (BuildConfiguration.getBuildType()) {
            BuildConfiguration.BuildType.DEBUG -> BuildVariant.DEBUG
            BuildConfiguration.BuildType.BETA -> BuildVariant.BETA
            BuildConfiguration.BuildType.RELEASE -> BuildVariant.RELEASE
        }
    }

    /**
     * Get the APK file name for the current variant.
     */
    fun getApkFileName(versionName: String): String {
        val variant = getCurrentVariant()
        return "BetterStreamflix-v$versionName${variant.suffix}.apk"
    }

    /**
     * Get the AAB file name for the current variant.
     */
    fun getAabFileName(versionName: String): String {
        val variant = getCurrentVariant()
        return "BetterStreamflix-v$versionName${variant.suffix}.aab"
    }

    /**
     * Check if ProGuard/R8 minification is enabled.
     */
    fun isMinificationEnabled(): Boolean {
        return getCurrentVariant().isMinified
    }

    /**
     * Check if the build is debuggable.
     */
    fun isDebuggable(): Boolean {
        return getCurrentVariant().isDebuggable
    }

    /**
     * Check if analytics should be collected.
     */
    fun shouldCollectAnalytics(): Boolean {
        return getCurrentVariant().supportsAnalytics
    }

    /**
     * Get variant-specific configuration.
     */
    fun getVariantConfig(): VariantConfig {
        val variant = getCurrentVariant()
        return VariantConfig(
            variant = variant,
            appName = "BetterStreamflix${if (variant == BuildVariant.DEBUG) " Debug" else if (variant == BuildVariant.BETA) " Beta" else ""}",
            applicationIdSuffix = if (variant == BuildVariant.DEBUG) ".debug" else if (variant == BuildVariant.BETA) ".beta" else "",
            supportsCrashReporting = variant != BuildVariant.DEBUG,
            supportsPerformanceMonitoring = variant != BuildVariant.DEBUG,
        )
    }

    data class VariantConfig(
        val variant: BuildVariant,
        val appName: String,
        val applicationIdSuffix: String,
        val supportsCrashReporting: Boolean,
        val supportsPerformanceMonitoring: Boolean,
    )
}
