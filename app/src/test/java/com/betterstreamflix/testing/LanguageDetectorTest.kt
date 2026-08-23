package com.betterstreamflix.testing

import com.betterstreamflix.i18n.LanguageDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

/**
 * Tests for LanguageDetector.
 */
class LanguageDetectorTest {

    @Test
    fun `detectBestLanguage for English locale returns en`() {
        val result = LanguageDetector.detectBestLanguage(Locale.ENGLISH)
        assertEquals("en", result)
    }

    @Test
    fun `detectBestLanguage for German locale returns de`() {
        val result = LanguageDetector.detectBestLanguage(Locale.GERMAN)
        assertEquals("de", result)
    }

    @Test
    fun `detectBestLanguage for unsupported returns en`() {
        val result = LanguageDetector.detectBestLanguage(Locale("xx"))
        assertEquals("en", result)
    }

    @Test
    fun `isRtl for Arabic is true`() {
        assertTrue(LanguageDetector.isRtl("ar"))
    }

    @Test
    fun `isRtl for English is false`() {
        assertFalse(LanguageDetector.isRtl("en"))
    }

    @Test
    fun `isRtl for Hebrew is true`() {
        assertTrue(LanguageDetector.isRtl("he"))
    }

    @Test
    fun `getTextDirection for Arabic returns rtl`() {
        assertEquals("rtl", LanguageDetector.getTextDirection("ar"))
    }

    @Test
    fun `getTextDirection for English returns ltr`() {
        assertEquals("ltr", LanguageDetector.getTextDirection("en"))
    }

    @Test
    fun `getProviderLanguage strips region`() {
        assertEquals("en", LanguageDetector.getProviderLanguage("en-US"))
    }
}
