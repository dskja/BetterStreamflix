package com.betterstreamflix.providers

import com.betterstreamflix.data.Result

/**
 * Provider health monitor — tracks success/failure rates per provider
 * and can disable providers that are consistently failing.
 */
object ProviderHealthMonitor {

    private data class HealthState(
        var consecutiveFailures: Int = 0,
        var totalRequests: Int = 0,
        var totalFailures: Int = 0,
        var lastSuccessTime: Long = 0,
        var lastFailureTime: Long = 0,
        var lastErrorMessage: String? = null,
    )

    private val healthStates = mutableMapOf<String, HealthState>()
    private const val MAX_CONSECUTIVE_FAILURES = 5
    private const val DISABLE_DURATION_MS = 5 * 60 * 1000L // 5 minutes

    /**
     * Record a successful request for a provider.
     */
    fun recordSuccess(providerName: String) {
        synchronized(healthStates) {
            val state = healthStates.getOrPut(providerName) { HealthState() }
            state.consecutiveFailures = 0
            state.totalRequests++
            state.lastSuccessTime = System.currentTimeMillis()
        }
    }

    /**
     * Record a failed request for a provider.
     */
    fun recordFailure(providerName: String, error: String) {
        synchronized(healthStates) {
            val state = healthStates.getOrPut(providerName) { HealthState() }
            state.consecutiveFailures++
            state.totalRequests++
            state.totalFailures++
            state.lastFailureTime = System.currentTimeMillis()
            state.lastErrorMessage = error
        }
    }

    /**
     * Check if a provider is currently healthy enough to use.
     */
    fun isHealthy(providerName: String): Boolean {
        synchronized(healthStates) {
            val state = healthStates[providerName] ?: return true
            if (state.consecutiveFailures < MAX_CONSECUTIVE_FAILURES) return true
            // Check if disable period has passed
            val timeSinceFailure = System.currentTimeMillis() - state.lastFailureTime
            return timeSinceFailure > DISABLE_DURATION_MS
        }
    }

    /**
     * Get health stats for a provider.
     */
    fun getStats(providerName: String): ProviderStats? {
        synchronized(healthStates) {
            val state = healthStates[providerName] ?: return null
            return ProviderStats(
                totalRequests = state.totalRequests,
                totalFailures = state.totalFailures,
                consecutiveFailures = state.consecutiveFailures,
                failureRate = if (state.totalRequests > 0) state.totalFailures.toFloat() / state.totalRequests else 0f,
                lastError = state.lastErrorMessage,
            )
        }
    }

    /**
     * Get all provider health stats.
     */
    fun getAllStats(): Map<String, ProviderStats> {
        synchronized(healthStates) {
            return healthStates.mapValues { (name, state) ->
                ProviderStats(
                    totalRequests = state.totalRequests,
                    totalFailures = state.totalFailures,
                    consecutiveFailures = state.consecutiveFailures,
                    failureRate = if (state.totalRequests > 0) state.totalFailures.toFloat() / state.totalRequests else 0f,
                    lastError = state.lastErrorMessage,
                )
            }
        }
    }

    /**
     * Reset health state for a provider.
     */
    fun reset(providerName: String) {
        synchronized(healthStates) {
            healthStates.remove(providerName)
        }
    }

    /**
     * Reset all health states.
     */
    fun resetAll() {
        synchronized(healthStates) {
            healthStates.clear()
        }
    }
}

/**
 * Health statistics for a provider.
 */
data class ProviderStats(
    val totalRequests: Int,
    val totalFailures: Int,
    val consecutiveFailures: Int,
    val failureRate: Float,
    val lastError: String?,
)
