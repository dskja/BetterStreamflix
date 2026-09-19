package com.dskja.betterstreamflix.utils

import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Genre
import com.dskja.betterstreamflix.models.People
import com.dskja.betterstreamflix.models.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCatalogPipelineTest {

    private val provider = object : Provider {
        override val baseUrl = "https://example.com"
        override val name = "TestProvider"
        override val logo = ""
        override val language = "en"
        override suspend fun getHome() = emptyList<Category>()
        override suspend fun search(query: String, page: Int) = emptyList<AppAdapter.Item>()
        override suspend fun getMovies(page: Int) = emptyList<Movie>()
        override suspend fun getTvShows(page: Int) = emptyList<TvShow>()
        override suspend fun getMovie(id: String) = Movie(id = id, title = id)
        override suspend fun getTvShow(id: String) = TvShow(id = id, title = id)
        override suspend fun getEpisodesBySeason(seasonId: String) = emptyList<Episode>()
        override suspend fun getGenre(id: String, page: Int) = Genre(id = id, name = "")
        override suspend fun getPeople(id: String, page: Int) = People(id = id, name = "")
        override suspend fun getServers(id: String, videoType: Video.Type) = emptyList<Video.Server>()
        override suspend fun getVideo(server: Video.Server) = Video(source = "")
    }

    @Test
    fun stampsProviderNameAndAbsoluteArtwork() {
        val movie = Movie(
            id = "1",
            title = "One",
            poster = "/img/a.jpg",
            banner = "//cdn.example.com/b.jpg",
        )
        val result = HomeCatalogPipeline.process(
            provider,
            listOf(Category(name = "Latest", list = listOf(movie))),
        )
        val featured = result.categories.first { it.name == Category.FEATURED }
        val stamped = featured.list.filterIsInstance<Movie>().first()
        assertEquals("TestProvider", stamped.providerName)
        assertEquals("https://example.com/img/a.jpg", stamped.poster)
        assertEquals("https://cdn.example.com/b.jpg", stamped.banner)
        assertTrue(result.warnings.any { it.contains("Featured") })
    }

    @Test
    fun dropsEmptyShelvesAndDedupes() {
        val a = Movie(id = "a", title = "A")
        val dup = Movie(id = "a", title = "A again")
        val result = HomeCatalogPipeline.process(
            provider,
            listOf(
                Category(name = Category.FEATURED, list = listOf(a, dup)),
                Category(name = "Empty", list = emptyList()),
                Category(name = "Latest", list = listOf(a)),
            ),
        )
        assertNull(result.categories.find { it.name == "Empty" })
        val featured = result.categories.first { it.name == Category.FEATURED }
        assertEquals(1, featured.list.size)
        assertNull(result.warningText)
    }

    @Test
    fun absoluteUrlHelpers() {
        assertEquals(
            "https://site.test/p.png",
            HomeCatalogPipeline.absoluteUrl("https://site.test/", "p.png"),
        )
        assertEquals(
            "https://cdn.test/x.png",
            HomeCatalogPipeline.absoluteUrl("https://site.test", "//cdn.test/x.png"),
        )
        assertEquals(
            "https://already.test/z.png",
            HomeCatalogPipeline.absoluteUrl("https://site.test", "https://already.test/z.png"),
        )
        assertNull(HomeCatalogPipeline.absoluteUrl("https://site.test", "  "))
        assertNotNull(HomeCatalogPipeline.absoluteUrl("", "relative.jpg"))
    }

    @Test
    fun mergesDuplicateShelfNames() {
        val a = Movie(id = "1", title = "One")
        val b = Movie(id = "2", title = "Two")
        val result = HomeCatalogPipeline.process(
            provider,
            listOf(
                Category(name = Category.FEATURED, list = listOf(a)),
                Category(name = "Hits", list = listOf(a)),
                Category(name = "Hits", list = listOf(b)),
            ),
        )
        val hits = result.categories.filter { it.name == "Hits" }
        assertEquals(1, hits.size)
        assertEquals(2, hits.first().list.size)
    }
}
