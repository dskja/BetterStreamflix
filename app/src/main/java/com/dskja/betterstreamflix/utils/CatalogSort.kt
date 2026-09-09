package com.dskja.betterstreamflix.utils

import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow
import java.util.Calendar

enum class CatalogSortMode {
    DEFAULT,
    LAST_RELEASE,
    ;

    companion object {
        fun fromKey(raw: String?): CatalogSortMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DEFAULT
    }
}

object CatalogSort {
    fun movies(items: List<Movie>, mode: CatalogSortMode = UserPreferences.catalogSortMode): List<Movie> {
        return when (mode) {
            CatalogSortMode.DEFAULT -> items
            CatalogSortMode.LAST_RELEASE -> items.sortedByDescending { it.released?.timeInMillis ?: Long.MIN_VALUE }
        }
    }

    fun tvShows(items: List<TvShow>, mode: CatalogSortMode = UserPreferences.catalogSortMode): List<TvShow> {
        return when (mode) {
            CatalogSortMode.DEFAULT -> items
            CatalogSortMode.LAST_RELEASE -> items.sortedByDescending {
                it.released?.timeInMillis ?: Long.MIN_VALUE
            }
        }
    }

    fun releaseMillis(calendar: Calendar?): Long = calendar?.timeInMillis ?: Long.MIN_VALUE
}
