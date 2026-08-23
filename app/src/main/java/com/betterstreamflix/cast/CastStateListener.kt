package com.betterstreamflix.cast

/**
 * Cast state listener — provides callbacks for cast state changes
 * and events.
 */
object CastStateListener {

    private val listeners = mutableListOf<CastStateChangeCallback>()

    fun interface CastStateChangeCallback {
        fun onCastStateChanged(state: CastManager.CastState, device: CastManager.CastDevice?)
    }

    /**
     * Add a state change listener.
     */
    fun addListener(callback: CastStateChangeCallback) {
        listeners.add(callback)
    }

    /**
     * Remove a listener.
     */
    fun removeListener(callback: CastStateChangeCallback) {
        listeners.remove(callback)
    }

    /**
     * Notify all listeners of a state change.
     */
    fun notifyStateChanged(state: CastManager.CastState, device: CastManager.CastDevice?) {
        listeners.forEach { it.onCastStateChanged(state, device) }
    }

    /**
     * Clear all listeners.
     */
    fun clearListeners() {
        listeners.clear()
    }
}
