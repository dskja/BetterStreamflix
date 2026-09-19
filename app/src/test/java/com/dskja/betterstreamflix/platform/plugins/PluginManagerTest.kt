package com.dskja.betterstreamflix.platform.plugins

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PluginManagerTest {

    @Before
    fun reset() {
        PluginRegistry.clear()
        PluginEvents.clear()
    }

    @Test
    fun demoAddonRegistersWithExpandedCaps() {
        DemoAddonPlugin.register()
        val plugin = PluginRegistry.get(DemoAddonPlugin.ID)
        requireNotNull(plugin)
        assertEquals("Demo Addon", plugin.manifest.name)
        assertTrue(plugin.manifest.capabilities.homeContribution)
        assertTrue(plugin.manifest.capabilities.searchContribution)
        assertTrue(plugin.manifest.capabilities.metadataEnrichment)
        assertTrue(plugin.manifest.capabilities.playbackHooks)
        assertTrue(plugin.manifest.capabilities.settings)
        assertFalse(plugin.manifest.capabilities.movies)
        assertTrue(plugin is HomeContributionPlugin)
        assertTrue(plugin is SearchContributionPlugin)
        assertTrue(plugin is PlaybackHookPlugin)
        assertTrue(plugin is SettingsContributionPlugin)
    }

    @Test
    fun registerKeepsDemoAndLocalSideBySide() {
        DemoAddonPlugin.register()
        PluginRegistry.register(
            object : SourcePlugin {
                override val manifest = PluginManifest(
                    id = "local:test",
                    name = "Test Local",
                    version = "1",
                    language = "en",
                    source = PluginManifest.Source.LOCAL,
                )
                override fun createProvider() = error("unused")
                override fun isEnabled() = false
            },
        )
        // Soft re-register must not drop either entry (putIfAbsent / overwrite by id only).
        DemoAddonPlugin.register()
        assertTrue(PluginRegistry.get(DemoAddonPlugin.ID) != null)
        assertTrue(PluginRegistry.get("local:test") != null)
        assertEquals(2, PluginRegistry.size)
    }

    @Test
    fun catalogParsesCapabilitiesAndRemoteEntries() {
        val raw = """
            {"plugins":[
              {"id":"builtin:demo-addon","name":"Demo","version":"1","source":"builtin",
               "capabilities":["home","search","playback"]},
              {"id":"remote:x","name":"Remote","version":2,"source":"remote","description":"stub"}
            ]}
        """.trimIndent()
        val entries = PluginCatalog.parse(raw)
        assertEquals(2, entries.size)
        assertEquals(PluginManifest.Source.BUILTIN, entries[0].source)
        assertTrue(entries[0].capabilities!!.homeContribution)
        assertTrue(entries[0].capabilities!!.searchContribution)
        assertEquals(PluginManifest.Source.REMOTE, entries[1].source)
        assertEquals("2", entries[1].version)
    }

    @Test
    fun capabilitiesFromLabelsMapsKnownHooks() {
        val caps = PluginManifest.capabilitiesFromLabels(listOf("home", "metadata", "subtitles"))
        assertTrue(caps.homeContribution)
        assertTrue(caps.metadataEnrichment)
        assertTrue(caps.subtitles)
        assertFalse(caps.playbackHooks)
    }

    @Test
    fun pluginEventsRingBufferKeepsRecent() {
        repeat(80) { PluginEvents.record("p", "kind$it") }
        assertEquals(64, PluginEvents.recent(100).size)
        assertEquals("kind79", PluginEvents.recent(1).first().kind)
    }

    @Test
    fun demoAddonSearchOnlyForPluginQueries() = runBlocking {
        val provider = object : com.dskja.betterstreamflix.providers.Provider {
            override val name = "Test"
            override val baseUrl = "https://example.com"
            override val logo = ""
            override val language = "en"
            override suspend fun getHome() = emptyList<com.dskja.betterstreamflix.models.Category>()
            override suspend fun search(query: String, page: Int) =
                emptyList<com.dskja.betterstreamflix.adapters.AppAdapter.Item>()
            override suspend fun getMovies(page: Int) =
                emptyList<com.dskja.betterstreamflix.models.Movie>()
            override suspend fun getTvShows(page: Int) =
                emptyList<com.dskja.betterstreamflix.models.TvShow>()
            override suspend fun getMovie(id: String) = error("n/a")
            override suspend fun getTvShow(id: String) = error("n/a")
            override suspend fun getEpisodesBySeason(seasonId: String) = error("n/a")
            override suspend fun getGenre(id: String, page: Int) = error("n/a")
            override suspend fun getPeople(id: String, page: Int) = error("n/a")
            override suspend fun getServers(
                id: String,
                videoType: com.dskja.betterstreamflix.models.Video.Type,
            ) = emptyList<com.dskja.betterstreamflix.models.Video.Server>()
            override suspend fun getVideo(server: com.dskja.betterstreamflix.models.Video.Server) =
                error("n/a")
        }
        val hits = DemoAddonPlugin.search(provider, "plugin system", 1)
        assertEquals(1, hits.size)
        assertTrue(DemoAddonPlugin.search(provider, "matrix", 1).isEmpty())
    }
}
