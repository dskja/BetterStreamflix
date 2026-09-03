package com.betterstreamflix.fragments.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class VideoUrlUtilsTest {

    @Test
    fun decodeBase64Uri_validDataUri() {
        val payload = Base64.getEncoder().encodeToString("#EXTM3U\nhttp://cdn.example/a.m3u8".toByteArray())
        val decoded = VideoUrlUtils.decodeBase64Uri("data:application/vnd.apple.mpegurl;base64,$payload")
        assertEquals("#EXTM3U\nhttp://cdn.example/a.m3u8", decoded)
    }

    @Test
    fun decodeBase64Uri_rejectsPlainUrl() {
        assertNull(VideoUrlUtils.decodeBase64Uri("https://example.com/video.m3u8"))
    }

    @Test
    fun extractUrlFromPlaylist_prefersHttpLine() {
        val playlist = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nhttps://cdn.example/master.m3u8\n"
        assertEquals("https://cdn.example/master.m3u8", VideoUrlUtils.extractUrlFromPlaylist(playlist))
    }

    @Test
    fun extractUrlFromPlaylist_uriAttribute() {
        val line = """#EXT-X-KEY:METHOD=AES-128,URI="https://cdn.example/key.bin""""
        assertEquals("https://cdn.example/key.bin", VideoUrlUtils.extractUrlFromPlaylist(line))
    }
}
