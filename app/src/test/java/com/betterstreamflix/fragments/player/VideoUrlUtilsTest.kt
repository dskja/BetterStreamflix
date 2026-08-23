package com.betterstreamflix.fragments.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for VideoUrlUtils utility functions.
 */
class VideoUrlUtilsTest {

    @Test
    fun `decodeBase64Uri decodes valid base64 data URI`() {
        val base64Content = java.util.Base64.getEncoder().encodeToString("https://example.com/video.m3u8".toByteArray())
        val uri = "data:application/x-mpegURL;base64,$base64Content"
        val result = VideoUrlUtils.decodeBase64Uri(uri)
        assertEquals("https://example.com/video.m3u8", result)
    }

    @Test
    fun `decodeBase64Uri returns null for non-base64 URI`() {
        val result = VideoUrlUtils.decodeBase64Uri("https://example.com/video.m3u8")
        assertNull(result)
    }

    @Test
    fun `decodeBase64Uri returns null for invalid base64`() {
        val result = VideoUrlUtils.decodeBase64Uri("data:application/x-mpegURL;base64,!!!invalid!!!")
        assertNull(result)
    }

    @Test
    fun `extractUrlFromPlaylist finds direct HTTP URL`() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1280000
            https://example.com/stream.m3u8
        """.trimIndent()
        val result = VideoUrlUtils.extractUrlFromPlaylist(playlist)
        assertEquals("https://example.com/stream.m3u8", result)
    }

    @Test
    fun `extractUrlFromPlaylist finds URI in quotes`() {
        val playlist = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=SUBTITLES,URI="https://example.com/subs.vtt"
        """.trimIndent()
        val result = VideoUrlUtils.extractUrlFromPlaylist(playlist)
        assertEquals("https://example.com/subs.vtt", result)
    }

    @Test
    fun `extractUrlFromPlaylist returns null for empty playlist`() {
        val result = VideoUrlUtils.extractUrlFromPlaylist("")
        assertNull(result)
    }

    @Test
    fun `extractUrlFromPlaylist returns null for playlist without URLs`() {
        val result = VideoUrlUtils.extractUrlFromPlaylist("#EXTM3U\n#EXT-X-ENDLIST")
        assertNull(result)
    }
}
