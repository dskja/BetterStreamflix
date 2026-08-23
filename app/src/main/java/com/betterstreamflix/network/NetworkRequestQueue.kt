package com.betterstreamflix.network

import java.util.concurrent.ConcurrentHashMap

/**
 * Network request queue — manages concurrent request limits and
 * request prioritization.
 */
object NetworkRequestQueue {

    private val activeRequests = ConcurrentHashMap<String, RequestInfo>()
    private var maxConcurrentRequests: Int = 10

    data class RequestInfo(
        val url: String,
        val method: String,
        val startTime: Long,
        val priority: Priority,
    )

    enum class Priority { LOW, NORMAL, HIGH, URGENT }

    /**
     * Register a request.
     */
    fun startRequest(url: String, method: String, priority: Priority = Priority.NORMAL): String {
        val requestId = "${url}_${System.nanoTime()}"
        activeRequests[requestId] = RequestInfo(url, method, System.currentTimeMillis(), priority)
        return requestId
    }

    /**
     * Complete a request.
     */
    fun completeRequest(requestId: String) {
        activeRequests.remove(requestId)
    }

    /**
     * Get the number of active requests.
     */
    fun getActiveRequestCount(): Int = activeRequests.size

    /**
     * Check if the queue is at capacity.
     */
    fun isAtCapacity(): Boolean = activeRequests.size >= maxConcurrentRequests

    /**
     * Get all active requests.
     */
    fun getActiveRequests(): List<RequestInfo> = activeRequests.values.toList()

    /**
     * Get active requests by priority.
     */
    fun getRequestsByPriority(priority: Priority): List<RequestInfo> {
        return activeRequests.values.filter { it.priority == priority }
    }

    /**
     * Set max concurrent requests.
     */
    fun setMaxConcurrentRequests(max: Int) {
        maxConcurrentRequests = max
    }

    /**
     * Cancel all pending requests (mark as cancelled).
     */
    fun clearAll() {
        activeRequests.clear()
    }

    /**
     * Get average request duration.
     */
    fun getAverageRequestDuration(): Long {
        if (activeRequests.isEmpty()) return 0
        val now = System.currentTimeMillis()
        return activeRequests.values.map { now - it.startTime }.average().toLong()
    }
}
