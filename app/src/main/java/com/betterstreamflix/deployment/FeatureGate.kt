package com.betterstreamflix.deployment

/**
 * Feature gate — manages feature availability based on build type,
 * version, and rollout percentage.
 */
object FeatureGate {

    private val gates = mutableMapOf<String, FeatureGateConfig>()

    data class FeatureGateConfig(
        val featureName: String,
        val enabledInDebug: Boolean,
        val enabledInRelease: Boolean,
        val enabledInBeta: Boolean,
        val minVersion: String?,
        val rolloutPercentage: Int,
    )

    /**
     * Register a feature gate.
     */
    fun registerGate(config: FeatureGateConfig) {
        gates[config.featureName] = config
    }

    /**
     * Check if a feature is enabled.
     */
    fun isFeatureEnabled(featureName: String, currentVersion: String = "0.0.0"): Boolean {
        val gate = gates[featureName] ?: return true // Default: enabled if no gate

        val buildType = BuildConfiguration.getBuildType()
        val enabledForBuild = when (buildType) {
            BuildConfiguration.BuildType.DEBUG -> gate.enabledInDebug
            BuildConfiguration.BuildType.RELEASE -> gate.enabledInRelease
            BuildConfiguration.BuildType.BETA -> gate.enabledInBeta
        }

        if (!enabledForBuild) return false

        // Check minimum version
        if (gate.minVersion != null) {
            if (VersionManager.compareVersions(currentVersion, gate.minVersion) < 0) {
                return false
            }
        }

        // Check rollout percentage
        if (gate.rolloutPercentage < 100) {
            val hash = (featureName + currentVersion).hashCode()
            val bucket = Math.abs(hash) % 100
            if (bucket >= gate.rolloutPercentage) return false
        }

        return true
    }

    /**
     * Get all registered feature gates.
     */
    fun getAllGates(): Map<String, FeatureGateConfig> = gates.toMap()

    /**
     * Get a feature gate config.
     */
    fun getGate(featureName: String): FeatureGateConfig? = gates[featureName]

    /**
     * Remove a feature gate.
     */
    fun removeGate(featureName: String) {
        gates.remove(featureName)
    }

    /**
     * Clear all gates.
     */
    fun clearAll() {
        gates.clear()
    }

    /**
     * Register default gates.
     */
    fun registerDefaults() {
        registerGate(FeatureGateConfig("cast", true, false, true, null, 100))
        registerGate(FeatureGateConfig("downloads", true, true, true, null, 100))
        registerGate(FeatureGateConfig("analytics", false, true, true, null, 50))
        registerGate(FeatureGateConfig("experimental_player", true, false, false, null, 100))
    }
}
