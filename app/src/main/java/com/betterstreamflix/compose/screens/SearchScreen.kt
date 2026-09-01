package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.adapters.AppAdapter
import androidx.compose.foundation.clickable
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsShimmerRow
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
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
    BetterStreamflixTheme {
        Scaffold(topBar = { BsTopBar(title = stringResource(R.string.main_menu_search)) }) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_input_hint)) },
                    singleLine = true,
                )
                if (query.isBlank()) {
                    TextButton(
                        onClick = onBrowseGenres,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.genres_hub_browse))
                    }
                }
                when {
                    isLoading -> BsShimmerRow(modifier = Modifier.padding(top = 16.dp))
                    isEmpty && query.isNotBlank() -> BsEmptyState(
                        message = stringResource(R.string.search_no_results),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    results.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 8.dp),
                        ) {
                            items(results, key = { resultKey(it) }) { item ->
                                Text(
                                    text = resultLabel(item),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onResultClick(item) }
                                        .padding(vertical = 8.dp),
                                )
                            }
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
    else -> item.hashCode().toString()
}

private fun resultLabel(item: AppAdapter.Item): String = when (item) {
    is Movie -> item.title.ifBlank { item.id }
    is TvShow -> item.title.ifBlank { item.id }
    else -> item.toString()
}
