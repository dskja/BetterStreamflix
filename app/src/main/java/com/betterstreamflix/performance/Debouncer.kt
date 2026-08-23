package com.betterstreamflix.performance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debouncer — prevents rapid-fire calls by delaying execution
 * until a quiet period is reached.
 */
class Debouncer<T>(
    private val delayMs: Long = 500,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    /**
     * Submit a value to be debounced.
     */
    fun submit(value: T, action: (T) -> Unit) {
        job?.cancel()
        job = scope.launch(Dispatchers.Main) {
            delay(delayMs)
            action(value)
        }
    }

    /**
     * Cancel any pending execution.
     */
    fun cancel() {
        job?.cancel()
        job = null
    }
}

/**
 * Throttler — limits execution rate to one call per period.
 */
class Throttler(
    private val minIntervalMs: Long = 200,
) {
    private var lastExecutionTime = 0L

    /**
     * Attempt to execute. Returns true if executed, false if throttled.
     */
    fun attempt(action: () -> Unit): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastExecutionTime >= minIntervalMs) {
            lastExecutionTime = now
            action()
            return true
        }
        return false
    }

    /**
     * Reset the throttler.
     */
    fun reset() {
        lastExecutionTime = 0L
    }
}
