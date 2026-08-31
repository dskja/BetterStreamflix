package com.betterstreamflix.providers

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UserM3uProviderParseTest {

    @Test
    fun parseM3u_extractsNameLogoAndUrl() {
        val body = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="https://cdn.example/logo.png",Demo Channel
            https://stream.example/live.m3u8
            #EXTINF:-1,Another
            http://stream.example/2.ts
        """.trimIndent()

        val entries = UserM3uProvider.parseM3u(body)
        assertThat(entries).hasSize(2)
        assertThat(entries[0].name).isEqualTo("Demo Channel")
        assertThat(entries[0].logo).isEqualTo("https://cdn.example/logo.png")
        assertThat(entries[0].url).isEqualTo("https://stream.example/live.m3u8")
        assertThat(entries[1].name).isEqualTo("Another")
        assertThat(entries[1].logo).isNull()
    }
}
