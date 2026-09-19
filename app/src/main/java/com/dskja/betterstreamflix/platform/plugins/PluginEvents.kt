package com.dskja.betterstreamflix.platform.plugins

import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Ring buffer of recent plugin lifecycle / extension events for Settings diagnostics.
 */
object PluginEvents {
    data class Event(
        val atMs: Long = System.currentTimeMillis(),
        val pluginId: String,
        val kind: String,
        val detail: String = "",
    )

    private const val MAX = 64
    private val buffer = ConcurrentLinkedDeque<Event>()

    fun record(pluginId: String, kind: String, detail: String = "") {
        buffer.addFirst(Event(pluginId = pluginId, kind = kind, detail = detail))
        while (buffer.size > MAX) buffer.pollLast()
    }

    fun recent(limit: Int = 20): List<Event> = buffer.take(limit.coerceAtLeast(0))

    fun clear() {
        buffer.clear()
    }
}
