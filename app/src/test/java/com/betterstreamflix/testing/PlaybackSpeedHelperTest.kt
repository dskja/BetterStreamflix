package com.betterstreamflix.testing

import com.betterstreamflix.player.PlaybackSpeedHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tests for PlaybackSpeedHelper.
 */
class PlaybackSpeedHelperTest {

    @Test
    fun `formatSpeed for 1,0x`() {
        assertEquals("1.0x", PlaybackSpeedHelper.formatSpeed(1.0f))
    }

    @Test
    fun `formatSpeed for 1,5x`() {
        assertEquals("1.5x", PlaybackSpeedHelper.formatSpeed(1.5f))
    }

    @Test
    fun `formatSpeed for 2,0x`() {
        assertEquals("2.0x", PlaybackSpeedHelper.formatSpeed(2.0f))
    }

    @Test
    fun `isDefaultSpeed for 1,0 is true`() {
        assertTrue(PlaybackSpeedHelper.isDefaultSpeed(1.0f))
    }

    @Test
    fun `isDefaultSpeed for 1,5 is false`() {
        assertFalse(PlaybackSpeedHelper.isDefaultSpeed(1.5f))
    }

    @Test
    fun `getNextSpeed from 1,0 returns 1,25`() {
        assertEquals(1.25f, PlaybackSpeedHelper.getNextSpeed(1.0f), 0.001f)
    }

    @Test
    fun `getNextSpeed from 3,0 returns 3,0 (max)`() {
        assertEquals(3.0f, PlaybackSpeedHelper.getNextSpeed(3.0f), 0.001f)
    }

    @Test
    fun `getPreviousSpeed from 1,0 returns 0,75`() {
        assertEquals(0.75f, PlaybackSpeedHelper.getPreviousSpeed(1.0f), 0.001f)
    }

    @Test
    fun `getPreviousSpeed from 0,25 returns 0,25 (min)`() {
        assertEquals(0.25f, PlaybackSpeedHelper.getPreviousSpeed(0.25f), 0.001f)
    }

    @Test
    fun `SPEED_PRESETS contains 1,0`() {
        assertTrue(PlaybackSpeedHelper.SPEED_PRESETS.contains(1.0f))
    }

    @Test
    fun `SPEED_PRESETS is sorted ascending`() {
        val sorted = PlaybackSpeedHelper.SPEED_PRESETS.sorted()
        assertEquals(sorted, PlaybackSpeedHelper.SPEED_PRESETS)
    }
}
