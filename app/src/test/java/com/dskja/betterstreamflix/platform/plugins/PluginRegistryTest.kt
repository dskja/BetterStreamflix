package com.dskja.betterstreamflix.platform.plugins

import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Genre
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.People
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.providers.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PluginRegistryTest {
    private object FakeProvider : Provider {
        override val baseUrl = "https://example.com"
        override val name = "FakePluginProvider"
        override val logo = ""
        override val language = "en"
        override suspend fun getHome(): List<Category> = emptyList()
        override suspend fun search(query: String, page: Int): List<AppAdapter.Item> = emptyList()
        override suspend fun getMovies(page: Int): List<Movie> = emptyList()
        override suspend fun getTvShows(page: Int): List<TvShow> = emptyList()
        override suspend fun getMovie(id: String): Movie = Movie(id = id)
        override suspend fun getTvShow(id: String): TvShow = TvShow(id = id)
        override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> = emptyList()
        override suspend fun getGenre(id: String, page: Int): Genre = Genre(id, id)
        override suspend fun getPeople(id: String, page: Int): People = People(id, id)
        override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> = emptyList()
        override suspend fun getVideo(server: Video.Server): Video = Video(source = "")
    }

    @Test
    fun registersAndFindsPlugin() {
        PluginRegistry.clear()
        val plugin = BuiltinProviderPlugin(
            FakeProvider,
            Provider.Companion.ProviderSupport(movies = true, tvShows = false),
        )
        PluginRegistry.register(plugin)
        assertNotNull(PluginRegistry.get(plugin.manifest.id))
        assertEquals("FakePluginProvider", PluginRegistry.findByProviderName("FakePluginProvider")?.manifest?.name)
        PluginRegistry.unregister(plugin.manifest.id)
    }
}
