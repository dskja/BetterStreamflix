package com.dskja.betterstreamflix.providers

import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture tests from live Chrome scrape of kinoger.fun / kinoger.vip (2026-09-18).
 */
class KinoGerHtmlTest {

    private val abs: (String) -> String = { path ->
        when {
            path.startsWith("http") -> path
            path.startsWith("//") -> "https:$path"
            path.startsWith("/") -> "https://kinoger.fun$path"
            path.startsWith("./") -> "https://kinoger.fun/${path.removePrefix("./")}"
            path.isBlank() -> ""
            else -> "https://kinoger.fun/$path"
        }
    }

    private fun load(name: String) =
        Jsoup.parse(
            javaClass.classLoader!!.getResourceAsStream("kinoger/$name")!!.reader().readText(),
            "https://kinoger.fun/",
        )

    @Test
    fun homeParsesShortsAndCarousel() {
        val doc = load("home.html")
        val shorts = KinoGerHtml.parseShorts(doc, abs)
        val featured = KinoGerHtml.parseFeaturedCarousel(doc, abs)
        assertTrue("expected shorts, got ${shorts.size}", shorts.size >= 8)
        assertTrue("expected carousel, got ${featured.size}", featured.size >= 5)
        assertTrue(shorts.any { it is Movie && it.title.contains("Du und Ich", ignoreCase = true) })
    }

    @Test
    fun shortsUseRealPostersNotPostinfoIcon() {
        val doc = load("home.html")
        val shorts = KinoGerHtml.parseShorts(doc, abs)
        assertTrue(shorts.isNotEmpty())
        shorts.forEach { item ->
            val poster = when (item) {
                is Movie -> item.poster
                is TvShow -> item.poster
                else -> null
            }
            assertTrue("missing poster for $item", !poster.isNullOrBlank())
            assertTrue(
                "junk poster icon leaked: $poster",
                !poster!!.contains("postinfo-icon", ignoreCase = true),
            )
            assertTrue(
                "favicon leaked: $poster",
                !poster.contains("favicon", ignoreCase = true),
            )
        }
    }

    @Test
    fun tvListMarksSeriesCards() {
        val doc = load("tvlist.html")
        val items = KinoGerHtml.parseShorts(doc, abs)
        assertTrue(items.size >= 5)
        assertTrue(items.all { it is TvShow })
        assertTrue(items.any { (it as TvShow).title.contains("MobLand", ignoreCase = true) })
    }

    @Test
    fun moviePageTitleAndHosters() {
        val doc = load("movie.html")
        val title = KinoGerHtml.pageTitle(doc)
        assertTrue(title.contains("Du und Ich"))
        // Must not pick logo h1
        assertTrue(!title.contains("StartSeite", ignoreCase = true))

        val servers = KinoGerHtml.parseMovieServers(doc, abs)
        assertTrue(servers.any { it.first.contains("voe.sx") })
        assertTrue(servers.any { it.first.contains("meinecloud") })
        assertTrue(servers.any { it.first.contains("firestream.site") })
        assertTrue(servers.none { it.first.contains("youtube", ignoreCase = true) })
        assertTrue(servers.none { it.first.contains("/vod/vpn", ignoreCase = true) })
    }

    @Test
    fun staffel2WithSerie1IdsStillYieldsEpisodes() {
        // Live MobLand Staffel 2 page uses serie-1_1 — must not drop episodes.
        val doc = load("series_staffel2.html")
        val title = KinoGerHtml.pageTitle(doc)
        val season = KinoGerHtml.extractStaffelNumber(title)
        assertEquals(2, season)
        val episodes = KinoGerHtml.parseEpisodes(
            showUrl = "https://kinoger.fun/25599-mobland.html",
            seasonNumber = season,
            document = doc,
        )
        assertEquals(1, episodes.size)
        assertEquals(1, episodes.first().number)
        assertTrue(episodes.first().id.contains("#s2e1"))

        val servers = KinoGerHtml.parseEpisodeServers(doc, season = 2, episode = 1, abs)
        assertTrue(servers.any { it.first.contains("voe.sx") })
    }

    @Test
    fun multiEpisodeSeriesParsesAll() {
        val doc = load("series_multi.html")
        val title = KinoGerHtml.pageTitle(doc)
        val season = KinoGerHtml.extractStaffelNumber(title)
        assertEquals(1, season)
        val episodes = KinoGerHtml.parseEpisodes(
            showUrl = "https://kinoger.fun/eberhofer.html",
            seasonNumber = season,
            document = doc,
        )
        assertEquals(9, episodes.size)
        val ep3 = KinoGerHtml.parseEpisodeServers(doc, 1, 3, abs)
        assertTrue(ep3.any { it.first.contains("voe.sx") })
    }

    @Test
    fun searchParsesShortCards() {
        val doc = load("search.html")
        val items = KinoGerHtml.parseShorts(doc, abs)
        assertTrue(items.size >= 5)
        assertTrue(
            items.any {
                when (it) {
                    is Movie -> it.title.contains("Avatar", ignoreCase = true)
                    is TvShow -> it.title.contains("Avatar", ignoreCase = true)
                    else -> false
                }
            },
        )
    }

    @Test
    fun searchUrlIsGet() {
        val url = KinoGerHtml.buildSearchUrl("https://kinoger.fun/", "Avatar", 1)
        assertTrue(url.contains("do=search"))
        assertTrue(url.contains("subaction=search"))
        assertTrue(url.contains("story=Avatar"))
    }
}
