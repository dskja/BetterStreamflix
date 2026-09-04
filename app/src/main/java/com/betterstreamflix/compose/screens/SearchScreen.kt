package com.betterstreamflix.compose.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsGlassFilterChip
import com.betterstreamflix.compose.components.BsGlassSearchField
import com.betterstreamflix.compose.components.BsSearchResultRow
import com.betterstreamflix.compose.components.BsSectionHeader
import com.betterstreamflix.compose.components.BsShimmerRow
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.components.itemKeyOf
import com.betterstreamflix.compose.components.itemLabelOf
import com.betterstreamflix.compose.components.posterOf
import com.betterstreamflix.models.Genre
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    isLoading: Boolean = false,
    isEmpty: Boolean = false,
    results: List<AppAdapter.Item> = emptyList(),
    recentQueries: List<String> = emptyList(),
    isTvLayout: Boolean = false,
    onBack: () -> Unit = {},
    onResultClick: (AppAdapter.Item) -> Unit = {},
    onResultLongClick: (AppAdapter.Item) -> Unit = {},
    onBrowseGenres: () -> Unit = {},
    onRecentClick: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
) {
    val horizontalPadding = if (isTvLayout) 32.dp else 20.dp

    BsAtmosphere {
        Column(modifier = Modifier.fillMaxSize()) {
            BsTopBar(
                title = stringResource(R.string.main_menu_search),
                showBrand = true,
                horizontalPadding = horizontalPadding,
            )
            BsGlassSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.search_input_hint),
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 8.dp),
            )
            if (query.isBlank()) {
                BsGhostButton(
                    text = stringResource(R.string.genres_hub_browse),
                    onClick = onBrowseGenres,
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 2.dp),
                )
                if (recentQueries.isNotEmpty()) {
                    BsSectionHeader(
                        title = stringResource(R.string.search_recent_title),
                        trailing = {
                            BsGhostButton(
                                text = stringResource(R.string.search_clear_history),
                                onClick = onClearHistory,
                            )
                        },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = horizontalPadding, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        recentQueries.forEach { recent ->
                            BsGlassFilterChip(
                                label = recent,
                                selected = false,
                                onClick = { onRecentClick(recent) },
                            )
                        }
                    }
                }
            }
            when {
                isLoading -> BsShimmerRow(modifier = Modifier.padding(top = 12.dp))
                isEmpty && query.isNotBlank() -> BsEmptyState(
                    message = stringResource(R.string.search_no_results),
                    modifier = Modifier.padding(top = 24.dp),
                )
                results.isNotEmpty() -> {
                    val genreResults = results.filterIsInstance<Genre>()
                    val mediaResults = results.filter { it !is Genre }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp),
                    ) {
                        if (genreResults.isNotEmpty()) {
                            item(key = "browse-genres-header") {
                                BsSectionHeader(title = stringResource(R.string.genres_hub_browse))
                            }
                            items(genreResults, key = { "genre:${it.id}" }) { genre ->
                                BsSearchResultRow(
                                    title = genre.name,
                                    subtitle = stringResource(R.string.search_genre_browse),
                                    imageUrl = null,
                                    isGenre = true,
                                    onClick = { onResultClick(genre) },
                                )
                            }
                        }
                        items(mediaResults, key = { itemKeyOf(it) }) { item ->
                            BsSearchResultRow(
                                title = itemLabelOf(item),
                                subtitle = resultSubtitle(item),
                                imageUrl = posterOf(item),
                                onClick = { onResultClick(item) },
                                onLongClick = { onResultLongClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resultSubtitle(item: AppAdapter.Item): String? = when (item) {
    is Movie -> item.providerName
    is TvShow -> item.providerName
    is Genre -> null
    else -> null
}
