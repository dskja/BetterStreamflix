package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsErrorState
import com.betterstreamflix.compose.components.BsPosterCard
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
import com.betterstreamflix.models.Genre

@Composable
fun GenresHubScreen(
    genres: List<Genre> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onGenreClick: (Genre) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    BetterStreamflixTheme {
        Scaffold(topBar = { BsTopBar(title = stringResource(R.string.genres_hub_browse)) }) { padding ->
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(32.dp),
                    )
                }
                errorMessage != null -> {
                    BsErrorState(
                        message = errorMessage,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
                genres.isEmpty() -> {
                    BsEmptyState(
                        message = stringResource(R.string.search_no_results),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
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
