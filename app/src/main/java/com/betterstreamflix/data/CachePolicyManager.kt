package com.betterstreamflix.data

import android.content.Context
import androidx.core.content.edit

/**
 * Cache policy manager — manages cache policies for different data types
 * with configurable TTLs and size limits.
 */
object CachePolicyManager {

    private const val PREFS_NAME = "cache_policies"

    data class CachePolicy(
        val typeName: String,
        val ttlMs: Long,
        val maxEntries: Int,
        val maxDiskSizeMb: Int,
    )

    private val defaultPolicies = mapOf(
        "search_results" to CachePolicy("search_results", 5 * 60 * 1000, 50, 10),
        "metadata" to CachePolicy("metadata", 60 * 60 * 1000, 500, 50),
        "images" to CachePolicy("images", 24 * 60 * 60 * 1000, 1000, 200),
        "html" to CachePolicy("html", 30 * 60 * 1000, 100, 30),
        "api_responses" to CachePolicy("api_responses", 10 * 60 * 1000, 200, 20),
        "provider_data" to CachePolicy("provider_data", 15 * 60 * 1000, 300, 40),
    )

    /**
     * Get the cache policy for a data type.
     */
    fun getPolicy(typeName: String): CachePolicy {
        return defaultPolicies[typeName] ?: CachePolicy(typeName, 5 * 60 * 1000, 100, 10)
    }

    /**
     * Set a custom cache policy.
     */
    fun setPolicy(context: Context, policy: CachePolicy) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putLong("${policy.typeName}_ttl", policy.ttlMs)
            putInt("${policy.typeName}_max_entries", policy.maxEntries)
            putInt("${policy.typeName}_max_disk", policy.maxDiskSizeMb)
        }
    }

    /**
     * Get the effective policy (custom override or default).
     */
    fun getEffectivePolicy(context: Context, typeName: String): CachePolicy {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val default = getPolicy(typeName)
        if (!prefs.contains("${typeName}_ttl")) return default

        return CachePolicy(
            typeName = typeName,
            ttlMs = prefs.getLong("${typeName}_ttl", default.ttlMs),
            maxEntries = prefs.getInt("${typeName}_max_entries", default.maxEntries),
            maxDiskSizeMb = prefs.getInt("${typeName}_max_disk", default.maxDiskSizeMb),
        )
    }

    /**
     * Check if a cache entry is still valid.
     */
    fun isCacheValid(cachedAt: Long, policy: CachePolicy): Boolean {
        return System.currentTimeMillis() - cachedAt < policy.ttlMs
    }

    /**
     * Get all default policies.
     */
    fun getAllDefaultPolicies(): Map<String, CachePolicy> = defaultPolicies.toMap()

    /**
     * Reset all custom policies.
     */
    fun resetPolicies(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }
}
