package com.betterstreamflix.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsSearchResultRow
import com.betterstreamflix.compose.components.BsShimmerRow
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.components.posterOf
import com.betterstreamflix.compose.theme.BsColors
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
    onBack: () -> Unit = {},
    onResultClick: (AppAdapter.Item) -> Unit = {},
    onBrowseGenres: () -> Unit = {},
) {
    BsAtmosphere {
        Column(modifier = Modifier.fillMaxSize()) {
            BsTopBar(
                title = stringResource(R.string.main_menu_search),
                showBrand = true,
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text(stringResource(R.string.search_input_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BsColors.Amber,
                    unfocusedBorderColor = BsColors.Hairline,
                    focusedContainerColor = BsColors.InkPanel,
                    unfocusedContainerColor = BsColors.InkPanel,
                    focusedTextColor = BsColors.Mist,
                    unfocusedTextColor = BsColors.Mist,
                    cursorColor = BsColors.Amber,
                ),
            )
            if (query.isBlank()) {
                BsGhostButton(
                    text = stringResource(R.string.genres_hub_browse),
                    onClick = onBrowseGenres,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
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
                                Text(
                                    text = stringResource(R.string.genres_hub_browse),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = BsColors.Mist,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                            }
                            items(genreResults, key = { "genre:${it.id}" }) { genre ->
                                BsSearchResultRow(
                                    title = genre.name,
                                    subtitle = stringResource(R.string.search_genre_browse),
                                    imageUrl = null,
                                    onClick = { onResultClick(genre) },
                                )
                            }
                        }
                        items(mediaResults, key = { resultKey(it) }) { item ->
                            BsSearchResultRow(
                                title = resultLabel(item),
                                subtitle = resultSubtitle(item),
                                imageUrl = posterOf(item),
                                onClick = { onResultClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resultKey(item: AppAdapter.Item): String = when (item) {
    is Movie -> "movie:${item.id}"
    is TvShow -> "tv:${item.id}"
    is Genre -> "genre:${item.id}"
    else -> item.hashCode().toString()
}

private fun resultLabel(item: AppAdapter.Item): String = when (item) {
    is Movie -> item.title.ifBlank { item.id }
    is TvShow -> item.title.ifBlank { item.id }
    is Genre -> item.name.ifBlank { item.id }
    else -> item.toString()
}

private fun resultSubtitle(item: AppAdapter.Item): String? = when (item) {
    is Movie -> item.providerName
    is TvShow -> item.providerName
    is Genre -> null
    else -> null
}
