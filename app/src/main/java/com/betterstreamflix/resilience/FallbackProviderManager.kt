package com.betterstreamflix.resilience

import com.betterstreamflix.providers.Provider
import com.betterstreamflix.utils.UserPreferences

/**
 * Fallback provider manager — tries alternative providers when the
 * primary provider fails.
 */
object FallbackProviderManager {

    private val circuitBreaker = CircuitBreaker()

    /**
     * Get the ordered list of providers to try, with failed providers deprioritized.
     */
    fun getProviderPriority(): List<Provider> {
        val allProviders = Provider.providers.keys.toList()
        val currentProvider = UserPreferences.currentProvider

        return allProviders.sortedWith(
            compareBy<Provider> { provider ->
                if (provider == currentProvider) 0
                else if (circuitBreaker.getState(provider.name) == CircuitBreaker.State.OPEN) 2
                else 1
            }.thenBy { it.name },
        )
    }

    /**
     * Check if a provider is available (circuit not open).
     */
    fun isProviderAvailable(providerName: String): Boolean {
        return circuitBreaker.isAllowed(providerName)
    }

    /**
     * Record a provider success.
     */
    fun recordProviderSuccess(providerName: String) {
        circuitBreaker.recordSuccess(providerName)
    }

    /**
    * Record a provider failure.
     */
    fun recordProviderFailure(providerName: String) {
        circuitBreaker.recordFailure(providerName)
    }

    /**
     * Get the circuit breaker state for a provider.
     */
    fun getProviderState(providerName: String): CircuitBreaker.State {
        return circuitBreaker.getState(providerName)
    }

    /**
     * Reset all provider circuits.
     */
    fun resetAll() {
        circuitBreaker.resetAll()
    }
}
