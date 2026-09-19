package com.dskja.betterstreamflix.providers

import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Genre
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.People
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video

/**
 * Shared empty / stub responses so providers can fail soft instead of throwing
 * on unsupported surfaces (genre browse, people, empty search pages, …).
 */
object ProviderDefaults {
    fun emptyGenre(id: String): Genre = Genre(id = id, name = "", shows = emptyList())

    fun emptyPeople(id: String): People = People(id = id, name = "", filmography = emptyList())

    fun emptyMovie(id: String, title: String = "", providerName: String? = null): Movie =
        Movie(id = id, title = title.ifBlank { id }, providerName = providerName)

    fun emptyTvShow(id: String, title: String = "", providerName: String? = null): TvShow =
        TvShow(id = id, title = title.ifBlank { id }, providerName = providerName)

    fun emptySearch(): List<AppAdapter.Item> = emptyList()

    fun emptyMovies(): List<Movie> = emptyList()

    fun emptyTvShows(): List<TvShow> = emptyList()

    fun emptyEpisodes(): List<Episode> = emptyList()

    fun emptyHome(): List<Category> = emptyList()

    fun emptyServers(): List<Video.Server> = emptyList()
}
