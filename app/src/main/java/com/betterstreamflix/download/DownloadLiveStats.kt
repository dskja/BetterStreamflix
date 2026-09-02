package com.betterstreamflix.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory live download telemetry (speed, ETA inputs) for the Downloads UI.
 */
object DownloadLiveStats {

    private data class Sample(val bytes: Long, val timeMs: Long)

    private val samples = ConcurrentHashMap<String, Sample>()
    private val speedById = ConcurrentHashMap<String, Long>()
    private val _speeds = MutableStateFlow<Map<String, Long>>(emptyMap())
    val speeds: StateFlow<Map<String, Long>> = _speeds.asStateFlow()

    fun record(downloadId: String, downloadedBytes: Long) {
        val now = System.currentTimeMillis()
        val previous = samples[downloadId]
        if (previous != null && now > previous.timeMs) {
            val deltaBytes = downloadedBytes - previous.bytes
            val deltaMs = now - previous.timeMs
            if (deltaBytes >= 0 && deltaMs >= 250) {
                val speed = deltaBytes * 1000L / deltaMs
                speedById[downloadId] = speed
                _speeds.value = speedById.toMap()
            }
        }
        samples[downloadId] = Sample(downloadedBytes, now)
    }

    fun speedFor(downloadId: String): Long = speedById[downloadId] ?: 0L

    fun etaSeconds(downloadId: String, downloadedBytes: Long, totalBytes: Long): Long {
        val speed = speedFor(downloadId)
        if (speed <= 0L || totalBytes <= 0L) return 0L
        val remaining = (totalBytes - downloadedBytes).coerceAtLeast(0L)
        return remaining / speed
    }

    fun clear(downloadId: String) {
        samples.remove(downloadId)
        speedById.remove(downloadId)
        _speeds.value = speedById.toMap()
    }
}
