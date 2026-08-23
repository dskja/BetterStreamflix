package com.betterstreamflix.resilience

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Connection state manager — tracks online/offline state and provides
 * reactive callbacks for UI updates.
 */
object ConnectionStateManager {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline

    private val _connectionQuality = MutableStateFlow(ConnectionQuality.UNKNOWN)
    val connectionQuality: StateFlow<ConnectionQuality> = _connectionQuality

    private var consecutiveFailures = 0
    private var lastSuccessTimestamp = 0L

    /**
     * Record a successful network operation.
     */
    fun recordSuccess() {
        consecutiveFailures = 0
        lastSuccessTimestamp = System.currentTimeMillis()
        _isOnline.value = true
        _connectionQuality.value = ConnectionQuality.GOOD
    }

    /**
     * Record a failed network operation.
     */
    fun recordFailure() {
        consecutiveFailures++
        if (consecutiveFailures >= 3) {
            _isOnline.value = false
            _connectionQuality.value = ConnectionQuality.POOR
        } else if (consecutiveFailures >= 1) {
            _connectionQuality.value = ConnectionQuality.DEGRADED
        }
    }

    /**
     * Force set online state (from system network callback).
     */
    fun setOnline(online: Boolean) {
        _isOnline.value = online
        if (online) {
            consecutiveFailures = 0
            _connectionQuality.value = ConnectionQuality.GOOD
        } else {
            _connectionQuality.value = ConnectionQuality.NONE
        }
    }

    /**
     * Check if we should show offline banner.
     */
    fun shouldShowOfflineBanner(): Boolean {
        return !_isOnline.value || consecutiveFailures >= 3
    }

    enum class ConnectionQuality {
        GOOD,
        DEGRADED,
        POOR,
        NONE,
        UNKNOWN,
    }
}
