package com.betterstreamflix.download

import com.betterstreamflix.models.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadActionHelperTitleTest {

    @Test
    fun displayTitle_movie_isPlainTitle() {
        val title = DownloadActionHelper.displayTitle(
            Video.Type.Movie(
                id = "m1",
                title = "Inception",
                releaseDate = "2010-07-16",
                poster = "",
            ),
        )
        assertEquals("Inception", title)
    }

    @Test
    fun displayTitle_episode_includesShowSeasonAndName() {
        val title = DownloadActionHelper.displayTitle(
            Video.Type.Episode(
                id = "e1",
                number = 3,
                title = "Pilot",
                poster = null,
                overview = null,
                tvShow = Video.Type.Episode.TvShow(
                    id = "s1",
                    title = "Breaking Bad",
                    poster = null,
                    banner = null,
                    releaseDate = null,
                ),
                season = Video.Type.Episode.Season(number = 1, title = "Season 1"),
            ),
        )
        assertEquals("Breaking Bad · S01E03 · Pilot", title)
    }

    @Test
    fun displayTitle_episode_withoutEpisodeTitle() {
        val title = DownloadActionHelper.displayTitle(
            Video.Type.Episode(
                id = "e2",
                number = 12,
                title = null,
                poster = null,
                overview = null,
                tvShow = Video.Type.Episode.TvShow(
                    id = "s1",
                    title = "Show",
                    poster = null,
                    banner = null,
                    releaseDate = null,
                ),
                season = Video.Type.Episode.Season(number = 2, title = null),
            ),
        )
        assertEquals("Show · S02E12", title)
    }

    @Test
    fun task_isActive_coversPendingAndDownloading() {
        val pending = DownloadManager.DownloadTask(
            id = "1",
            videoId = "v",
            title = "t",
            providerName = "p",
            url = "u",
            filePath = "f",
            status = DownloadManager.DownloadStatus.PENDING,
        )
        val paused = pending.copy(status = DownloadManager.DownloadStatus.PAUSED)
        assertTrue(pending.isActive)
        assertFalse(paused.isActive)
    }
}
