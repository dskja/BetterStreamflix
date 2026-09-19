package com.dskja.betterstreamflix.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CastPlaylistRewriterTest {
    @Test
    fun rewritesRelativeSegmentsThroughProxy() {
        val playlist = """
            #EXTM3U
            #EXTINF:4.0,
            segment0.ts
            #EXTINF:4.0,
            ../other/segment1.ts
        """.trimIndent()
        val out = CastPlaylistRewriter.rewrite(
            playlistText = playlist,
            playlistUrl = "https://cdn.example.com/hls/master.m3u8",
            proxyBase = "http://192.168.1.10:8080",
        )
        assertTrue(out.contains("http://192.168.1.10:8080/p?u="))
        assertTrue(out.contains("segment0.ts") || out.contains("cdn.example.com"))
        assertTrue(out.lines().none { it.trim() == "segment0.ts" })
    }

    @Test
    fun rewritesUriAttributesInTags() {
        val playlist = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.key"
            #EXTINF:4.0,
            https://cdn.example.com/a.ts
        """.trimIndent()
        val out = CastPlaylistRewriter.rewrite(
            playlistText = playlist,
            playlistUrl = "https://cdn.example.com/hls/master.m3u8",
            proxyBase = "http://10.0.0.2:9",
        )
        assertTrue(out.contains("URI=\"http://10.0.0.2:9/p?u="))
        assertTrue(out.contains("http://10.0.0.2:9/p?u="))
    }

    @Test
    fun resolveAgainstKeepsAbsolute() {
        assertEquals(
            "https://cdn.example.com/x.ts",
            CastPlaylistRewriter.resolveAgainst("https://base/a.m3u8", "https://cdn.example.com/x.ts"),
        )
    }
}
