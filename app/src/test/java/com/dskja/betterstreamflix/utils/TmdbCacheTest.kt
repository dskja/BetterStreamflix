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
            contentRating = "R",
            genres = listOf("18" to "Drama"),
            cast = listOf(Triple("287", "Brad Pitt", null)),
            directors = listOf(Triple("1", "David Fincher", null)),
            recommendations = listOf(
                TmdbCache.CachedShowRef(
                    id = 807,
                    isTv = false,
                    title = "Se7en",
                    overview = "o",
                    released = "1995-09-22",
                    rating = 8.3,
                    poster = "p2",
                    banner = "b2",
                ),
            ),
        )
        TmdbCache.putMovie(cached)
        val loaded = TmdbCache.getMovie(550)
        assertEquals("Fight Club", loaded?.title)
        assertEquals("tt0137523", loaded?.imdbId)
        assertEquals("R", loaded?.contentRating)
        assertEquals(1, loaded?.genres?.size)
        assertEquals(1, loaded?.directors?.size)
        assertEquals(1, loaded?.recommendations?.size)
        assertEquals("Se7en", loaded?.recommendations?.first()?.title)
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
            contentRating = "TV-MA",
            seasons = listOf(TmdbCache.SeasonCache(1, "Season 1", null)),
            genres = listOf("18" to "Drama"),
            cast = emptyList(),
            directors = listOf(Triple("9", "Vince Gilligan", null)),
            recommendations = listOf(
                TmdbCache.CachedShowRef(
                    id = 60059,
                    isTv = true,
                    title = "Better Call Saul",
                    overview = "o",
                    released = "2015-02-08",
                    rating = 8.7,
                    poster = "p3",
                    banner = "b3",
                ),
            ),
        )
        TmdbCache.putTv(cached)
        assertEquals("Breaking Bad", TmdbCache.getTv(1396)?.title)
        assertEquals(1, TmdbCache.getTv(1396)?.seasons?.size)
        assertEquals("TV-MA", TmdbCache.getTv(1396)?.contentRating)
        assertEquals(1, TmdbCache.getTv(1396)?.directors?.size)
        assertEquals("Better Call Saul", TmdbCache.getTv(1396)?.recommendations?.first()?.title)
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
