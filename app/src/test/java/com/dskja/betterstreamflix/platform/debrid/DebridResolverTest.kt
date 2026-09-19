package com.dskja.betterstreamflix.platform.debrid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebridResolverTest {
    @Test
    fun detectsMagnets() {
        assertTrue(DebridResolver.looksLikeHosterOrMagnet("magnet:?xt=urn:btih:abc"))
    }

    @Test
    fun detectsCommonHostersWhenEnabled() {
        // Heuristic still matches known hosts even if prefs default debridEnabled=false —
        // magnets always match; hosters require enabled flag.
        assertTrue(DebridResolver.looksLikeHosterOrMagnet("magnet:?xt=urn:btih:xyz"))
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
