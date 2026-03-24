package com.streamflixreborn.streamflix.utils

import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.Show
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.providers.TmdbProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object ParentalControlUtils {

    suspend fun filterCategories(categories: List<Category>): List<Category> {
        if (!UserPreferences.isParentalControlActive) return categories

        return categories.mapNotNull { category ->
            val filteredItems = filterItems(category.list)
            if (filteredItems.isEmpty()) {
                null
            } else {
                category.copy(list = filteredItems).also { filteredCategory ->
                    filteredCategory.selectedIndex = category.selectedIndex
                        .coerceAtMost(filteredItems.lastIndex.coerceAtLeast(0))
                    filteredCategory.itemSpacing = category.itemSpacing
                }
            }
        }
    }

    suspend fun filterShows(shows: List<Show>): List<Show> {
        return filterItems(shows)
    }

    suspend fun <T : AppAdapter.Item> filterItems(items: List<T>): List<T> {
        if (!UserPreferences.isParentalControlActive) return items

        return coroutineScope {
            val visibility = items.map { item ->
                async { filterItem(item) != null }
            }.awaitAll()

            items.filterIndexed { index, _ -> visibility[index] }
        }
    }

    private suspend fun filterItem(item: AppAdapter.Item): AppAdapter.Item? {
        return when (item) {
            is Movie -> item.takeIf { isAllowedMovie(it) }
            is TvShow -> item.takeIf { isAllowedTvShow(it) }
            is Episode -> item.takeIf { isAllowedEpisode(it) }
            else -> item
        }
    }

    private suspend fun isAllowedMovie(movie: Movie): Boolean {
        val maxAge = UserPreferences.parentalControlMaxAge ?: return true
        val ageRating = resolveMovieAgeRating(movie) ?: return false
        return ageRating <= maxAge
    }

    private suspend fun isAllowedTvShow(tvShow: TvShow): Boolean {
        val maxAge = UserPreferences.parentalControlMaxAge ?: return true
        val ageRating = resolveTvShowAgeRating(tvShow) ?: return false
        return ageRating <= maxAge
    }

    private suspend fun isAllowedEpisode(episode: Episode): Boolean {
        val tvShow = episode.tvShow ?: return false
        return isAllowedTvShow(tvShow)
    }

    private suspend fun resolveMovieAgeRating(movie: Movie): Int? {
        val provider = resolveProvider(movie.providerName)
        val providerLanguage = provider?.language ?: UserPreferences.currentProvider?.language
        val isTmdbSource = provider is TmdbProvider || (movie.providerName.isNullOrBlank() && UserPreferences.currentProvider is TmdbProvider)

        return when {
            isTmdbSource ->
                movie.id.toIntOrNull()?.let { TmdbUtils.getMovieAgeRatingById(it, providerLanguage) }
                    ?: TmdbUtils.getMovieAgeRating(movie.title, extractYear(movie), providerLanguage)

            else -> TmdbUtils.getMovieAgeRating(movie.title, extractYear(movie), providerLanguage)
        }
    }

    private suspend fun resolveTvShowAgeRating(tvShow: TvShow): Int? {
        val provider = resolveProvider(tvShow.providerName)
        val providerLanguage = provider?.language ?: UserPreferences.currentProvider?.language
        val isTmdbSource = provider is TmdbProvider || (tvShow.providerName.isNullOrBlank() && UserPreferences.currentProvider is TmdbProvider)

        return when {
            isTmdbSource ->
                tvShow.id.toIntOrNull()?.let { TmdbUtils.getTvShowAgeRatingById(it, providerLanguage) }
                    ?: TmdbUtils.getTvShowAgeRating(tvShow.title, extractYear(tvShow), providerLanguage)

            else -> TmdbUtils.getTvShowAgeRating(tvShow.title, extractYear(tvShow), providerLanguage)
        }
    }

    private fun extractYear(movie: Movie): Int? = movie.released?.get(java.util.Calendar.YEAR)

    private fun extractYear(tvShow: TvShow): Int? = tvShow.released?.get(java.util.Calendar.YEAR)

    private fun resolveProvider(providerName: String?): Provider? {
        if (providerName.isNullOrBlank()) return UserPreferences.currentProvider
        if (providerName.startsWith("TMDb (") && providerName.endsWith(")")) {
            return TmdbProvider(providerName.substringAfter("TMDb (").substringBefore(")"))
        }
        return Provider.findByName(providerName)
    }
}
