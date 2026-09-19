package com.dskja.betterstreamflix.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerPlaybackControllerTest {

    @Test
    fun youtubeVideoId_parsesWatchUrl() {
        assertEquals(
            "dQw4w9WgXcQ",
            TrailerPlaybackController.youtubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
    }

    @Test
    fun youtubeVideoId_parsesShortUrl() {
        assertEquals(
            "dQw4w9WgXcQ",
            TrailerPlaybackController.youtubeVideoId("https://youtu.be/dQw4w9WgXcQ"),
        )
    }

    @Test
    fun youtubeVideoId_parsesEmbedUrl() {
        assertEquals(
            "abcDEF12345",
            TrailerPlaybackController.youtubeVideoId("https://www.youtube.com/embed/abcDEF12345"),
        )
    }

    @Test
    fun youtubeVideoId_returnsNullForUnknown() {
        assertNull(TrailerPlaybackController.youtubeVideoId("https://example.com/video"))
        assertNull(TrailerPlaybackController.youtubeVideoId(""))
    }

    @Test
    fun playerConstants_includeInApp() {
        assertEquals("in_app", TrailerPlaybackController.PLAYER_IN_APP)
        assertEquals("preferred_player", TrailerPlaybackController.KEY_PREFERRED_PLAYER)
    }
}
