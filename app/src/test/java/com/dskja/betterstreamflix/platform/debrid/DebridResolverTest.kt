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
    fun detectsCommonHosters() {
        assertTrue(DebridResolver.looksLikeHosterOrMagnet("https://rapidgator.net/file/xyz"))
        assertTrue(DebridResolver.looksLikeHosterOrMagnet("https://1fichier.com/?abc"))
    }

    @Test
    fun rejectsPlainStreams() {
        assertFalse(DebridResolver.looksLikeHosterOrMagnet("https://cdn.example.com/video.m3u8"))
    }
}
