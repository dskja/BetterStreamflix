package com.betterstreamflix.compose.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.betterstreamflix.compose.theme.BsMotion
import com.betterstreamflix.compose.components.BsEmptyState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsGlassFilterChip
import com.betterstreamflix.compose.components.BsGlassPanel
import com.betterstreamflix.compose.components.BsGlassSearchField
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BsTheme
import com.betterstreamflix.compose.theme.BsDisplayFont
import com.betterstreamflix.download.DownloadArtworkStore
import com.betterstreamflix.download.DownloadFeature
import com.betterstreamflix.download.DownloadLiveStats
import com.betterstreamflix.download.DownloadManager
import com.betterstreamflix.download.DownloadProgressTracker
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
    liveSpeeds: Map<String, Long> = emptyMap(),
    onOpen: (DownloadManager.DownloadTask) -> Unit = {},
    onPause: (DownloadManager.DownloadTask) -> Unit = {},
    onResume: (DownloadManager.DownloadTask) -> Unit = {},
    onCancel: (DownloadManager.DownloadTask) -> Unit = {},
    onClearCompleted: () -> Unit = {},
    onPauseAll: () -> Unit = {},
    onResumeAll: () -> Unit = {},
    onRetryFailed: () -> Unit = {},
    onClearFailed: () -> Unit = {},
    isTvLayout: Boolean = false,
) {
    val horizontalPadding = if (isTvLayout) 32.dp else 20.dp
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val headerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(560, easing = FastOutSlowInEasing),
        label = "downloadsHeaderAlpha",
    )

    var filter by remember { mutableStateOf(DownloadsFilter.LIBRARY) }
    var query by remember { mutableStateOf("") }
    var initialTabChosen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

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

    // Pick a useful tab once on first non-empty load; afterwards only leave an emptied tab.
    LaunchedEffect(ready.isEmpty(), queue.isEmpty(), failed.isEmpty(), downloads.isNotEmpty()) {
        if (downloads.isEmpty()) return@LaunchedEffect
        if (!initialTabChosen) {
            filter = when {
                ready.isNotEmpty() -> DownloadsFilter.LIBRARY
                queue.isNotEmpty() -> DownloadsFilter.QUEUE
                failed.isNotEmpty() -> DownloadsFilter.FAILED
                else -> filter
            }
            initialTabChosen = true
            return@LaunchedEffect
        }
        val currentEmpty = when (filter) {
            DownloadsFilter.LIBRARY -> ready.isEmpty()
            DownloadsFilter.QUEUE -> queue.isEmpty()
            DownloadsFilter.FAILED -> failed.isEmpty()
        }
        if (currentEmpty) {
            filter = when {
                ready.isNotEmpty() -> DownloadsFilter.LIBRARY
                queue.isNotEmpty() -> DownloadsFilter.QUEUE
                failed.isNotEmpty() -> DownloadsFilter.FAILED
                else -> filter
            }
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

    // Persist any remaining remote artwork URLs into durable local storage.
    LaunchedEffect(downloads.map { it.id to it.artworkUrl }) {
        DownloadFeature.ensureArtworkCached(context, downloads)
    }

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
            val heroModel = DownloadArtworkStore.coilModel(heroArtwork)
            if (heroModel != null) {
                AsyncImage(
                    model = heroModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .alpha(0.22f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    BsTheme.colors.Ink.copy(alpha = 0.33f),
                                    BsTheme.colors.Ink.copy(alpha = 0.73f),
                                    BsTheme.colors.Ink,
                                ),
                            ),
                        ),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    BsTheme.colors.Amber.copy(alpha = 0.20f),
                                    BsTheme.colors.InkElevated.copy(alpha = 0.07f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(headerAlpha),
            ) {
                BsTopBar(
                    title = stringResource(R.string.downloads_title),
                    showBrand = true,
                    subtitle = storageLabel
                        ?: if (downloads.isEmpty()) {
                            stringResource(R.string.downloads_empty_title)
                        } else {
                            stringResource(
                                R.string.downloads_count_summary,
                                ready.size,
                                queue.size,
                            )
                        },
                    horizontalPadding = horizontalPadding,
                    actions = {
                        BsGhostButton(
                            text = stringResource(R.string.main_menu_settings),
                            onClick = { showSettings = true },
                        )
                    },
                )

                if (showSettings) {
                    DownloadSettingsSheet(
                        onDismiss = { showSettings = false },
                        onClearCompleted = {
                            onClearCompleted()
                            showSettings = false
                        },
                        onClearFailed = {
                            onClearFailed()
                            showSettings = false
                        },
                    )
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
                        horizontalPadding = horizontalPadding,
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    BsGlassSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.downloads_search_hint),
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    when (filter) {
                        DownloadsFilter.LIBRARY -> LibraryPane(
                            items = readyView,
                            horizontalPadding = horizontalPadding,
                            isTvLayout = isTvLayout,
                            onOpen = onOpen,
                            onDelete = onCancel,
                            onClearCompleted = onClearCompleted,
                        )
                        DownloadsFilter.QUEUE -> QueuePane(
                            items = queueView,
                            liveSpeeds = liveSpeeds,
                            horizontalPadding = horizontalPadding,
                            onOpen = onOpen,
                            onPause = onPause,
                            onResume = onResume,
                            onCancel = onCancel,
                            onPauseAll = onPauseAll,
                            onResumeAll = onResumeAll,
                        )
                        DownloadsFilter.FAILED -> FailedPane(
                            items = failedView,
                            horizontalPadding = horizontalPadding,
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
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DownloadsFilter.entries.forEach { tab ->
            val selected = filter == tab
            val label = when (tab) {
                DownloadsFilter.LIBRARY -> stringResource(R.string.downloads_filter_ready)
                DownloadsFilter.QUEUE -> stringResource(R.string.downloads_filter_active)
                DownloadsFilter.FAILED -> stringResource(R.string.downloads_filter_failed)
            }
            val count = counts[tab] ?: 0
            BsGlassFilterChip(
                label = if (count > 0) "$label  $count" else label,
                selected = selected,
                onClick = { onFilter(tab) },
            )
        }
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
                    .background(BsTheme.colors.Amber, RoundedCornerShape(2.dp)),
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.downloads_empty_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontFamily = BsDisplayFont),
                color = BsTheme.colors.Mist,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.downloads_empty_message),
                style = MaterialTheme.typography.bodyLarge,
                color = BsTheme.colors.MistDim,
            )
        }
    }
}

@Composable
private fun LibraryPane(
    items: List<DownloadManager.DownloadTask>,
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
    isTvLayout: Boolean = false,
    onOpen: (DownloadManager.DownloadTask) -> Unit,
    onDelete: (DownloadManager.DownloadTask) -> Unit,
    onClearCompleted: () -> Unit,
) {
    if (items.isEmpty()) {
        EmptyFilterMessage(stringResource(R.string.downloads_library_empty))
        return
    }
    val gridMin = if (isTvLayout) 140.dp else 118.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = gridMin),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = horizontalPadding, top = 8.dp, end = horizontalPadding, bottom = 48.dp),
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
    liveSpeeds: Map<String, Long> = emptyMap(),
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
    onOpen: (DownloadManager.DownloadTask) -> Unit,
    onPause: (DownloadManager.DownloadTask) -> Unit,
    onResume: (DownloadManager.DownloadTask) -> Unit,
    onCancel: (DownloadManager.DownloadTask) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
) {
    if (items.isEmpty()) {
        EmptyFilterMessage(stringResource(R.string.downloads_queue_empty))
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
                .padding(horizontal = horizontalPadding),
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
                    liveSpeedBytesPerSec = liveSpeeds[task.id] ?: 0L,
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
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
    onRetry: (DownloadManager.DownloadTask) -> Unit,
    onDelete: (DownloadManager.DownloadTask) -> Unit,
    onRetryFailed: () -> Unit,
    onClearFailed: () -> Unit,
) {
    if (items.isEmpty()) {
        EmptyFilterMessage(stringResource(R.string.downloads_failed_empty))
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding),
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
    BsEmptyState(
        message = message,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun OfflinePosterCard(
    task: DownloadManager.DownloadTask,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = BsMotion.FocusSpring,
        label = "offlinePosterScale",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onOpen),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BsTheme.colors.InkSoft)
                .border(
                    1.dp,
                    if (focused) BsTheme.colors.Amber.copy(alpha = 0.55f) else BsTheme.colors.Hairline,
                    RoundedCornerShape(10.dp),
                ),
        ) {
            AsyncImage(
                model = DownloadArtworkStore.coilModel(task.artworkUrl),
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
                            listOf(Color.Transparent, BsTheme.colors.Ink.copy(alpha = 0.90f)),
                        ),
                    ),
            )
            Text(
                text = stringResource(R.string.download_open),
                style = MaterialTheme.typography.labelLarge,
                color = BsTheme.colors.AmberBright,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
            )
        }
        Text(
            text = task.title,
            style = MaterialTheme.typography.labelMedium,
            color = BsTheme.colors.Mist,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            text = stringResource(R.string.download_delete),
            style = MaterialTheme.typography.labelSmall,
            color = BsTheme.colors.MistFaint,
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
    liveSpeedBytesPerSec: Long = 0L,
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
    val liveStatsLabel = when {
        task.status == DownloadManager.DownloadStatus.DOWNLOADING && liveSpeedBytesPerSec > 0L -> {
            val percent = (task.progressFraction * 100).toInt().coerceIn(0, 100)
            val eta = DownloadLiveStats.etaSeconds(task.id, task.downloadedBytes, task.fileSize)
            val etaLabel = if (eta > 0L) DownloadProgressTracker.formatEta(eta) else "—"
            stringResource(
                R.string.download_live_stats,
                DownloadProgressTracker.formatSpeed(liveSpeedBytesPerSec),
                percent,
                etaLabel,
            )
        }
        task.status == DownloadManager.DownloadStatus.DOWNLOADING && task.fileSize > 0L -> {
            val percent = (task.progressFraction * 100).toInt().coerceIn(0, 100)
            "$percent%"
        }
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                translationY = offset
            }
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        BsGlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = task.canOpen, onClick = onOpen),
            corner = 10.dp,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 58.dp, height = 84.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BsTheme.colors.InkSoft),
                ) {
                    AsyncImage(
                        model = DownloadArtworkStore.coilModel(task.artworkUrl),
                        contentDescription = task.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = BsTheme.colors.Mist,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (task.status == DownloadManager.DownloadStatus.FAILED) {
                            BsTheme.colors.Danger
                        } else {
                            BsTheme.colors.MistFaint
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!liveStatsLabel.isNullOrBlank()) {
                        Text(
                            text = liveStatsLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = BsTheme.colors.AmberBright,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (!task.errorMessage.isNullOrBlank()) {
                        Text(
                            text = task.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = BsTheme.colors.Danger,
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
                                color = BsTheme.colors.Amber,
                                trackColor = BsTheme.colors.InkSoft,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp)),
                                color = BsTheme.colors.Amber,
                                trackColor = BsTheme.colors.InkSoft,
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = primary.first,
                        style = MaterialTheme.typography.labelLarge,
                        color = BsTheme.colors.AmberBright,
                        modifier = Modifier
                            .clickable(onClick = primary.second)
                            .padding(vertical = 4.dp),
                    )
                    Text(
                        text = secondary.first,
                        style = MaterialTheme.typography.labelMedium,
                        color = BsTheme.colors.MistFaint,
                        modifier = Modifier
                            .clickable(onClick = secondary.second)
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
