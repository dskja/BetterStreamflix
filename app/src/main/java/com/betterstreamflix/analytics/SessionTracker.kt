package com.betterstreamflix.analytics

import android.content.Context
import androidx.core.content.edit

/**
 * Session tracker — tracks user sessions including duration,
 * content watched, and actions taken.
 */
object SessionTracker {

    private const val PREFS_NAME = "session_tracking"

    private var sessionStartTime: Long = 0
    private var sessionActions: Int = 0
    private var sessionContentWatched: Int = 0

    data class SessionSummary(
        val startTime: Long,
        val endTime: Long,
        val durationMs: Long,
        val actionsCount: Int,
        val contentWatched: Int,
    )

    /**
     * Start a new session.
     */
    fun startSession() {
        sessionStartTime = System.currentTimeMillis()
        sessionActions = 0
        sessionContentWatched = 0
    }

    /**
     * Record an action in the current session.
     */
    fun recordAction() {
        sessionActions++
    }

    /**
     * Record content watched in the current session.
     */
    fun recordContentWatched() {
        sessionContentWatched++
    }

    /**
     * End the current session and save summary.
     */
    fun endSession(context: Context): SessionSummary {
        val endTime = System.currentTimeMillis()
        val summary = SessionSummary(
            startTime = sessionStartTime,
            endTime = endTime,
            durationMs = endTime - sessionStartTime,
            actionsCount = sessionActions,
            contentWatched = sessionContentWatched,
        )

        saveSession(context, summary)
        sessionStartTime = 0
        sessionActions = 0
        sessionContentWatched = 0

        return summary
    }

    /**
     * Get session history.
     */
    fun getSessionHistory(context: Context): List<SessionSummary> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString("sessions", null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                SessionSummary(
                    startTime = obj.getLong("startTime"),
                    endTime = obj.getLong("endTime"),
                    durationMs = obj.getLong("durationMs"),
                    actionsCount = obj.getInt("actionsCount"),
                    contentWatched = obj.getInt("contentWatched"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get average session duration.
     */
    fun getAverageSessionDuration(context: Context): Long {
        val sessions = getSessionHistory(context)
        if (sessions.isEmpty()) return 0
        return sessions.map { it.durationMs }.average().toLong()
    }

    private fun saveSession(context: Context, summary: SessionSummary) {
        val history = getSessionHistory(context).toMutableList()
        history.add(summary)
        if (history.size > 30) history.subList(0, history.size - 30).clear()

        val array = org.json.JSONArray()
        history.forEach { s ->
            array.put(org.json.JSONObject().apply {
                put("startTime", s.startTime)
                put("endTime", s.endTime)
                put("durationMs", s.durationMs)
                put("actionsCount", s.actionsCount)
                put("contentWatched", s.contentWatched)
            })
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString("sessions", array.toString())
        }
    }
}
