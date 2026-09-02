package com.betterstreamflix.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsPrimaryButton
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.compose.theme.BsMotion
import com.betterstreamflix.download.DownloadManager
import com.betterstreamflix.models.Episode

@Composable
fun SeasonScreen(
    seasonTitle: String,
    episodes: List<Episode>,
    query: String,
    onQueryChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String? = null,
    downloadStatusByEpisodeId: Map<String, DownloadManager.DownloadStatus?> = emptyMap(),
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onEpisodeClick: (Episode) -> Unit = {},
    onDownloadEpisode: (Episode) -> Unit = {},
    onDownloadSeason: () -> Unit = {},
    isTvLayout: Boolean = false,
) {
    val filtered = if (query.isBlank()) {
        episodes
    } else {
        episodes.filter { ep ->
            ep.title?.contains(query, ignoreCase = true) == true ||
                ep.number.toString().contains(query)
        }
    }

    BsAtmosphere {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isTvLayout) 32.dp else 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isTvLayout) Arrangement.Center else Arrangement.Start,
            ) {
                if (!isTvLayout) {
                    BsGhostButton(text = "‹", onClick = onBack)
                }
                Column(
                    modifier = Modifier
                        .then(if (isTvLayout) Modifier else Modifier.weight(1f))
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = if (isTvLayout) Alignment.CenterHorizontally else Alignment.Start,
                ) {
                    Text(
                        text = seasonTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = BsColors.Mist,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (episodes.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.season_episode_count, episodes.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = BsColors.MistDim,
                        )
                    }
                }
            }

            if (!isLoading && episodes.isNotEmpty()) {
                if (isTvLayout) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        BsPrimaryButton(
                            text = stringResource(R.string.download_season_all),
                            onClick = onDownloadSeason,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.download_season_all_confirm_short,
                            episodes.size,
                            seasonTitle,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = BsColors.MistFaint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp, vertical = 4.dp),
                    )
                } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BsColors.InkPanel)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = BsColors.Mist),
                        cursorBrush = SolidColor(BsColors.Amber),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    stringResource(R.string.search_episodes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BsColors.MistFaint,
                                )
                            }
                            inner()
                        },
                    )
                    BsPrimaryButton(
                        text = stringResource(R.string.download_season_all_short, episodes.size),
                        onClick = onDownloadSeason,
                    )
                }
                Text(
                    text = stringResource(R.string.download_season_all_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = BsColors.MistFaint,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                }
            }

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
                        verticalArrangement = Arrangement.Center,
                    ) {
                        BsEmptyState(message = errorMessage)
                        BsGhostButton(text = stringResource(R.string.loading_error_retry), onClick = onRetry)
                    }
                }
                filtered.isEmpty() -> {
                    BsEmptyState(
                        message = stringResource(R.string.season_no_episodes_match),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    if (isTvLayout) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(filtered, key = { _, ep -> ep.id }) { _, episode ->
                                SeasonTvEpisodeCard(
                                    episode = episode,
                                    downloadStatus = downloadStatusByEpisodeId[episode.id],
                                    onOpen = { onEpisodeClick(episode) },
                                    onDownload = { onDownloadEpisode(episode) },
                                )
                            }
                        }
                    } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(filtered, key = { _, ep -> ep.id }) { _, episode ->
                            SeasonEpisodeCard(
                                episode = episode,
                                downloadStatus = downloadStatusByEpisodeId[episode.id],
                                onOpen = { onEpisodeClick(episode) },
                                onDownload = { onDownloadEpisode(episode) },
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonEpisodeCard(
    episode: Episode,
    downloadStatus: DownloadManager.DownloadStatus?,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
) {
    val seasonNumber = episode.season?.number ?: 0
    val episodeLabel = when {
        seasonNumber > 0 -> stringResource(R.string.tv_show_item_season_number_episode_number, seasonNumber, episode.number)
        episode.number > 0 -> stringResource(R.string.tv_show_watch_episode, episode.number)
        else -> episode.title.orEmpty()
    }
    val watchProgress = episode.watchHistory?.let { history ->
        if (history.durationMillis > 0L) {
            (history.lastPlaybackPositionMillis.toFloat() / history.durationMillis).coerceIn(0f, 1f)
        } else null
    }
    val downloadLabel = when (downloadStatus) {
        DownloadManager.DownloadStatus.COMPLETED -> stringResource(R.string.download_status_completed)
        DownloadManager.DownloadStatus.DOWNLOADING -> stringResource(R.string.download_status_downloading)
        DownloadManager.DownloadStatus.PENDING -> stringResource(R.string.download_status_pending)
        DownloadManager.DownloadStatus.PAUSED -> stringResource(R.string.download_status_paused)
        DownloadManager.DownloadStatus.FAILED -> stringResource(R.string.download_retry)
        else -> stringResource(R.string.download_episode)
    }
    val downloadEnabled = downloadStatus != DownloadManager.DownloadStatus.COMPLETED &&
        downloadStatus != DownloadManager.DownloadStatus.DOWNLOADING &&
        downloadStatus != DownloadManager.DownloadStatus.PENDING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BsColors.InkPanel)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BsColors.InkSoft),
        ) {
            AsyncImage(
                model = episode.poster ?: episode.tvShow?.poster,
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (watchProgress != null && watchProgress > 0f) {
                LinearProgressIndicator(
                    progress = { watchProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = BsColors.Amber,
                    trackColor = BsColors.Ink,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episodeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = BsColors.AmberBright,
            )
            Text(
                text = episode.title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = BsColors.Mist,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = BsColors.MistDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = downloadLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (downloadEnabled) BsColors.AmberBright else BsColors.MistFaint,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(enabled = downloadEnabled, onClick = onDownload),
            )
        }
    }
}

@Composable
private fun SeasonTvEpisodeCard(
    episode: Episode,
    downloadStatus: DownloadManager.DownloadStatus?,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = BsMotion.FocusSpring,
        label = "tvEpScale",
    )
    val seasonNumber = episode.season?.number ?: 0
    val episodeLabel = when {
        seasonNumber > 0 -> stringResource(R.string.tv_show_item_season_number_episode_number, seasonNumber, episode.number)
        episode.number > 0 -> stringResource(R.string.tv_show_watch_episode, episode.number)
        else -> episode.title.orEmpty()
    }
    val watchProgress = episode.watchHistory?.let { history ->
        if (history.durationMillis > 0L) {
            (history.lastPlaybackPositionMillis.toFloat() / history.durationMillis).coerceIn(0f, 1f)
        } else null
    }
    val downloadLabel = when (downloadStatus) {
        DownloadManager.DownloadStatus.COMPLETED -> stringResource(R.string.download_status_completed)
        DownloadManager.DownloadStatus.DOWNLOADING -> stringResource(R.string.download_status_downloading)
        DownloadManager.DownloadStatus.PENDING -> stringResource(R.string.download_status_pending)
        else -> stringResource(R.string.download_episode)
    }

    Column(
        modifier = Modifier
            .width(200.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(14.dp))
            .background(BsColors.InkPanel)
            .clickable(onClick = onOpen)
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BsColors.InkSoft),
        ) {
            AsyncImage(
                model = episode.poster ?: episode.tvShow?.poster,
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (watchProgress != null && watchProgress > 0f) {
                LinearProgressIndicator(
                    progress = { watchProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = BsColors.Amber,
                    trackColor = BsColors.Ink,
                )
            }
        }
        Text(
            text = episodeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = BsColors.AmberBright,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = episode.title.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            color = BsColors.Mist,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = downloadLabel,
            style = MaterialTheme.typography.labelMedium,
            color = BsColors.MistFaint,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable(onClick = onDownload),
        )
    }
}
