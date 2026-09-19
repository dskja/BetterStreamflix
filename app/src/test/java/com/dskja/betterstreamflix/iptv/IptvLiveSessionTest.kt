package com.dskja.betterstreamflix.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IptvLiveSessionTest {

    @Before
    fun reset() {
        IptvLiveSession.clear()
    }

    @Test
    fun zappingMovesPreviousAndNext() {
        IptvLiveSession.remember(
            listOf(
                IptvLiveSession.Channel("1", "One"),
                IptvLiveSession.Channel("2", "Two"),
                IptvLiveSession.Channel("3", "Three"),
            ),
        )
        IptvLiveSession.setCurrent("2")
        assertTrue(IptvLiveSession.hasPrevious())
        assertTrue(IptvLiveSession.hasNext())
        assertEquals("One", IptvLiveSession.previous()?.name)
        assertEquals("Two", IptvLiveSession.next()?.name)
        assertEquals("Three", IptvLiveSession.next()?.name)
        assertFalse(IptvLiveSession.hasNext())
        assertNull(IptvLiveSession.next())
    }

    @Test
    fun toEpisodeTypeBuildsLiveSeason() {
        val episode = IptvLiveSession.toEpisodeType(
            IptvLiveSession.Channel("abc", "CNN", logo = "https://logo"),
        )
        assertEquals("abc", episode.id)
        assertEquals("CNN", episode.tvShow.title)
        assertEquals("Live", episode.season.title)
        assertEquals(1, episode.number)
    }

    @Test
    fun rememberMergesById() {
        IptvLiveSession.remember(listOf(IptvLiveSession.Channel("1", "A")))
        IptvLiveSession.remember(listOf(IptvLiveSession.Channel("1", "A-updated"), IptvLiveSession.Channel("2", "B")))
        assertEquals(2, IptvLiveSession.size())
        IptvLiveSession.setCurrent("1")
        assertEquals("A-updated", IptvLiveSession.current()?.name)
    }
}

class IptvLivePlaybackTest {

    @Test
    fun liveConfigurationHasStableOffsets() {
        val config = IptvLivePlayback.liveConfiguration()
        assertEquals(IptvLivePlayback.TARGET_OFFSET_MS, config.targetOffsetMs)
        assertTrue(config.minOffsetMs > 0)
        assertTrue(config.maxOffsetMs > config.targetOffsetMs)
        assertTrue(config.maxPlaybackSpeed > 1f)
    }
}
