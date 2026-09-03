package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsPosterCard
import com.betterstreamflix.compose.components.BsSectionHeader
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.components.itemKeyOf
import com.betterstreamflix.compose.theme.BsColors
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
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val hasItems = sections.any { it.items.isNotEmpty() }
    val horizontalPadding = if (isTvLayout) 32.dp else 20.dp

    BsAtmosphere {
        Column(modifier = Modifier.fillMaxSize()) {
            BsTopBar(
                title = stringResource(R.string.main_menu_favorites),
                showBrand = true,
                actions = {
                    if (hasItems) {
                        BsGhostButton(
                            text = stringResource(R.string.favorites_sort_title),
                            onClick = { showSortMenu = true },
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            FavoritesViewModel.SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(sortModeLabel(mode)) },
                                    onClick = {
                                        onSortModeChange(mode)
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                },
            )

            if (!hasItems) {
                BsEmptyState(
                    message = stringResource(R.string.favorites_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
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
                                        )
                                        is TvShow -> BsPosterCard(
                                            title = item.title,
                                            imageUrl = item.poster ?: item.banner,
                                            onClick = { onTvShowClick(item) },
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
