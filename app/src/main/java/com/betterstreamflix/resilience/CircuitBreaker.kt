package com.betterstreamflix.resilience

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Circuit breaker — prevents cascading failures by stopping requests
 * to a failing service after a threshold is reached.
 */
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val recoveryTimeoutMs: Long = 30_000,
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    private val failureCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val circuitStates = ConcurrentHashMap<String, State>()
    private val openedAt = ConcurrentHashMap<String, Long>()

    /**
     * Check if requests are allowed for a given service.
     */
    fun isAllowed(serviceName: String): Boolean {
        val state = getState(serviceName)
        return when (state) {
            State.CLOSED -> true
            State.OPEN -> {
                val openedTimestamp = openedAt[serviceName] ?: 0
                if (System.currentTimeMillis() - openedTimestamp > recoveryTimeoutMs) {
                    circuitStates[serviceName] = State.HALF_OPEN
                    true
                } else {
                    false
                }
            }
            State.HALF_OPEN -> true
        }
    }

    /**
     * Record a successful call.
     */
    fun recordSuccess(serviceName: String) {
        failureCounts.remove(serviceName)
        circuitStates[serviceName] = State.CLOSED
        openedAt.remove(serviceName)
    }

    /**
     * Record a failed call.
     */
    fun recordFailure(serviceName: String) {
        val count = failureCounts.getOrPut(serviceName) { AtomicInteger(0) }.incrementAndGet()
        if (count >= failureThreshold) {
            circuitStates[serviceName] = State.OPEN
            openedAt[serviceName] = System.currentTimeMillis()
        }
    }

    /**
     * Get the current state of the circuit.
     */
    fun getState(serviceName: String): State {
        return circuitStates[serviceName] ?: State.CLOSED
    }

    /**
     * Reset the circuit breaker for a service.
     */
    fun reset(serviceName: String) {
        failureCounts.remove(serviceName)
        circuitStates.remove(serviceName)
        openedAt.remove(serviceName)
    }

    /**
     * Reset all circuits.
     */
    fun resetAll() {
        failureCounts.clear()
        circuitStates.clear()
        openedAt.clear()
    }
}
