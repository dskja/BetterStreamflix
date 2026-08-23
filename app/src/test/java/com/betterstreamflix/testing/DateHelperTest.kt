package com.betterstreamflix.testing

import com.betterstreamflix.metadata.DateHelper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for DateHelper utility functions.
 */
class DateHelperTest {

    @Test
    fun `extractYear from ISO date`() {
        assertEquals("1999", DateHelper.extractYear("1999-03-31"))
    }

    @Test
    fun `extractYear from null returns empty`() {
        assertEquals("", DateHelper.extractYear(null))
    }

    @Test
    fun `extractYear from empty returns empty`() {
        assertEquals("", DateHelper.extractYear(""))
    }

    @Test
    fun `formatRuntime 90 minutes`() {
        assertEquals("1h 30m", DateHelper.formatRuntime(90))
    }

    @Test
    fun `formatRuntime 60 minutes`() {
        assertEquals("1h", DateHelper.formatRuntime(60))
    }

    @Test
    fun `formatRuntime 45 minutes`() {
        assertEquals("45m", DateHelper.formatRuntime(45))
    }

    @Test
    fun `formatRuntime 0 minutes`() {
        assertEquals("", DateHelper.formatRuntime(0))
    }

    @Test
    fun `formatDuration milliseconds`() {
        assertEquals("1:30", DateHelper.formatDuration(90_000))
    }

    @Test
    fun `formatDuration with hours`() {
        assertEquals("1:00:00", DateHelper.formatDuration(3_600_000))
    }
}
