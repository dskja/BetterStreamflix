package com.dskja.betterstreamflix.platform.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSubtitlesBridgeMappingTest {
    @Test
    fun mapsV1HitToLegacySubtitle() {
        val hit = OpenSubtitlesV1Client.SubtitleHit(
            id = "99",
            fileId = 4242,
            language = "de",
            release = "Show.S01E01.1080p",
            downloadCount = 12,
            hearingImpaired = true,
        )
        val legacy = with(OpenSubtitlesBridge) { hit.toLegacySubtitle() }
        assertEquals("opensubtitles.com", legacy.matchedBy)
        assertEquals("4242", legacy.idSubtitleFile)
        assertEquals("de", legacy.languageName)
        assertEquals("1", legacy.subHearingImpaired)
        assertTrue(legacy.subFileName!!.contains("Show"))
    }
}
