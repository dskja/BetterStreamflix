package com.dskja.betterstreamflix.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TmdbCacheTest {

    @Before
    fun clear() {
        TmdbCache.clear()
    }

    @Test
    fun movieDetailsRoundTrip() {
        val cached = TmdbCache.CachedMovie(
            id = 550,
            title = "Fight Club",
            overview = "overview",
            released = "1999-10-15",
            runtime = 139,
            trailer = "https://youtube.com/watch?v=x",
            rating = 8.4,
            poster = "https://image.tmdb.org/t/p/original/p.jpg",
            banner = "https://image.tmdb.org/t/p/original/b.jpg",
            imdbId = "tt0137523",
            genres = listOf("18" to "Drama"),
            cast = listOf(Triple("287", "Brad Pitt", null)),
        )
        TmdbCache.putMovie(cached)
        val loaded = TmdbCache.getMovie(550)
        assertEquals("Fight Club", loaded?.title)
        assertEquals("tt0137523", loaded?.imdbId)
        assertEquals(1, loaded?.genres?.size)
    }

    @Test
    fun tvDetailsRoundTrip() {
        val cached = TmdbCache.CachedTv(
            id = 1396,
            title = "Breaking Bad",
            overview = "overview",
            released = "2008-01-20",
            trailer = null,
            rating = 8.9,
            poster = "p",
            banner = "b",
            imdbId = "tt0903747",
            seasons = listOf(TmdbCache.SeasonCache(1, "Season 1", null)),
            genres = listOf("18" to "Drama"),
            cast = emptyList(),
        )
        TmdbCache.putTv(cached)
        assertEquals("Breaking Bad", TmdbCache.getTv(1396)?.title)
        assertEquals(1, TmdbCache.getTv(1396)?.seasons?.size)
    }

    @Test
    fun searchAndFindCachesStoreNullMisses() {
        TmdbCache.putSearchMovieId("movie|x", null)
        assertTrue(TmdbCache.hasSearchMovie("movie|x"))
        assertNull(TmdbCache.getSearchMovieId("movie|x"))

        TmdbCache.putFindImdbTv("tt0000001", 42)
        assertTrue(TmdbCache.hasFindImdbTv("tt0000001"))
        assertEquals(42, TmdbCache.getFindImdbTv("tt0000001"))
    }

    @Test
    fun clearWipesAllBuckets() {
        TmdbCache.putSearchTvId("k", 1)
        TmdbCache.putFindImdbMovie("tt1", 2)
        TmdbCache.clear()
        assertFalse(TmdbCache.hasSearchTv("k"))
        assertFalse(TmdbCache.hasFindImdbMovie("tt1"))
    }
}
