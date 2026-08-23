package com.betterstreamflix.testing

import com.betterstreamflix.i18n.LocaleFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Tests for LocaleFormatter.
 */
class LocaleFormatterTest {

    @Test
    fun `formatNumber with US locale`() {
        val result = LocaleFormatter.formatNumber(1234567, Locale.US)
        assertEquals("1,234,567", result)
    }

    @Test
    fun `formatRating with one decimal`() {
        val result = LocaleFormatter.formatRating(8.567, Locale.US)
        assertEquals("8.6", result)
    }

    @Test
    fun `formatFileSize in GB`() {
        val result = LocaleFormatter.formatFileSize(1_500_000_000, Locale.US)
        assertTrue(result.contains("GB"))
    }

    @Test
    fun `formatFileSize in MB`() {
        val result = LocaleFormatter.formatFileSize(1_500_000, Locale.US)
        assertTrue(result.contains("MB"))
    }

    @Test
    fun `formatFileSize in KB`() {
        val result = LocaleFormatter.formatFileSize(1_500, Locale.US)
        assertTrue(result.contains("KB"))
    }

    @Test
    fun `formatDuration under 1 minute`() {
        assertEquals("0:45", LocaleFormatter.formatDuration(45_000, Locale.US))
    }

    @Test
    fun `formatDuration over 1 hour`() {
        assertEquals("1:30:00", LocaleFormatter.formatDuration(5_400_000, Locale.US))
    }

    @Test
    fun `formatPercent`() {
        assertEquals("50%", LocaleFormatter.formatPercent(0.5f, Locale.US))
    }
}
