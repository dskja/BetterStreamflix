package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsErrorState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsPosterCard
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.models.Genre

@Composable
fun GenresHubScreen(
    genres: List<Genre> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    isTvLayout: Boolean = false,
    onGenreClick: (Genre) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val horizontalPadding = if (isTvLayout) 32.dp else 20.dp
    val gridMinSize = if (isTvLayout) 160.dp else 140.dp
    BsAtmosphere {
        Column(modifier = Modifier.fillMaxSize()) {
            BsTopBar(title = stringResource(R.string.genres_hub_browse), showBrand = true)
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BsColors.Amber)
                    }
                }
                errorMessage != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BsErrorState(message = errorMessage, modifier = Modifier.fillMaxWidth())
                        BsGhostButton(
                            text = stringResource(R.string.loading_error_retry),
                            onClick = onRetry,
                        )
                    }
                }
                genres.isEmpty() -> {
                    BsEmptyState(
                        message = stringResource(R.string.genres_hub_empty),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = gridMinSize),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(genres, key = { it.id }) { genre ->
                            BsPosterCard(
                                title = genre.name,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onGenreClick(genre) },
                            )
                        }
                    }
                }
            }
        }
    }
}
