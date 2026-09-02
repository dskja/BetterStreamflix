package com.betterstreamflix.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsBrandMark
import com.betterstreamflix.compose.components.BsErrorState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsPrimaryButton
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow

@Composable
fun DetailScreen(
    title: String,
    overview: String?,
    posterUrl: String? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    showDownloadButton: Boolean = true,
    onBack: () -> Unit = {},
    onDownload: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    BsAtmosphere {
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BsColors.Amber)
                }
            }
            errorMessage != null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BsErrorState(message = errorMessage, modifier = Modifier.padding(top = 80.dp))
                    BsGhostButton(text = stringResource(R.string.loading_error_retry), onClick = onRetry)
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .background(BsColors.InkSoft),
                    ) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(BsColors.HeroWash),
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(20.dp),
                        ) {
                            BsBrandMark(compact = true)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.displayMedium,
                                color = BsColors.Mist,
                            )
                        }
                    }
                    if (!overview.isNullOrBlank()) {
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodyLarge,
                            color = BsColors.MistDim,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                    if (showDownloadButton) {
                        BsPrimaryButton(
                            text = stringResource(R.string.downloads_title),
                            onClick = onDownload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    BsGhostButton(
                        text = "Back",
                        onClick = onBack,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Spacer(modifier = Modifier.height(32.dp))
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
        posterUrl = movie.banner ?: movie.poster,
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
        posterUrl = tvShow.banner ?: tvShow.poster,
        onBack = onBack,
        onDownload = onDownload,
    )
}
