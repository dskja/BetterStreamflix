package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsErrorState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsPosterCard
import com.betterstreamflix.compose.components.posterOf
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.People
import com.betterstreamflix.models.Show
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.utils.format

@Composable
fun PeopleScreen(
    people: People?,
    fallbackName: String = "",
    fallbackImage: String? = null,
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    hasMore: Boolean = false,
    errorMessage: String? = null,
    isTvLayout: Boolean = false,
    onBack: () -> Unit = {},
    onFilmographyClick: (Show) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val filmography = people?.filmography.orEmpty()
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

    val displayName = people?.name?.takeIf { it.isNotBlank() } ?: fallbackName
    val imageUrl = people?.image ?: fallbackImage

    BsAtmosphere {
        when {
            isLoading && people == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BsColors.Amber)
                }
            }
            errorMessage != null && people == null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BsErrorState(message = errorMessage, modifier = Modifier.fillMaxWidth())
                    BsGhostButton(text = stringResource(R.string.loading_error_retry), onClick = onRetry)
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!isTvLayout) {
                        BsGhostButton(text = "‹", onClick = onBack, modifier = Modifier.padding(12.dp))
                    }
                    PeopleHeader(
                        name = displayName,
                        imageUrl = imageUrl,
                        people = people,
                        modifier = Modifier.padding(horizontal = if (isTvLayout) 32.dp else 20.dp),
                    )
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 124.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(filmography, key = { showKey(it) }) { show ->
                            val title = when (show) {
                                is Movie -> show.title
                                is TvShow -> show.title
                                else -> ""
                            }
                            BsPosterCard(
                                title = title,
                                imageUrl = posterOf(show),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onFilmographyClick(show) },
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

@Composable
private fun PeopleHeader(
    name: String,
    imageUrl: String?,
    people: People?,
    modifier: Modifier = Modifier,
) {
    var bioExpanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(BsColors.InkPanel),
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = BsColors.Mist,
                )
                people?.birthday?.format("MMMM dd, yyyy")?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = BsColors.MistDim)
                }
                people?.placeOfBirth?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = BsColors.MistFaint)
                }
            }
        }
        people?.biography?.takeIf { it.isNotBlank() }?.let { bio ->
            Text(
                text = bio,
                style = MaterialTheme.typography.bodyMedium,
                color = BsColors.MistDim,
                maxLines = if (bioExpanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 14.dp),
            )
            if (bio.length > 200) {
                Text(
                    text = stringResource(if (bioExpanded) R.string.overview_show_less else R.string.overview_show_more),
                    style = MaterialTheme.typography.labelMedium,
                    color = BsColors.AmberBright,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { bioExpanded = !bioExpanded },
                )
            }
        }
    }
}

private fun showKey(show: Show): String = when (show) {
    is Movie -> "movie:${show.id}"
    is TvShow -> "tv:${show.id}"
    else -> show.hashCode().toString()
}
