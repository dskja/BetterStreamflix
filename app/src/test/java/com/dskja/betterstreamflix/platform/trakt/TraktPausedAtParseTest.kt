package com.dskja.betterstreamflix.platform.trakt

import org.junit.Assert.assertTrue
import org.junit.Test

class TraktPausedAtParseTest {
    @Test
    fun parsesIsoPausedAt() {
        val ms = TraktContinueWatching.parsePausedAtString("2024-01-15T12:30:00.000Z")
        assertTrue("expected epoch millis, got $ms", ms > 1_700_000_000_000L)
    }

    @Test
    fun parsesEpochMillisString() {
        assertTrue(TraktContinueWatching.parsePausedAtString("1705321800000") == 1_705_321_800_000L)
    }
}
