package com.betterstreamflix.compose.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsBrandMark
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.compose.theme.BsDisplayFont
import com.betterstreamflix.download.DownloadManager
import com.betterstreamflix.download.DownloadStorageManager

enum class DownloadsFilter {
    LIBRARY,
    QUEUE,
    FAILED,
}

@Composable
fun DownloadsScreen(
    downloads: List<DownloadManager.DownloadTask>,
    storageUsedBytes: Long = 0,
    storageFreeBytes: Long = 0,
    onBack: () -> Unit = {},
    onOpen: (DownloadManager.DownloadTask) -> Unit = {},
    onPause: (DownloadManager.DownloadTask) -> Unit = {},
    onResume: (DownloadManager.DownloadTask) -> Unit = {},
    onCancel: (DownloadManager.DownloadTask) -> Unit = {},
    onClearCompleted: () -> Unit = {},
    onPauseAll: () -> Unit = {},
    onResumeAll: () -> Unit = {},
    onRetryFailed: () -> Unit = {},
    onClearFailed: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val headerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(560, easing = FastOutSlowInEasing),
        label = "downloadsHeaderAlpha",
    )

    var filter by remember { mutableStateOf(DownloadsFilter.LIBRARY) }
    var query by remember { mutableStateOf("") }

    val ready = remember(downloads) {
        downloads.filter { it.status == DownloadManager.DownloadStatus.COMPLETED }
            .sortedByDescending { it.completedAt ?: it.createdAt }
    }
    val queue = remember(downloads) {
        downloads.filter {
            it.status == DownloadManager.DownloadStatus.PENDING ||
                it.status == DownloadManager.DownloadStatus.DOWNLOADING ||
                it.status == DownloadManager.DownloadStatus.PAUSED
        }.sortedWith(
            compareBy<DownloadManager.DownloadTask> {
                when (it.status) {
                    DownloadManager.DownloadStatus.DOWNLOADING -> 0
                    DownloadManager.DownloadStatus.PENDING -> 1
                    else -> 2
                }
            }.thenByDescending { it.createdAt },
        )
    }
    val failed = remember(downloads) {
        downloads.filter { it.status == DownloadManager.DownloadStatus.FAILED }
            .sortedByDescending { it.createdAt }
    }

    // Prefer the most useful tab when opening with content.
    LaunchedEffect(downloads.size) {
        if (ready.isEmpty() && queue.isNotEmpty()) filter = DownloadsFilter.QUEUE
        else if (ready.isEmpty() && queue.isEmpty() && failed.isNotEmpty()) {
            filter = DownloadsFilter.FAILED
        }
    }

    val matchesQuery: (DownloadManager.DownloadTask) -> Boolean = { task ->
        query.isBlank() ||
            task.title.contains(query, ignoreCase = true) ||
            task.providerName.contains(query, ignoreCase = true)
    }
    val readyView = remember(ready, query) { ready.filter(matchesQuery) }
    val queueView = remember(queue, query) { queue.filter(matchesQuery) }
    val failedView = remember(failed, query) { failed.filter(matchesQuery) }

    val heroArtwork = ready.firstOrNull()?.artworkUrl
        ?: queue.firstOrNull()?.artworkUrl
        ?: failed.firstOrNull()?.artworkUrl

    val storageLabel = when {
        storageUsedBytes <= 0L && storageFreeBytes <= 0L -> null
        storageUsedBytes > 0L && storageFreeBytes > 0L ->
            "${DownloadStorageManager.formatSize(storageUsedBytes)} used · ${DownloadStorageManager.formatSize(storageFreeBytes)} free"
        storageUsedBytes > 0L ->
            stringResource(R.string.download_storage_used, DownloadStorageManager.formatSize(storageUsedBytes))
        else ->
            stringResource(R.string.downloads_storage_free, DownloadStorageManager.formatSize(storageFreeBytes))
    }

    BsAtmosphere {
        Box(modifier = Modifier.fillMaxSize()) {
            // Full-bleed cinematic backdrop from first available artwork.
            if (!heroArtwork.isNullOrBlank()) {
                AsyncImage(
                    model = heroArtwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .alpha(0.28f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x6607090D),
                                    Color(0xCC07090D),
                                    BsColors.Ink,
                                ),
                            ),
                        ),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x33E8A838), Color(0x110B121A), Color.Transparent),
                            ),
                        ),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(headerAlpha),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BsBrandMark(compact = true)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.downloads_title),
                            style = MaterialTheme.typography.displayMedium,
                            color = BsColors.Mist,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = storageLabel
                                ?: if (downloads.isEmpty()) {
                                    stringResource(R.string.downloads_empty_title)
                                } else {
                                    stringResource(
                                        R.string.downloads_count_summary,
                                        ready.size,
                                        queue.size,
                                    )
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = BsColors.MistDim,
                        )
                    }
                    BsGhostButton(text = "‹", onClick = onBack)
                }

                if (downloads.isEmpty()) {
                    DownloadsEmptyHero(modifier = Modifier.fillMaxSize())
                } else {
                    FilterTabs(
                        filter = filter,
                        counts = mapOf(
                            DownloadsFilter.LIBRARY to ready.size,
                            DownloadsFilter.QUEUE to queue.size,
                            DownloadsFilter.FAILED to failed.size,
                        ),
                        onFilter = { filter = it },
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.downloads_search_hint),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    when (filter) {
                        DownloadsFilter.LIBRARY -> LibraryPane(
                            items = readyView,
                            onOpen = onOpen,
                            onDelete = onCancel,
                            onClearCompleted = onClearCompleted,
                        )
                        DownloadsFilter.QUEUE -> QueuePane(
                            items = queueView,
                            onOpen = onOpen,
                            onPause = onPause,
                            onResume = onResume,
                            onCancel = onCancel,
                            onPauseAll = onPauseAll,
                            onResumeAll = onResumeAll,
                        )
                        DownloadsFilter.FAILED -> FailedPane(
                            items = failedView,
                            onRetry = onResume,
                            onDelete = onCancel,
                            onRetryFailed = onRetryFailed,
                            onClearFailed = onClearFailed,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterTabs(
    filter: DownloadsFilter,
    counts: Map<DownloadsFilter, Int>,
    onFilter: (DownloadsFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        DownloadsFilter.entries.forEach { tab ->
            val selected = filter == tab
            val label = when (tab) {
                DownloadsFilter.LIBRARY -> stringResource(R.string.downloads_filter_ready)
                DownloadsFilter.QUEUE -> stringResource(R.string.downloads_filter_active)
                DownloadsFilter.FAILED -> stringResource(R.string.downloads_filter_failed)
            }
            val count = counts[tab] ?: 0
            Column(
                modifier = Modifier
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onFilter(tab) },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    )
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = if (count > 0) "$label  $count" else label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) BsColors.Mist else BsColors.MistFaint,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(if (selected) 32.dp else 0.dp)
                        .height(2.dp)
                        .background(BsColors.Amber, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = SolidColor(BsColors.Amber),
            textStyle = TextStyle(
                color = BsColors.Mist,
                fontSize = 15.sp,
                fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = BsColors.MistFaint,
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (focused) BsColors.Amber else BsColors.Hairline),
        )
    }
}

@Composable
private fun DownloadsEmptyHero(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "emptyPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "emptyGlow",
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 36.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(3.dp)
                    .alpha(glow)
                    .background(BsColors.AmberGlow, RoundedCornerShape(2.dp)),
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.downloads_empty_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontFamily = BsDisplayFont),
                color = BsColors.Mist,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.downloads_empty_message),
                style = MaterialTheme.typography.bodyLarge,
                color = BsColors.MistDim,
            )
        }
    }
}

@Composable
private fun LibraryPane(
    items: List<DownloadManager.DownloadTask>,
    onOpen: (DownloadManager.DownloadTask) -> Unit,
    onDelete: (DownloadManager.DownloadTask) -> Unit,
    onClearCompleted: () -> Unit,
) {
    if (items.isEmpty()) {
        EmptyFilterMessage(stringResource(R.string.downloads_no_matches))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 118.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (items.size >= 2) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "clear") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    BsGhostButton(
                        text = stringResource(R.string.downloads_clear_completed),
                        onClick = onClearCompleted,
                    )
                }
            }
        }
        items(items, key = { it.id }) { task ->
            OfflinePosterCard(
                task = task,
                onOpen = { onOpen(task) },
                onDelete = { onDelete(task) },
            )
        }
    }
}

@Composable
private fun QueuePane(
    items: List<DownloadManager.DownloadTask>,
    onOpen: (DownloadManager.DownloadTask) -> Unit,
    onPause: (DownloadManager.DownloadTask) -> Unit,
    onResume: (DownloadManager.DownloadTask) -> Unit,
    onCancel: (DownloadManager.DownloadTask) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
) {
    if (items.isEmpty()) {
        EmptyFilterMessage(stringResource(R.string.downloads_no_matches))
        return
    }
    val canPause = items.any {
        it.status == DownloadManager.DownloadStatus.DOWNLOADING ||
            it.status == DownloadManager.DownloadStatus.PENDING
    }
    val canResume = items.any { it.status == DownloadManager.DownloadStatus.PAUSED }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            if (canPause) {
                BsGhostButton(text = stringResource(R.string.downloads_pause_all), onClick = onPauseAll)
            }
            if (canResume) {
                BsGhostButton(text = stringResource(R.string.downloads_resume_all), onClick = onResumeAll)
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
            itemsIndexed(items, key = { _, t -> t.id }) { index, task ->
                val primary = when (task.status) {
                    DownloadManager.DownloadStatus.PAUSED ->
                        stringResource(R.string.download_resume) to { onResume(task) }
                    else ->
                        stringResource(R.string.download_pause) to { onPause(task) }
                }
                MediaDownloadRow(
                    task = task,
                    index = index,
                    primary = primary,
                    secondary = stringResource(R.string.download_cancel) to { onCancel(task) },
                    onOpen = { if (task.canOpen) onOpen(task) },
                    showProgress = true,
                )
            }
        }
    }
}

@Composable
private fun FailedPane(
    items: List<DownloadManager.DownloadTask>,
    onRetry: (DownloadManager.DownloadTask) -> Unit,
    onDelete: (DownloadManager.DownloadTask) -> Unit,
    onRetryFailed: () -> Unit,
    onClearFailed: () -> Unit,
) {
    if (items.isEmpty()) {
        EmptyFilterMessage(stringResource(R.string.downloads_no_matches))
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            BsGhostButton(text = stringResource(R.string.downloads_retry_failed), onClick = onRetryFailed)
            BsGhostButton(text = stringResource(R.string.downloads_clear_failed), onClick = onClearFailed)
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
            itemsIndexed(items, key = { _, t -> t.id }) { index, task ->
                MediaDownloadRow(
                    task = task,
                    index = index,
                    primary = stringResource(R.string.download_retry) to { onRetry(task) },
                    secondary = stringResource(R.string.download_delete) to { onDelete(task) },
                    onOpen = {},
                )
            }
        }
    }
}

@Composable
private fun EmptyFilterMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = BsColors.MistDim)
    }
}

@Composable
private fun OfflinePosterCard(
    task: DownloadManager.DownloadTask,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BsColors.InkSoft),
        ) {
            AsyncImage(
                model = task.artworkUrl,
                contentDescription = task.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xE607090D)),
                        ),
                    ),
            )
            Text(
                text = stringResource(R.string.download_open),
                style = MaterialTheme.typography.labelLarge,
                color = BsColors.AmberBright,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
            )
        }
        Text(
            text = task.title,
            style = MaterialTheme.typography.labelMedium,
            color = BsColors.Mist,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            text = stringResource(R.string.download_delete),
            style = MaterialTheme.typography.labelSmall,
            color = BsColors.MistFaint,
            modifier = Modifier
                .padding(top = 2.dp, start = 2.dp)
                .clickable(onClick = onDelete),
        )
    }
}

@Composable
private fun MediaDownloadRow(
    task: DownloadManager.DownloadTask,
    index: Int,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>,
    onOpen: () -> Unit,
    showProgress: Boolean = false,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(task.id) { shown = true }
    val delay = (index * 36).coerceAtMost(240)
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(340, delayMillis = delay, easing = FastOutSlowInEasing),
        label = "rowAlpha",
    )
    val offset by animateFloatAsState(
        targetValue = if (shown) 0f else 10f,
        animationSpec = tween(340, delayMillis = delay, easing = FastOutSlowInEasing),
        label = "rowOffset",
    )

    val statusLabel = when (task.status) {
        DownloadManager.DownloadStatus.PENDING -> stringResource(R.string.download_status_pending)
        DownloadManager.DownloadStatus.DOWNLOADING -> stringResource(R.string.download_status_downloading)
        DownloadManager.DownloadStatus.PAUSED -> stringResource(R.string.download_status_paused)
        DownloadManager.DownloadStatus.COMPLETED -> stringResource(R.string.download_status_completed)
        DownloadManager.DownloadStatus.FAILED -> stringResource(R.string.download_status_failed)
        DownloadManager.DownloadStatus.CANCELLED -> stringResource(R.string.download_status_cancelled)
    }
    val sizeLabel = when {
        task.fileSize > 0L && task.status == DownloadManager.DownloadStatus.COMPLETED ->
            DownloadStorageManager.formatSize(task.fileSize)
        task.fileSize > 0L -> stringResource(
            R.string.download_size_progress,
            DownloadStorageManager.formatSize(task.downloadedBytes),
            DownloadStorageManager.formatSize(task.fileSize),
        )
        task.status == DownloadManager.DownloadStatus.DOWNLOADING ->
            stringResource(R.string.download_progress_unknown)
        else -> null
    }
    val subtitle = buildString {
        append(statusLabel)
        append("  ·  ")
        append(task.providerName)
        if (!sizeLabel.isNullOrBlank()) {
            append("  ·  ")
            append(sizeLabel)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                translationY = offset
            }
            .clickable(enabled = task.canOpen, onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 58.dp, height = 84.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BsColors.InkSoft),
            ) {
                AsyncImage(
                    model = task.artworkUrl,
                    contentDescription = task.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = BsColors.Mist,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.status == DownloadManager.DownloadStatus.FAILED) {
                        BsColors.Danger
                    } else {
                        BsColors.MistFaint
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!task.errorMessage.isNullOrBlank()) {
                    Text(
                        text = task.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = BsColors.Danger,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (showProgress && (
                        task.status == DownloadManager.DownloadStatus.DOWNLOADING ||
                            task.status == DownloadManager.DownloadStatus.PENDING ||
                            task.status == DownloadManager.DownloadStatus.PAUSED
                        )
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (task.fileSize > 0L) {
                        LinearProgressIndicator(
                            progress = { task.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp)),
                            color = BsColors.Amber,
                            trackColor = BsColors.InkSoft,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp)),
                            color = BsColors.Amber,
                            trackColor = BsColors.InkSoft,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = primary.first,
                    style = MaterialTheme.typography.labelLarge,
                    color = BsColors.AmberBright,
                    modifier = Modifier
                        .clickable(onClick = primary.second)
                        .padding(vertical = 4.dp),
                )
                Text(
                    text = secondary.first,
                    style = MaterialTheme.typography.labelMedium,
                    color = BsColors.MistFaint,
                    modifier = Modifier
                        .clickable(onClick = secondary.second)
                        .padding(vertical = 4.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(BsColors.Hairline),
        )
    }
}
