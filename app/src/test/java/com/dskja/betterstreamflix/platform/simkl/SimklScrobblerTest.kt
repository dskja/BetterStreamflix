package com.dskja.betterstreamflix.platform.simkl

import org.junit.Assert.assertEquals
import org.junit.Test

class SimklScrobblerTest {
    @Test
    fun startsWhenPlayingPastOnePercent() {
        assertEquals(
            SimklScrobbler.Action.START,
            SimklScrobbler.decide(0.0, 5.0, isPlaying = true, alreadyStarted = false),
        )
    }

    @Test
    fun stopsNearComplete() {
        assertEquals(
            SimklScrobbler.Action.STOP,
            SimklScrobbler.decide(50.0, 85.0, isPlaying = true, alreadyStarted = true),
        )
    }

    @Test
    fun restartsAfterSignificantJump() {
        assertEquals(
            SimklScrobbler.Action.START,
            SimklScrobbler.decide(10.0, 40.0, isPlaying = true, alreadyStarted = true),
        )
    }

    @Test
    fun pausesWhenStoppedMidway() {
        assertEquals(
            SimklScrobbler.Action.PAUSE,
            SimklScrobbler.decide(10.0, 40.0, isPlaying = false, alreadyStarted = true),
        )
    }
}
