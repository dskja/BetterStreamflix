package com.dskja.betterstreamflix.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStatsTest {

    @Test
    fun fromItems_countsStates() {
        val items = listOf(
            entity("a", DownloadItemState.DOWNLOADING),
            entity("b", DownloadItemState.PAUSED),
            entity("c", DownloadItemState.COMPLETED, watched = true),
            entity("d", DownloadItemState.COMPLETED, watched = false),
            entity("e", DownloadItemState.FAILED),
        )
        val stats = DownloadStats.fromItems(items)
        assertEquals(2, stats.active)
        assertEquals(2, stats.completed)
        assertEquals(1, stats.failed)
        assertEquals(1, stats.watched)
        assertEquals(5, stats.total)
        assertTrue(stats.hasWork)
    }

    @Test
    fun fromItems_emptyHasNoWork() {
        val stats = DownloadStats.fromItems(emptyList())
        assertEquals(0, stats.total)
        assertFalse(stats.hasWork)
    }

    @Test
    fun clampSoftLimitGb_boundsAndDefaults() {
        assertEquals(0, DownloadStats.clampSoftLimitGb("0"))
        assertEquals(20, DownloadStats.clampSoftLimitGb("20"))
        assertEquals(500, DownloadStats.clampSoftLimitGb("9999"))
        assertEquals(0, DownloadStats.clampSoftLimitGb("-3"))
    }

    @Test
    fun errorClassifier_rateLimitAndGateway() {
        assertEquals(
            DownloadErrorCode.NETWORK,
            DownloadErrorClassifier.classify(RuntimeException("HTTP 429 Too Many Requests")),
        )
        assertEquals(
            DownloadErrorCode.NETWORK,
            DownloadErrorClassifier.classify(RuntimeException("502 Bad Gateway")),
        )
        assertEquals(
            DownloadErrorCode.EXPIRED,
            DownloadErrorClassifier.classify(RuntimeException("403 Forbidden")),
        )
    }

    private fun entity(
        id: String,
        state: DownloadItemState,
        watched: Boolean = false,
    ) = DownloadItemEntity(
        id = id,
        contentKey = id,
        media3Id = id,
        providerName = "Test",
        kind = DownloadKind.MOVIE.name,
        title = id,
        subtitle = "",
        posterUrl = "",
        videoTypeJson = "{}",
        serverName = "",
        serverId = "",
        qualityLabel = "",
        mimeType = "",
        state = state.name,
        streamUrl = "",
        headersJson = "{}",
        watchedOffline = watched,
    )
}
