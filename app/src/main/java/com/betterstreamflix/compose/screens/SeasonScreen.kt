package com.betterstreamflix.compose.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsErrorState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsGlassPanel
import com.betterstreamflix.compose.components.BsGlassSearchField
import com.betterstreamflix.compose.components.BsPrimaryButton
import com.betterstreamflix.compose.components.BsProgressBar
import com.betterstreamflix.compose.components.BsShimmerRow
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BsMotion
import com.betterstreamflix.compose.theme.BsTheme
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
    onEpisodeLongClick: (Episode) -> Unit = {},
    onDownloadEpisode: (Episode) -> Unit = {},
    onDownloadSeason: () -> Unit = {},
    isTvLayout: Boolean = false,
) {
    val horizontalPadding = if (isTvLayout) 32.dp else 20.dp
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
            BsTopBar(
                title = seasonTitle,
                subtitle = if (episodes.isNotEmpty()) {
                    stringResource(R.string.season_episode_count, episodes.size)
                } else {
                    null
                },
                onBack = if (isTvLayout) null else onBack,
                horizontalPadding = horizontalPadding,
            )

            if (!isLoading && episodes.isNotEmpty()) {
                if (isTvLayout) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding, vertical = 8.dp),
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
                        color = BsTheme.colors.MistFaint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp, vertical = 4.dp),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BsGlassSearchField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = stringResource(R.string.search_episodes),
                            modifier = Modifier.weight(1f),
                        )
                        BsPrimaryButton(
                            text = stringResource(R.string.download_season_all_short, episodes.size),
                            onClick = onDownloadSeason,
                        )
                    }
                    Text(
                        text = stringResource(R.string.download_season_all_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = BsTheme.colors.MistFaint,
                        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 6.dp),
                    )
                }
            }

            when {
                isLoading -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        BsShimmerRow()
                        BsShimmerRow()
                    }
                }
                errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BsErrorState(message = errorMessage)
                            BsPrimaryButton(
                                text = stringResource(R.string.loading_error_retry),
                                onClick = onRetry,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
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
                            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(filtered, key = { _, ep -> ep.id }) { _, episode ->
                                SeasonTvEpisodeCard(
                                    episode = episode,
                                    downloadStatus = downloadStatusByEpisodeId[episode.id],
                                    onOpen = { onEpisodeClick(episode) },
                                    onLongClick = { onEpisodeLongClick(episode) },
                                    onDownload = { onDownloadEpisode(episode) },
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(filtered, key = { _, ep -> ep.id }) { _, episode ->
                                SeasonEpisodeCard(
                                    episode = episode,
                                    downloadStatus = downloadStatusByEpisodeId[episode.id],
                                    onOpen = { onEpisodeClick(episode) },
                                    onLongClick = { onEpisodeLongClick(episode) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeasonEpisodeCard(
    episode: Episode,
    downloadStatus: DownloadManager.DownloadStatus?,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = BsMotion.FocusSpring,
        label = "epCardScale",
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
        DownloadManager.DownloadStatus.PAUSED -> stringResource(R.string.download_status_paused)
        DownloadManager.DownloadStatus.FAILED -> stringResource(R.string.download_retry)
        else -> stringResource(R.string.download_episode)
    }
    val downloadEnabled = downloadStatus != DownloadManager.DownloadStatus.COMPLETED &&
        downloadStatus != DownloadManager.DownloadStatus.DOWNLOADING &&
        downloadStatus != DownloadManager.DownloadStatus.PENDING

    BsGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onOpen,
                onLongClick = onLongClick,
            ),
        selected = focused,
        corner = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BsTheme.colors.InkSoft),
            ) {
                AsyncImage(
                    model = episode.poster ?: episode.tvShow?.poster,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (watchProgress != null && watchProgress > 0f) {
                    BsProgressBar(
                        progress = watchProgress,
                        modifier = Modifier
                            .align(Alignment.BottomCenter),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episodeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = BsTheme.colors.AmberBright,
                )
                Text(
                    text = episode.title.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = BsTheme.colors.Mist,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = BsTheme.colors.MistDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (downloadEnabled) {
                        BsGhostButton(
                            text = downloadLabel,
                            onClick = onDownload,
                        )
                    } else {
                        Text(
                            text = downloadLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = BsTheme.colors.MistFaint,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    BsGhostButton(
                        text = "⋯",
                        onClick = onLongClick,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeasonTvEpisodeCard(
    episode: Episode,
    downloadStatus: DownloadManager.DownloadStatus?,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
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

    BsGlassPanel(
        modifier = Modifier
            .width(200.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onOpen,
                onLongClick = onLongClick,
            ),
        selected = focused,
        corner = 10.dp,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BsTheme.colors.InkSoft),
            ) {
                AsyncImage(
                    model = episode.poster ?: episode.tvShow?.poster,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (watchProgress != null && watchProgress > 0f) {
                    BsProgressBar(
                        progress = watchProgress,
                        modifier = Modifier
                            .align(Alignment.BottomCenter),
                    )
                }
            }
            Text(
                text = episodeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = BsTheme.colors.AmberBright,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = episode.title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = BsTheme.colors.Mist,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            BsGhostButton(
                text = downloadLabel,
                onClick = onDownload,
                modifier = Modifier.padding(top = 4.dp),
            )
            BsGhostButton(
                text = "⋯",
                onClick = onLongClick,
            )
        }
    }
}
