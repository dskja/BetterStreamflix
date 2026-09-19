package com.dskja.betterstreamflix.platform.debrid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebridResolverTest {
    @Test
    fun magnetsRequireConfiguredDebrid() {
        // Without UserPreferences setup, magnets must not be claimed.
        assertFalse(DebridResolver.looksLikeHosterOrMagnet("magnet:?xt=urn:btih:abc"))
    }

    @Test
    fun rejectsPlainStreams() {
        assertFalse(DebridResolver.looksLikeHosterOrMagnet("https://cdn.example.com/video.m3u8"))
        assertFalse(DebridResolver.looksLikeHosterOrMagnet("https://files.example.com/clip.mp4"))
    }

    @Test
    fun providerIdParsing() {
        assertTrue(DebridProviderId.fromId("premiumize") == DebridProviderId.PREMIUMIZE)
        assertTrue(DebridProviderId.fromId("unknown") == DebridProviderId.REAL_DEBRID)
    }
}
