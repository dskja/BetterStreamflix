package com.betterstreamflix.architecture

/**
 * Configuration manager — centralizes app configuration management
 * with defaults, overrides, and persistence.
 */
object ConfigurationManager {

    private val config = mutableMapOf<String, ConfigValue>()
    private val overrides = mutableMapOf<String, ConfigValue>()

    /**
     * Register a default configuration value.
     */
    fun registerDefault(key: String, value: Any, description: String = "") {
        config[key] = ConfigValue(value, description, isOverride = false)
    }

    /**
     * Override a configuration value.
     */
    fun override(key: String, value: Any) {
        overrides[key] = ConfigValue(value, "Override", isOverride = true)
    }

    /**
     * Get a configuration value.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, default: T): T {
        return (overrides[key] ?: config[key])?.value as? T ?: default
    }

    /**
     * Get a string configuration.
     */
    fun getString(key: String, default: String = ""): String = get(key, default)

    /**
     * Get an int configuration.
     */
    fun getInt(key: String, default: Int = 0): Int = get(key, default)

    /**
     * Get a boolean configuration.
     */
    fun getBoolean(key: String, default: Boolean = false): Boolean = get(key, default)

    /**
     * Get a long configuration.
     */
    fun getLong(key: String, default: Long = 0L): Long = get(key, default)

    /**
     * Get a float configuration.
     */
    fun getFloat(key: String, default: Float = 0f): Float = get(key, default)

    /**
     * Remove an override.
     */
    fun removeOverride(key: String) {
        overrides.remove(key)
    }

    /**
     * Clear all overrides.
     */
    fun clearOverrides() {
        overrides.clear()
    }

    /**
     * Get all configuration entries.
     */
    fun getAll(): Map<String, ConfigValue> {
        return config + overrides
    }

    data class ConfigValue(
        val value: Any,
        val description: String,
        val isOverride: Boolean,
    )
}
