package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsErrorState
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow

@Composable
fun DetailScreen(
    title: String,
    overview: String?,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    showDownloadButton: Boolean = true,
    onBack: () -> Unit = {},
    onDownload: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    BetterStreamflixTheme {
        Scaffold(
            topBar = {
                BsTopBar(title = title.ifBlank { "Details" })
            },
        ) { padding ->
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                    }
                }
                errorMessage != null -> {
                    BsErrorState(
                        message = errorMessage,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        if (!overview.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (showDownloadButton) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onDownload,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.downloads_title))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MovieDetailContent(
    movie: Movie,
    onBack: () -> Unit = {},
    onDownload: () -> Unit = {},
) {
    DetailScreen(
        title = movie.title,
        overview = movie.overview,
        onBack = onBack,
        onDownload = onDownload,
    )
}

@Composable
fun TvShowDetailContent(
    tvShow: TvShow,
    onBack: () -> Unit = {},
    onDownload: () -> Unit = {},
) {
    DetailScreen(
        title = tvShow.title,
        overview = tvShow.overview,
        onBack = onBack,
        onDownload = onDownload,
    )
}
