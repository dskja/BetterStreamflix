package com.dskja.betterstreamflix.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerienStreamDomainTest {

    @Test
    fun candidateDomains_prefersConfiguredThenKnownGoodMirrors() {
        val candidates = SerienStreamProvider.candidateDomains("serienstream.cx")
        assertEquals("serienstream.cx", candidates.first())
        assertTrue(candidates.contains("serienstream.to"))
        assertFalse(candidates.contains("s.to"))
        assertFalse(candidates.contains("serienstream.sx"))
    }

    @Test
    fun candidateDomains_normalizesProtocolAndWww() {
        val candidates = SerienStreamProvider.candidateDomains("https://www.serienstream.to/")
        assertEquals("serienstream.to", candidates.first())
        assertEquals(1, candidates.count { it == "serienstream.to" })
    }

    @Test
    fun isSerienStreamHost_acceptsKnownMirrors() {
        assertTrue(SerienStreamProvider.isSerienStreamHost("https://serienstream.to/serie/foo"))
        assertTrue(SerienStreamProvider.isSerienStreamHost("serienstream.cx"))
        assertTrue(SerienStreamProvider.isSerienStreamHost("https://challenges.cloudflare.com/cdn-cgi/challenge"))
    }
}
