package com.betterstreamflix.compose.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsGlassFilterChip
import com.betterstreamflix.compose.components.BsPosterCard
import com.betterstreamflix.compose.components.BsSectionHeader
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.components.itemKeyOf
import com.betterstreamflix.fragments.favorites.FavoritesViewModel
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow

@Composable
fun FavoritesScreen(
    sections: List<FavoritesViewModel.FavoriteSection>,
    sortMode: FavoritesViewModel.SortMode,
    isTvLayout: Boolean = false,
    onSortModeChange: (FavoritesViewModel.SortMode) -> Unit = {},
    onMovieClick: (Movie) -> Unit = {},
    onTvShowClick: (TvShow) -> Unit = {},
    onMovieLongClick: (Movie) -> Unit = {},
    onTvShowLongClick: (TvShow) -> Unit = {},
) {
    val hasItems = sections.any { it.items.isNotEmpty() }
    val horizontalPadding = if (isTvLayout) 32.dp else 20.dp

    BsAtmosphere {
        Column(modifier = Modifier.fillMaxSize()) {
            BsTopBar(
                title = stringResource(R.string.main_menu_favorites),
                showBrand = true,
                horizontalPadding = horizontalPadding,
            )

            if (!hasItems) {
                BsEmptyState(
                    message = stringResource(R.string.favorites_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = horizontalPadding, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FavoritesViewModel.SortMode.entries.forEach { mode ->
                        BsGlassFilterChip(
                            label = sortModeLabel(mode),
                            selected = mode == sortMode,
                            onClick = { onSortModeChange(mode) },
                        )
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    sections.filter { it.items.isNotEmpty() }.forEach { section ->
                        item(key = "header-${section.section.key}") {
                            BsSectionHeader(title = sectionTitle(section.section))
                        }
                        item(key = "row-${section.section.key}") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = horizontalPadding),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(
                                    items = section.items,
                                    key = { item -> itemKeyOf(item) },
                                ) { item ->
                                    when (item) {
                                        is Movie -> BsPosterCard(
                                            title = item.title,
                                            imageUrl = item.poster ?: item.banner,
                                            onClick = { onMovieClick(item) },
                                            onLongClick = { onMovieLongClick(item) },
                                        )
                                        is TvShow -> BsPosterCard(
                                            title = item.title,
                                            imageUrl = item.poster ?: item.banner,
                                            onClick = { onTvShowClick(item) },
                                            onLongClick = { onTvShowLongClick(item) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun sectionTitle(section: FavoritesViewModel.Section): String = when (section) {
    FavoritesViewModel.Section.MOVIES -> stringResource(R.string.home_favorite_movies)
    FavoritesViewModel.Section.TV_SHOWS -> stringResource(R.string.home_favorite_tv_shows)
}

@Composable
private fun sortModeLabel(mode: FavoritesViewModel.SortMode): String = when (mode) {
    FavoritesViewModel.SortMode.MANUAL -> stringResource(R.string.favorites_sort_manual)
    FavoritesViewModel.SortMode.RECENTLY_ADDED -> stringResource(R.string.favorites_sort_recent)
    FavoritesViewModel.SortMode.TITLE_ASCENDING -> stringResource(R.string.favorites_sort_title_ascending)
    FavoritesViewModel.SortMode.TITLE_DESCENDING -> stringResource(R.string.favorites_sort_title_descending)
}
