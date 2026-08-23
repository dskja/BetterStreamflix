package com.betterstreamflix.analytics

import android.content.Context
import androidx.core.content.edit

/**
 * Debug logger — enhanced logging for debug builds with levels,
 * tags, and persistence.
 */
object DebugLogger {

    private const val PREFS_NAME = "debug_log"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 200

    enum class Level(val priority: Int) {
        VERBOSE(2),
        DEBUG(3),
        INFO(4),
        WARN(5),
        ERROR(6),
    }

    data class LogEntry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val threadName: String,
    )

    private val entries = mutableListOf<LogEntry>()
    private var minLevel: Level = Level.DEBUG

    /**
     * Set the minimum log level.
     */
    fun setMinLevel(level: Level) {
        minLevel = level
    }

    /**
     * Log a message.
     */
    fun log(level: Level, tag: String, message: String) {
        if (level.priority < minLevel.priority) return

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            threadName = Thread.currentThread().name,
        )

        synchronized(entries) {
            entries.add(entry)
            if (entries.size > MAX_ENTRIES) {
                entries.subList(0, entries.size - MAX_ENTRIES).clear()
            }
        }

        // Also log to Android logcat
        android.util.Log.println(level.priority, tag, message)
    }

    fun v(tag: String, message: String) = log(Level.VERBOSE, tag, message)
    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String) = log(Level.WARN, tag, message)
    fun e(tag: String, message: String) = log(Level.ERROR, tag, message)

    /**
     * Get all log entries.
     */
    fun getEntries(): List<LogEntry> = synchronized(entries) { entries.toList() }

    /**
     * Get entries filtered by level.
     */
    fun getEntriesByLevel(minLevel: Level): List<LogEntry> {
        return getEntries().filter { it.level.priority >= minLevel.priority }
    }

    /**
     * Clear all log entries.
     */
    fun clear() {
        synchronized(entries) { entries.clear() }
    }

    /**
     * Export log entries as a formatted string.
     */
    fun exportLog(): String {
        return synchronized(entries) {
            entries.joinToString("\n") { entry ->
                val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                    .format(java.util.Date(entry.timestamp))
                "[${entry.level.name}] $time ${entry.tag}/${entry.threadName}: ${entry.message}"
            }
        }
    }
}
