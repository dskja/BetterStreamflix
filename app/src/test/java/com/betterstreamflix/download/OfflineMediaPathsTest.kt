package com.betterstreamflix.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OfflineMediaPathsTest {

    @Test
    fun forDownload_and_parse_roundTrip() {
        val path = OfflineMediaPaths.forDownload("abc-123")
        assertThat(path).isEqualTo("media3offline://abc-123")
        assertThat(OfflineMediaPaths.parseDownloadId(path)).isEqualTo("abc-123")
    }

    @Test
    fun parseDownloadId_rejectsPlainPaths() {
        assertThat(OfflineMediaPaths.parseDownloadId("/data/video.mp4")).isNull()
    }
}
