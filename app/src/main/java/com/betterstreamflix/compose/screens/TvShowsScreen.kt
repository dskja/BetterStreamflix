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
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsErrorState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsLoadMoreFooter
import com.betterstreamflix.compose.components.BsPosterCard
import com.betterstreamflix.compose.components.BsShimmerRow
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.components.posterOf
import com.betterstreamflix.models.TvShow

@Composable
fun TvShowsScreen(
    shows: List<TvShow> = emptyList(),
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    hasMore: Boolean = false,
    errorMessage: String? = null,
    isTvLayout: Boolean = false,
    onShowClick: (TvShow) -> Unit = {},
    onShowLongClick: (TvShow) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val horizontalPadding = if (isTvLayout) 32.dp else 20.dp
    val gridMinSize = if (isTvLayout) 140.dp else 124.dp
    val gridState = rememberLazyGridState()
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
        Column(modifier = Modifier.fillMaxSize()) {
            BsTopBar(
                title = stringResource(R.string.main_menu_tv_shows),
                showBrand = true,
                horizontalPadding = horizontalPadding,
            )
            when {
                isLoading && shows.isEmpty() -> {
                    Column {
                        BsShimmerRow()
                        BsShimmerRow()
                        BsShimmerRow()
                    }
                }
                errorMessage != null && shows.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BsErrorState(message = errorMessage)
                            BsGhostButton(
                                text = stringResource(R.string.loading_error_retry),
                                onClick = onRetry,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
                shows.isEmpty() && !isLoadingMore -> {
                    BsEmptyState(
                        message = stringResource(R.string.tv_shows_empty),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = gridMinSize),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(shows, key = { it.id }) { show ->
                            BsPosterCard(
                                title = show.title,
                                imageUrl = posterOf(show),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onShowClick(show) },
                                onLongClick = { onShowLongClick(show) },
                            )
                        }
                        if (isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                BsLoadMoreFooter()
                            }
                        }
                    }
                }
            }
        }
    }
}
