package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsPosterCard
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
import com.betterstreamflix.fragments.favorites.FavoritesViewModel
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow

@Composable
fun FavoritesScreen(
    sections: List<FavoritesViewModel.FavoriteSection>,
    sortMode: FavoritesViewModel.SortMode,
    onSortModeChange: (FavoritesViewModel.SortMode) -> Unit = {},
    onMovieClick: (Movie) -> Unit = {},
    onTvShowClick: (TvShow) -> Unit = {},
) {
    BetterStreamflixTheme {
        var showSortMenu by remember { mutableStateOf(false) }
        val hasItems = sections.any { it.items.isNotEmpty() }

        Scaffold(
            topBar = {
                BsTopBar(title = stringResource(R.string.main_menu_favorites))
            },
            floatingActionButton = {},
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (hasItems) {
                    TextButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(stringResource(R.string.favorites_sort_title))
                    }
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

                if (!hasItems) {
                    BsEmptyState(
                        message = stringResource(R.string.favorites_empty),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        sections.filter { it.items.isNotEmpty() }.forEach { section ->
                            item(key = "header-${section.section.key}") {
                                Text(
                                    text = sectionTitle(section.section),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                            items(
                                items = section.items,
                                key = { item -> itemKey(item) ?: item.hashCode().toString() },
                            ) { item ->
                                when (item) {
                                    is Movie -> BsPosterCard(
                                        title = item.title,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { onMovieClick(item) },
                                    )
                                    is TvShow -> BsPosterCard(
                                        title = item.title,
                                        modifier = Modifier.fillMaxWidth(),
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

private fun itemKey(item: AppAdapter.Item): String? = when (item) {
    is Movie -> "movie:${item.id}"
    is TvShow -> "tv:${item.id}"
    else -> null
}
