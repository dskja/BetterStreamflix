package com.betterstreamflix.architecture

/**
 * Feature flags — manages runtime feature toggles for gradual
 * rollout and A/B testing.
 */
object FeatureFlags {

    private val flags = mutableMapOf<String, Boolean>()
    private val overrides = mutableMapOf<String, Boolean>()

    /**
     * Register a feature flag with a default value.
     */
    fun register(name: String, defaultEnabled: Boolean) {
        if (name !in flags) flags[name] = defaultEnabled
    }

    /**
     * Check if a feature is enabled.
     */
    fun isEnabled(name: String): Boolean {
        return overrides[name] ?: flags[name] ?: false
    }

    /**
     * Override a feature flag at runtime.
     */
    fun override(name: String, enabled: Boolean) {
        overrides[name] = enabled
    }

    /**
     * Remove an override.
     */
    fun removeOverride(name: String) {
        overrides.remove(name)
    }

    /**
     * Get all registered flags.
     */
    fun getAllFlags(): Map<String, Boolean> {
        return flags.mapValues { (name, default) -> overrides[name] ?: default }
    }

    /**
     * Reset all overrides.
     */
    fun resetOverrides() {
        overrides.clear()
    }

    // Common feature flag names
    const val ADAPTIVE_QUALITY = "adaptive_quality"
    const val BACKGROUND_PLAYBACK = "background_playback"
    const val PIP_MODE = "pip_mode"
    const val CAST_SUPPORT = "cast_support"
    const val DOWNLOADS = "downloads"
    const val RECOMMENDATIONS = "recommendations"
    const val FAVORITES = "favorites"
    const val SEARCH_HISTORY = "search_history"
    const val ANALYTICS = "analytics"
    const val CRASH_REPORTING = "crash_reporting"
    const val TV_ONBOARDING = "tv_onboarding"
    const val SLEEP_TIMER = "sleep_timer"
    const val SKIP_INTRO = "skip_intro"
    const val SUBTITLE_STYLING = "subtitle_styling"
    const val GESTURE_CONTROLS = "gesture_controls"
    const val DATA_SAVER = "data_saver"
}
