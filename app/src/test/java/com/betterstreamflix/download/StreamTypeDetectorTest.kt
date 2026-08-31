package com.betterstreamflix.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamTypeDetectorTest {

    @Test
    fun detect_hlsFromM3u8() {
        assertThat(StreamTypeDetector.detect("https://cdn.example.com/stream.m3u8")).isEqualTo(StreamType.HLS)
    }

    @Test
    fun detect_dashFromMpd() {
        assertThat(StreamTypeDetector.detect("https://cdn.example.com/manifest.mpd")).isEqualTo(StreamType.DASH)
    }

    @Test
    fun detect_httpFallback() {
        assertThat(StreamTypeDetector.detect("https://cdn.example.com/movie.mp4")).isEqualTo(StreamType.HTTP)
    }

    @Test
    fun isDrmProtected_detectsWidevine() {
        assertThat(StreamTypeDetector.isDrmProtected("https://example.com/widevine/stream.mpd")).isTrue()
    }
}
