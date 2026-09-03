package com.betterstreamflix.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadLiveStatsTest {

    @Before
    fun setUp() {
        DownloadLiveStats.clear("dl-1")
    }

    @Test
    fun etaSeconds_zeroWhenNoSpeed() {
        assertEquals(0L, DownloadLiveStats.etaSeconds("dl-1", 100L, 1000L))
    }

    @Test
    fun record_computesPositiveSpeed() {
        DownloadLiveStats.record("dl-1", 0L)
        Thread.sleep(400)
        DownloadLiveStats.record("dl-1", 400_000L)
        val speed = DownloadLiveStats.speedFor("dl-1")
        // Second sample after >=250ms should produce a positive B/s reading.
        assertTrue("expected positive speed, got $speed", speed > 0L)
        val eta = DownloadLiveStats.etaSeconds("dl-1", 400_000L, 800_000L)
        assertTrue(eta >= 0L)
    }

    @Test
    fun clear_removesSample() {
        DownloadLiveStats.record("dl-1", 10L)
        DownloadLiveStats.clear("dl-1")
        assertEquals(0L, DownloadLiveStats.speedFor("dl-1"))
    }
}
