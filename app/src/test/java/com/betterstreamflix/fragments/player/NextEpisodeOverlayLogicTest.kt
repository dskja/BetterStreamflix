package com.betterstreamflix.fragments.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEpisodeOverlayLogicTest {

    @Test
    fun overlayThreshold_respectsMinimumAndBuffer() {
        assertEquals(30_000L, NextEpisodeOverlayLogic.overlayThresholdMs(10L))
        assertEquals(45_000L, NextEpisodeOverlayLogic.overlayThresholdMs(45L))
    }

    @Test
    fun shouldPrefetchNext_withinWindow() {
        assertTrue(NextEpisodeOverlayLogic.shouldPrefetchNext(60_000L))
        assertTrue(NextEpisodeOverlayLogic.shouldPrefetchNext(1L))
        assertFalse(NextEpisodeOverlayLogic.shouldPrefetchNext(60_001L))
    }

    @Test
    fun shouldShowOverlay_gatesOnDismissAndRemaining() {
        assertTrue(
            NextEpisodeOverlayLogic.shouldShowOverlay(
                hasNextEpisode = true,
                remainingMs = 25_000L,
                autoplayBufferSeconds = 20L,
                dismissed = false,
            ),
        )
        assertFalse(
            NextEpisodeOverlayLogic.shouldShowOverlay(
                hasNextEpisode = true,
                remainingMs = 25_000L,
                autoplayBufferSeconds = 20L,
                dismissed = true,
            ),
        )
        assertFalse(
            NextEpisodeOverlayLogic.shouldShowOverlay(
                hasNextEpisode = false,
                remainingMs = 10_000L,
                autoplayBufferSeconds = 30L,
                dismissed = false,
            ),
        )
        assertFalse(
            NextEpisodeOverlayLogic.shouldShowOverlay(
                hasNextEpisode = true,
                remainingMs = 0L,
                autoplayBufferSeconds = 30L,
                dismissed = false,
            ),
        )
        assertFalse(
            NextEpisodeOverlayLogic.shouldShowOverlay(
                hasNextEpisode = true,
                remainingMs = 40_000L,
                autoplayBufferSeconds = 20L,
                dismissed = false,
            ),
        )
    }

    @Test
    fun countdownSeconds_roundsUp() {
        assertEquals(1, NextEpisodeOverlayLogic.countdownSeconds(1L))
        assertEquals(1, NextEpisodeOverlayLogic.countdownSeconds(1000L))
        assertEquals(2, NextEpisodeOverlayLogic.countdownSeconds(1001L))
    }
}
