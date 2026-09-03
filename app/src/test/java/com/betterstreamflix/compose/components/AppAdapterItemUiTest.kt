package com.betterstreamflix.compose.components

import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.models.Genre
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow
import org.junit.Assert.assertEquals
import org.junit.Test

class AppAdapterItemUiTest {

    @Test
    fun itemKeyOf_usesStablePrefixes() {
        val movie = Movie(id = "m1", title = "Alpha")
        val show = TvShow(id = "t1", title = "Beta")
        val genre = Genre(id = "g1", name = "Action")

        assertEquals("movie:m1", itemKeyOf(movie))
        assertEquals("tv:t1", itemKeyOf(show))
        assertEquals("genre:g1", itemKeyOf(genre))
    }

    @Test
    fun itemLabelOf_fallsBackToId() {
        val movie = Movie(id = "m2", title = "")
        assertEquals("m2", itemLabelOf(movie))
        assertEquals(AppAdapter.Type.LOADING_ITEM, movie.itemType)
    }
}
