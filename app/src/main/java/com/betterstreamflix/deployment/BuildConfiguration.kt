package com.betterstreamflix.deployment

/**
 * Build configuration — provides build-time configuration values
 * and environment detection.
 */
object BuildConfiguration {

    /**
     * Build types.
     */
    enum class BuildType { DEBUG, RELEASE, BETA }

    /**
     * Get the current build type.
     */
    fun getBuildType(): BuildType {
        return when {
            isDebug() -> BuildType.DEBUG
            isBeta() -> BuildType.BETA
            else -> BuildType.RELEASE
        }
    }

    /**
     * Check if this is a debug build.
     */
    fun isDebug(): Boolean {
        return try {
            Class.forName("com.betterstreamflix.BuildConfig")
                .getField("DEBUG")
                .getBoolean(null)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if this is a release build.
     */
    fun isRelease(): Boolean = !isDebug()

    /**
     * Check if this is a beta build.
     */
    fun isBeta(): Boolean {
        return try {
            val flavor = Class.forName("com.betterstreamflix.BuildConfig")
                .getField("FLAVOR")
                .get(null) as? String
            flavor?.lowercase()?.contains("beta") == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the application ID.
     */
    fun getApplicationId(): String {
        return try {
            Class.forName("com.betterstreamflix.BuildConfig")
                .getField("APPLICATION_ID")
                .get(null) as? String ?: "com.betterstreamflix"
        } catch (e: Exception) {
            "com.betterstreamflix"
        }
    }

    /**
     * Get build version name from BuildConfig.
     */
    fun getBuildVersionName(): String {
        return try {
            Class.forName("com.betterstreamflix.BuildConfig")
                .getField("VERSION_NAME")
                .get(null) as? String ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Get build version code from BuildConfig.
     */
    fun getBuildVersionCode(): Int {
        return try {
            Class.forName("com.betterstreamflix.BuildConfig")
                .getField("VERSION_CODE")
                .get(null) as? Int ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Check if a specific build feature is enabled.
     */
    fun isFeatureEnabled(featureName: String): Boolean {
        return try {
            Class.forName("com.betterstreamflix.BuildConfig")
                .getField(featureName)
                .getBoolean(null)
        } catch (e: Exception) {
            false
        }
    }
}
