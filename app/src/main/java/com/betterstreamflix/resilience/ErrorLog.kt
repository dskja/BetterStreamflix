package com.betterstreamflix.resilience

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Error log — persists recent errors for the debug panel and diagnostics.
 */
object ErrorLog {

    private const val PREFS_NAME = "error_log"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 100

    data class LogEntry(
        val timestamp: Long,
        val tag: String,
        val level: Level,
        val message: String,
        val throwable: String?,
    )

    enum class Level { INFO, WARNING, ERROR }

    /**
     * Log an error entry.
     */
    fun log(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            tag = tag,
            level = Level.ERROR,
            message = message,
            throwable = throwable?.stackTraceToString(),
        )
        addEntry(context, entry)
    }

    /**
     * Log a warning entry.
     */
    fun warn(context: Context, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            tag = tag,
            level = Level.WARNING,
            message = message,
            throwable = null,
        )
        addEntry(context, entry)
    }

    /**
     * Log an info entry.
     */
    fun info(context: Context, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            tag = tag,
            level = Level.INFO,
            message = message,
            throwable = null,
        )
        addEntry(context, entry)
    }

    /**
     * Get all log entries.
     */
    fun getEntries(context: Context): List<LogEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                LogEntry(
                    timestamp = obj.getLong("timestamp"),
                    tag = obj.getString("tag"),
                    level = Level.valueOf(obj.getString("level")),
                    message = obj.getString("message"),
                    throwable = obj.optString("throwable").ifBlank { null },
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get entries filtered by level.
     */
    fun getEntriesByLevel(context: Context, minLevel: Level): List<LogEntry> {
        val levels = when (minLevel) {
            Level.ERROR -> listOf(Level.ERROR)
            Level.WARNING -> listOf(Level.ERROR, Level.WARNING)
            Level.INFO -> listOf(Level.ERROR, Level.WARNING, Level.INFO)
        }
        return getEntries(context).filter { it.level in levels }
    }

    /**
     * Clear all log entries.
     */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_ENTRIES)
        }
    }

    private fun addEntry(context: Context, entry: LogEntry) {
        val entries = getEntries(context).toMutableList()
        entries.add(0, entry)
        if (entries.size > MAX_ENTRIES) {
            entries.subList(MAX_ENTRIES, entries.size).clear()
        }

        val array = JSONArray()
        entries.forEach { e ->
            array.put(JSONObject().apply {
                put("timestamp", e.timestamp)
                put("tag", e.tag)
                put("level", e.level.name)
                put("message", e.message)
                e.throwable?.let { put("throwable", it) }
            })
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_ENTRIES, array.toString())
        }
    }
}
