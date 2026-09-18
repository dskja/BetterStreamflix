package com.dskja.betterstreamflix.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerienStreamBypassHelperTest {

    @Test
    fun rejectsEmptyAndNoiseOnlyCookies() {
        assertFalse(SerienStreamBypassHelper.looksLikeBypassSolved(""))
        // DuckDuckGo noise must never count as a solved bypass / login.
        assertFalse(SerienStreamBypassHelper.looksLikeBypassSolved("__ddg1=1; other=2"))
        assertFalse(SerienStreamBypassHelper.looksLikeBypassSolved("__ddg1=1; __ddgid=abc"))
    }

    @Test
    fun acceptsClearanceAndSessionAuthCookies() {
        assertTrue(SerienStreamBypassHelper.looksLikeBypassSolved("cf_clearance=xyz; path=/"))
        assertTrue(SerienStreamBypassHelper.looksLikeBypassSolved("PHPSESSID=abc; path=/"))
        assertTrue(
            SerienStreamBypassHelper.looksLikeBypassSolved(
                "__ddg1=noise; cf_clearance=real; PHPSESSID=abc",
            ),
        )
    }

    @Test
    fun sanitizeDropsDdgNoiseButKeepsAuth() {
        val cleaned = SerienStreamBypassHelper.sanitizeSessionCookies(
            "__ddg1=1; cf_clearance=xyz; PHPSESSID=abc; __ddgid=nope",
        )
        assertFalse(cleaned.contains("__ddg", ignoreCase = true))
        assertTrue(cleaned.contains("cf_clearance=xyz"))
        assertTrue(cleaned.contains("PHPSESSID=abc") || cleaned.contains("phpsessid=abc"))
        assertEquals(
            true,
            SerienStreamBypassHelper.looksLikeBypassSolved(cleaned),
        )
    }
}
