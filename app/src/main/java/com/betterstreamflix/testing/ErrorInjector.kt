package com.betterstreamflix.testing

import java.util.concurrent.ConcurrentHashMap

/**
 * Error injector — injects errors for testing error handling
 * and resilience.
 */
object ErrorInjector {

    private val errorRates = ConcurrentHashMap<String, Float>()
    private val errorCounts = ConcurrentHashMap<String, Int>()
    private var globalErrorRate: Float = 0f
    private var isEnabled: Boolean = false

    /**
     * Enable error injection.
     */
    fun enable() { isEnabled = true }

    /**
     * Disable error injection.
     */
    fun disable() { isEnabled = false }

    /**
     * Set global error rate.
     */
    fun setGlobalErrorRate(rate: Float) {
        globalErrorRate = rate.coerceIn(0f, 1f)
    }

    /**
     * Set error rate for a specific operation.
     */
    fun setErrorRate(operation: String, rate: Float) {
        errorRates[operation] = rate.coerceIn(0f, 1f)
    }

    /**
     * Check if an error should be injected for an operation.
     */
    fun shouldInjectError(operation: String): Boolean {
        if (!isEnabled) return false

        val rate = errorRates[operation] ?: globalErrorRate
        if (rate <= 0f) return false

        val shouldError = Math.random() < rate
        if (shouldError) {
            errorCounts[operation] = (errorCounts[operation] ?: 0) + 1
        }
        return shouldError
    }

    /**
     * Maybe throw an error for an operation.
     */
    fun maybeThrowError(operation: String, error: Throwable = RuntimeException("Injected error: $operation")) {
        if (shouldInjectError(operation)) throw error
    }

    /**
     * Get error count for an operation.
     */
    fun getErrorCount(operation: String): Int = errorCounts[operation] ?: 0

    /**
     * Get total injected error count.
     */
    fun getTotalErrorCount(): Int = errorCounts.values.sum()

    /**
     * Reset error counts.
     */
    fun resetCounts() {
        errorCounts.clear()
    }

    /**
     * Clear all configuration.
     */
    fun clearAll() {
        errorRates.clear()
        errorCounts.clear()
        globalErrorRate = 0f
        isEnabled = false
    }

    /**
     * Check if error injection is enabled.
     */
    fun isEnabled(): Boolean = isEnabled
}
