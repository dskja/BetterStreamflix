package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsErrorState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsPosterCard
import com.betterstreamflix.compose.components.posterOf
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.models.Genre
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.Show
import com.betterstreamflix.models.TvShow

@Composable
fun GenreScreen(
    genre: Genre?,
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    hasMore: Boolean = false,
    errorMessage: String? = null,
    isTvLayout: Boolean = false,
    onShowClick: (Show) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val shows = genre?.shows.orEmpty()
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && total > 0 && lastVisible >= total - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    BsAtmosphere {
        when {
            isLoading && genre == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BsColors.Amber)
                }
            }
            errorMessage != null && genre == null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BsErrorState(message = errorMessage, modifier = Modifier.fillMaxWidth())
                    BsGhostButton(text = stringResource(R.string.loading_error_retry), onClick = onRetry)
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = genre?.name.orEmpty(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = BsColors.Mist,
                        modifier = Modifier.padding(
                            horizontal = if (isTvLayout) 32.dp else 20.dp,
                            vertical = 16.dp,
                        ),
                    )
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 124.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(shows, key = { showKey(it) }) { show ->
                            val title = when (show) {
                                is Movie -> show.title
                                is TvShow -> show.title
                                else -> ""
                            }
                            BsPosterCard(
                                title = title,
                                imageUrl = posterOf(show),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onShowClick(show) },
                            )
                        }
                        if (isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = BsColors.Amber)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun showKey(show: Show): String = when (show) {
    is Movie -> "movie:${show.id}"
    is TvShow -> "tv:${show.id}"
    else -> show.hashCode().toString()
}
