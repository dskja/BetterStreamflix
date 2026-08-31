package com.betterstreamflix.utils

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeepLinkHandlerTest {

    @Test
    fun parse_movieDeepLink() {
        val link = DeepLinkHandler.parse(Uri.parse("betterstreamflix://movie/abc123"))
        assertThat(link).isEqualTo(DeepLink.Movie("abc123"))
    }

    @Test
    fun parse_searchDeepLink() {
        val link = DeepLinkHandler.parse(Uri.parse("betterstreamflix://search/matrix"))
        assertThat(link).isEqualTo(DeepLink.Search("matrix"))
    }

    @Test
    fun parse_rejectsWrongScheme() {
        val link = DeepLinkHandler.parse(Uri.parse("https://example.com/movie/1"))
        assertThat(link).isNull()
    }

    @Test
    fun movieUri_roundTrips() {
        val uri = DeepLinkHandler.movieUri("id42")
        assertThat(DeepLinkHandler.parse(uri)).isEqualTo(DeepLink.Movie("id42"))
    }
}
