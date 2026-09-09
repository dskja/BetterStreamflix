package com.dskja.betterstreamflix.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFailoverTest {

    @Test
    fun triesNextServerFirst() {
        val action = PlaybackFailover.decide(
            currentServerIndex = 0,
            serverCount = 3,
            playbackAlreadyStarted = false,
            softwareDecoderAlreadyEnabled = false,
        )
        assertEquals(PlaybackFailover.Action.TryNextServer(1), action)
    }

    @Test
    fun retriesSoftwareWhenServersExhausted() {
        val action = PlaybackFailover.decide(
            currentServerIndex = 2,
            serverCount = 3,
            playbackAlreadyStarted = false,
            softwareDecoderAlreadyEnabled = false,
        )
        assertEquals(PlaybackFailover.Action.RetrySoftwareDecoder, action)
    }

    @Test
    fun givesUpWhenSoftwareAlreadyTried() {
        val action = PlaybackFailover.decide(
            currentServerIndex = 2,
            serverCount = 3,
            playbackAlreadyStarted = false,
            softwareDecoderAlreadyEnabled = true,
        )
        assertEquals(PlaybackFailover.Action.GiveUp, action)
    }

    @Test
    fun midPlaybackPrefersSoftwareThenGiveUp() {
        val soft = PlaybackFailover.decide(
            currentServerIndex = 0,
            serverCount = 3,
            playbackAlreadyStarted = true,
            softwareDecoderAlreadyEnabled = false,
            allowMidPlaybackFailover = false,
        )
        assertEquals(PlaybackFailover.Action.RetrySoftwareDecoder, soft)

        val giveUp = PlaybackFailover.decide(
            currentServerIndex = 0,
            serverCount = 3,
            playbackAlreadyStarted = true,
            softwareDecoderAlreadyEnabled = true,
            allowMidPlaybackFailover = false,
        )
        assertEquals(PlaybackFailover.Action.GiveUp, giveUp)
    }

    @Test
    fun midPlaybackCanStillAdvanceWhenAllowed() {
        val action = PlaybackFailover.decide(
            currentServerIndex = 0,
            serverCount = 2,
            playbackAlreadyStarted = true,
            softwareDecoderAlreadyEnabled = false,
            allowMidPlaybackFailover = true,
        )
        assertTrue(action is PlaybackFailover.Action.TryNextServer)
    }
}
