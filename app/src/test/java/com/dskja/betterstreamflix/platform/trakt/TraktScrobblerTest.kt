package com.dskja.betterstreamflix.platform.trakt

import org.junit.Assert.assertEquals
import org.junit.Test

class TraktScrobblerTest {
    @Test
    fun startsWhenPlayingPastOnePercent() {
        assertEquals(
            TraktScrobbler.Action.START,
            TraktScrobbler.decide(0.0, 5.0, isPlaying = true, alreadyStarted = false),
        )
    }

    @Test
    fun stopsNearComplete() {
        assertEquals(
            TraktScrobbler.Action.STOP,
            TraktScrobbler.decide(50.0, 85.0, isPlaying = true, alreadyStarted = true),
        )
    }

    @Test
    fun pausesWhenStoppedMidway() {
        assertEquals(
            TraktScrobbler.Action.PAUSE,
            TraktScrobbler.decide(10.0, 40.0, isPlaying = false, alreadyStarted = true),
        )
    }

    @Test
    fun noneWhenIdle() {
        assertEquals(
            TraktScrobbler.Action.NONE,
            TraktScrobbler.decide(0.0, 0.0, isPlaying = false, alreadyStarted = false),
        )
    }
}
