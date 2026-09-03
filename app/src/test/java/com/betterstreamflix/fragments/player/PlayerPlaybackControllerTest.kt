package com.betterstreamflix.fragments.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPlaybackControllerTest {

    @Test
    fun updatePosition_andPlayingState() {
        val controller = PlayerPlaybackController()
        controller.updatePosition(1_500L, 10_000L)
        controller.setPlaying(true)
        controller.setBuffering(true)

        val state = controller.state.value
        assertEquals(1_500L, state.positionMs)
        assertEquals(10_000L, state.durationMs)
        assertTrue(state.isPlaying)
        assertTrue(state.isBuffering)

        controller.setPlaying(false)
        controller.setBuffering(false)
        assertFalse(controller.state.value.isPlaying)
        assertFalse(controller.state.value.isBuffering)
    }
}
