package com.dskja.betterstreamflix.watchlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistImporterTest {

    private val aniBase = "https://aniworld.to/"
    private val sBase = "https://serienstream.to/"

    @Test
    fun aniWorldExtractsSlugAfterAnimeStream() {
        val id = WatchlistImporter.extractShowId(
            "anime/stream/tomb-raider-king",
            WatchlistImporter.Source.ANIWORLD,
        )
        assertEquals("tomb-raider-king", id)
    }

    @Test
    fun aniWorldIgnoresSeasonEpisodeSuffix() {
        val id = WatchlistImporter.extractShowId(
            "anime/stream/tomb-raider-king/staffel-1/episode-2",
            WatchlistImporter.Source.ANIWORLD,
        )
        assertEquals("tomb-raider-king", id)
    }

    @Test
    fun aniWorldDoesNotReturnStreamAsId() {
        // Regression: old code stripped only "anime/" then took first segment → "stream"
        val html = """
            <div class="seriesListContainer row">
              <div class="col-md-15 col-sm-3 col-xs-6">
                <a href="/anime/stream/black-torch" title="BLACK TORCH stream online">
                  <img src="/public/img/cover/black-torch.png" data-src="/public/img/cover/black-torch.png">
                  <h3>BLACK TORCH<span class="paragraph-end"></span></h3>
                </a>
              </div>
            </div>
        """.trimIndent()
        val items = WatchlistImporter.parseItems(html, aniBase, WatchlistImporter.Source.ANIWORLD)
        assertEquals(1, items.size)
        assertEquals("black-torch", items[0].id)
        assertEquals("BLACK TORCH", items[0].title)
        assertTrue(items[0].poster!!.startsWith("https://aniworld.to/"))
    }

    @Test
    fun serienStreamExtractsSlug() {
        val id = WatchlistImporter.extractShowId(
            "serie/breaking-bad",
            WatchlistImporter.Source.SERIENSTREAM,
        )
        assertEquals("breaking-bad", id)
    }

    @Test
    fun serienStreamParsesCoverCards() {
        val html = """
            <div class="seriesListContainer row">
              <div class="col-md-15">
                <a href="/serie/dark" title="Dark stream online">
                  <img data-src="/public/img/cover/dark.png">
                  <h3>Dark</h3>
                </a>
              </div>
            </div>
        """.trimIndent()
        val items = WatchlistImporter.parseItems(html, sBase, WatchlistImporter.Source.SERIENSTREAM)
        assertEquals(1, items.size)
        assertEquals("dark", items[0].id)
        assertEquals("Dark", items[0].title)
    }

    @Test
    fun skipsFilmLinks() {
        val item = WatchlistImporter.parseItemFromHref(
            href = "/film/some-movie",
            title = "Some Movie",
            poster = null,
            baseUrl = sBase,
            source = WatchlistImporter.Source.SERIENSTREAM,
        )
        assertNull(item)
    }

    @Test
    fun detectsLoginPage() {
        assertTrue(
            WatchlistImporter.looksLikeLoginPage(
                html = """<form><input name="email"><input name="password">Einloggen Passwort</form>""",
                finalUrl = "https://aniworld.to/login",
            ),
        )
        assertFalse(
            WatchlistImporter.looksLikeLoginPage(
                html = """<div class="seriesListContainer"><a href="/anime/stream/x">X</a></div>""",
                finalUrl = "https://aniworld.to/account/watchlist",
            ),
        )
    }

    @Test
    fun findsNextPageFromPagination() {
        val html = """
            <ul class="pagination">
              <li><a href="/account/watchlist?page=1">1</a></li>
              <li><a href="/account/watchlist?page=2" rel="next">»</a></li>
            </ul>
        """.trimIndent()
        val next = WatchlistImporter.findNextPageUrl(
            html,
            "https://aniworld.to/account/watchlist",
            aniBase,
        )
        assertTrue(next!!.contains("page=2"))
    }

    @Test
    fun aniWorldParsesLazySrcsetAndDataHref() {
        val html = """
            <div class="seriesListContainer row">
              <a class="coverListItem" data-href="/anime/stream/one-punch-man" title="One Punch Man">
                <img data-srcset="/public/img/cover/opm.webp 1x" alt="One Punch Man">
              </a>
            </div>
        """.trimIndent()
        val items = WatchlistImporter.parseItems(html, aniBase, WatchlistImporter.Source.ANIWORLD)
        assertEquals(1, items.size)
        assertEquals("one-punch-man", items[0].id)
        assertEquals("One Punch Man", items[0].title)
        assertTrue(items[0].poster!!.contains("opm.webp"))
    }

    @Test
    fun serienStreamSkipsEpisodeDeepLinks() {
        val html = """
            <div class="seriesListContainer">
              <a href="/serie/dark/staffel-1/episode-1">Dark S1E1</a>
              <a href="/serie/dark"><h3>Dark</h3></a>
            </div>
        """.trimIndent()
        val items = WatchlistImporter.parseItems(html, sBase, WatchlistImporter.Source.SERIENSTREAM)
        assertEquals(1, items.size)
        assertEquals("dark", items[0].id)
    }

    @Test
    fun detectsChallengePage() {
        assertTrue(
            WatchlistImporter.looksLikeChallengePage(
                "<html><body>Just a moment... Cloudflare</body></html>",
            ),
        )
        assertFalse(
            WatchlistImporter.looksLikeChallengePage(
                """<div class="seriesListContainer"><a href="/serie/x">X</a></div>""",
            ),
        )
    }

    @Test
    fun watchlistUrlsHonorBaseOverride() {
        val urls = WatchlistImporter.watchlistUrls(
            WatchlistImporter.Source.SERIENSTREAM,
            1,
            "https://serienstream.cx",
        )
        assertTrue(urls.first().startsWith("https://serienstream.cx/"))
    }

    @Test
    fun cleansSeoTitleJunk() {
        val item = WatchlistImporter.parseItemFromHref(
            href = "https://aniworld.to/anime/stream/tomb-raider-king",
            title = "Tomb Raider King stream online alle Staffeln in Deutsch, kostenlos und sofort",
            poster = "/public/img/cover/x.png",
            baseUrl = aniBase,
            source = WatchlistImporter.Source.ANIWORLD,
        )!!
        assertEquals("tomb-raider-king", item.id)
        assertEquals("Tomb Raider King", item.title)
    }
}
