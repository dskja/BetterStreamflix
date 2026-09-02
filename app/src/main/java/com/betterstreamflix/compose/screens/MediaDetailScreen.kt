package com.betterstreamflix.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.betterstreamflix.compose.components.BsPrimaryButton
import com.betterstreamflix.compose.components.posterOf
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.People
import com.betterstreamflix.models.Season
import com.betterstreamflix.models.Show
import com.betterstreamflix.models.TvShow

@Composable
fun MediaDetailScreen(
    title: String,
    bannerUrl: String?,
    overview: String?,
    metaLine: String? = null,
    genresLine: String? = null,
    watchLabel: String? = null,
    watchProgress: Float? = null,
    showWatchButton: Boolean = false,
    showDownloadButton: Boolean = false,
    cast: List<People> = emptyList(),
    directors: List<People> = emptyList(),
    seasons: List<Season> = emptyList(),
    recommendations: List<Show> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    isTvLayout: Boolean = false,
    onBack: () -> Unit = {},
    onWatch: () -> Unit = {},
    onDownload: () -> Unit = {},
    onCastClick: (People) -> Unit = {},
    onSeasonClick: (Season) -> Unit = {},
    onRecommendationClick: (Show) -> Unit = {},
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
                            .height(if (isTvLayout) 360.dp else 280.dp)
                            .background(BsColors.InkSoft),
                    ) {
                        AsyncImage(
                            model = bannerUrl,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(BsColors.HeroWash),
                        )
                        if (!isTvLayout) {
                            BsGhostButton(
                                text = "‹",
                                onClick = onBack,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(horizontal = if (isTvLayout) 32.dp else 20.dp, vertical = 16.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = BsColors.Mist,
                        )
                        metaLine?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BsColors.MistDim,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        genresLine?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                color = BsColors.AmberBright,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        ExpandableOverview(overview = overview, modifier = Modifier.padding(top = 12.dp))

                        if (showWatchButton && watchLabel != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            BsPrimaryButton(text = watchLabel, onClick = onWatch)
                            if (watchProgress != null && watchProgress > 0f) {
                                LinearProgressIndicator(
                                    progress = { watchProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                        .height(3.dp),
                                    color = BsColors.Amber,
                                    trackColor = BsColors.Ink,
                                )
                            }
                        }
                        if (showDownloadButton) {
                            Spacer(modifier = Modifier.height(10.dp))
                            BsGhostButton(
                                text = stringResource(R.string.download_episode),
                                onClick = onDownload,
                            )
                        }

                        if (directors.isNotEmpty()) {
                            DetailSectionLabel(stringResource(R.string.movie_directors))
                            Text(
                                text = directors.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodyMedium,
                                color = BsColors.MistDim,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }

                        if (seasons.isNotEmpty()) {
                            DetailSectionLabel(stringResource(R.string.tv_show_seasons))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 8.dp),
                            ) {
                                items(seasons, key = { it.id }) { season ->
                                    SeasonChip(
                                        season = season,
                                        onClick = { onSeasonClick(season) },
                                    )
                                }
                            }
                        }

                        if (cast.isNotEmpty()) {
                            DetailSectionLabel(stringResource(R.string.movie_cast))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 8.dp),
                            ) {
                                items(cast, key = { it.id }) { person ->
                                    CastChip(person = person, onClick = { onCastClick(person) })
                                }
                            }
                        }

                        if (recommendations.isNotEmpty()) {
                            DetailSectionLabel(stringResource(R.string.movie_you_may_also_like))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(recommendations, key = { showKey(it) }) { show ->
                                    val showTitle = when (show) {
                                        is Movie -> show.title
                                        is TvShow -> show.title
                                        else -> ""
                                    }
                                    BsPosterCard(
                                        title = showTitle,
                                        imageUrl = posterOf(show),
                                        onClick = { onRecommendationClick(show) },
                                    )
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
private fun ExpandableOverview(overview: String?, modifier: Modifier = Modifier) {
    if (overview.isNullOrBlank()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = overview,
            style = MaterialTheme.typography.bodyLarge,
            color = BsColors.MistDim,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (overview.length > 180 || overview.lines().size > 4) {
            Text(
                text = stringResource(
                    if (expanded) R.string.overview_show_less else R.string.overview_show_more,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = BsColors.AmberBright,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun DetailSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = BsColors.MistFaint,
        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
    )
}

@Composable
private fun CastChip(person: People, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .focusable()
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = person.image,
            contentDescription = person.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(BsColors.InkPanel),
        )
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelSmall,
            color = BsColors.Mist,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SeasonChip(season: Season, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .focusable()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BsColors.InkPanel),
        ) {
            AsyncImage(
                model = season.poster,
                contentDescription = season.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = season.title?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.season_number, season.number),
            style = MaterialTheme.typography.labelMedium,
            color = BsColors.Mist,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun showKey(show: Show): String = when (show) {
    is Movie -> "movie:${show.id}"
    is TvShow -> "tv:${show.id}"
    else -> show.hashCode().toString()
}
