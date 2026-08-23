package com.betterstreamflix.analytics

import android.content.Context
import androidx.core.content.edit

/**
 * Analytics manager — collects and reports usage analytics
 * (with privacy opt-in).
 */
object AnalyticsManager {

    private const val PREFS_NAME = "analytics"
    private const val KEY_ENABLED = "analytics_enabled"
    private const val KEY_SESSION_ID = "session_id"

    private var sessionId: String = ""
    private var sessionStartTime: Long = 0

    /**
     * Start a new analytics session.
     */
    fun startSession() {
        sessionId = java.util.UUID.randomUUID().toString()
        sessionStartTime = System.currentTimeMillis()
    }

    /**
     * End the current analytics session.
     */
    fun endSession() {
        sessionId = ""
        sessionStartTime = 0
    }

    /**
     * Track an event.
     */
    fun trackEvent(event: String, params: Map<String, Any?> = emptyMap()) {
        if (!isEnabled()) return
        // In a real implementation, this would queue events for batch submission
        val entry = AnalyticsEvent(
            event = event,
            params = params,
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
        )
        eventQueue.add(entry)
    }

    /**
     * Track a screen view.
     */
    fun trackScreenView(screenName: String) {
        trackEvent("screen_view", mapOf("screen_name" to screenName))
    }

    /**
     * Track a content play.
     */
    fun trackContentPlay(contentId: String, title: String, provider: String) {
        trackEvent("content_play", mapOf(
            "content_id" to contentId,
            "title" to title,
            "provider" to provider,
        ))
    }

    /**
     * Track a search.
     */
    fun trackSearch(query: String, resultCount: Int) {
        trackEvent("search", mapOf(
            "query" to query,
            "result_count" to resultCount,
        ))
    }

    /**
     * Check if analytics is enabled.
     */
    fun isEnabled(): Boolean = enabled

    /**
     * Set analytics enabled.
     */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    private var enabled = false
    private val eventQueue = mutableListOf<AnalyticsEvent>()

    data class AnalyticsEvent(
        val event: String,
        val params: Map<String, Any?>,
        val timestamp: Long,
        val sessionId: String,
    )
}
