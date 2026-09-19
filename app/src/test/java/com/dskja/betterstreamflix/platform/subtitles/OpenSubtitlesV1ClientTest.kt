package com.dskja.betterstreamflix.platform.subtitles

import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSubtitlesV1ClientTest {
    @Test
    fun buildSearchQueryEncodesParams() {
        val q = OpenSubtitlesV1Client.buildSearchQuery(
            tmdbId = 27205,
            imdbId = "tt1375666",
            languages = "en,de",
            season = 1,
            episode = 2,
        )
        assertTrue(q.contains("tmdb_id=27205"))
        assertTrue(q.contains("imdb_id=1375666"))
        assertTrue(q.contains("season_number=1"))
        assertTrue(q.contains("episode_number=2"))
        assertTrue(q.contains("languages="))
    }

    @Test
    fun stripsTtPrefixFromImdb() {
        val q = OpenSubtitlesV1Client.buildSearchQuery(imdbId = "tt123")
        assertTrue(q.contains("imdb_id=123"))
        assertTrue(!q.contains("imdb_id=tt123"))
    }
}
