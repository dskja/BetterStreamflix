package com.betterstreamflix.compose.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsBrandMark
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.compose.theme.BsDisplayFont
import com.betterstreamflix.download.DownloadManager
import com.betterstreamflix.download.DownloadStorageManager

enum class DownloadsFilter {
    ALL,
    ACTIVE,
    READY,
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
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "downloadsHeaderAlpha",
    )
    val headerOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 18f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "downloadsHeaderOffset",
    )

    var filter by remember { mutableStateOf(DownloadsFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var manageOpen by remember { mutableStateOf(false) }

    val readyCount = downloads.count { it.status == DownloadManager.DownloadStatus.COMPLETED }
    val activeCount = downloads.count {
        it.status == DownloadManager.DownloadStatus.PENDING ||
            it.status == DownloadManager.DownloadStatus.DOWNLOADING ||
            it.status == DownloadManager.DownloadStatus.PAUSED
    }
    val failedCount = downloads.count { it.status == DownloadManager.DownloadStatus.FAILED }
    val downloadingOrPending = downloads.count {
        it.status == DownloadManager.DownloadStatus.DOWNLOADING ||
            it.status == DownloadManager.DownloadStatus.PENDING
    }
    val pausedCount = downloads.count { it.status == DownloadManager.DownloadStatus.PAUSED }

    val filtered = remember(downloads, filter, query) {
        downloads
            .asSequence()
            .filter { task ->
                when (filter) {
                    DownloadsFilter.ALL -> true
                    DownloadsFilter.ACTIVE ->
                        task.status == DownloadManager.DownloadStatus.PENDING ||
                            task.status == DownloadManager.DownloadStatus.DOWNLOADING ||
                            task.status == DownloadManager.DownloadStatus.PAUSED
                    DownloadsFilter.READY -> task.status == DownloadManager.DownloadStatus.COMPLETED
                    DownloadsFilter.FAILED -> task.status == DownloadManager.DownloadStatus.FAILED
                }
            }
            .filter { task ->
                query.isBlank() ||
                    task.title.contains(query, ignoreCase = true) ||
                    task.providerName.contains(query, ignoreCase = true)
            }
            .sortedWith(
                compareBy<DownloadManager.DownloadTask> { statusRank(it.status) }
                    .thenByDescending { it.createdAt },
            )
            .toList()
    }

    val storageTotal = (storageUsedBytes + storageFreeBytes).coerceAtLeast(1L)
    val storageFraction = (storageUsedBytes.toFloat() / storageTotal.toFloat()).coerceIn(0f, 1f)
    val hasManageActions = downloadingOrPending > 0 || pausedCount > 0 ||
        readyCount > 0 || failedCount > 0

    BsAtmosphere {
        Column(modifier = Modifier.fillMaxSize()) {
            // Soft amber wash behind the header — atmosphere, not chrome.
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x33E8A838),
                                    Color(0x140B121A),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 18.dp)
                        .alpha(headerAlpha)
                        .padding(top = headerOffset.dp),
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BsBrandMark(compact = true)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.downloads_title),
                            style = MaterialTheme.typography.displayMedium,
                            color = BsColors.Mist,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                downloads.isEmpty() -> stringResource(R.string.downloads_empty_title)
                                else -> stringResource(
                                    R.string.downloads_count_summary,
                                    readyCount,
                                    activeCount,
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = BsColors.MistDim,
                        )
                    }
                    BsGhostButton(text = "‹", onClick = onBack)
                }

                if (storageUsedBytes > 0 || storageFreeBytes > 0) {
                    Spacer(modifier = Modifier.height(18.dp))
                    StorageStrip(
                        usedLabel = stringResource(
                            R.string.download_storage_used,
                            DownloadStorageManager.formatSize(storageUsedBytes),
                        ),
                        freeLabel = if (storageFreeBytes > 0) {
                            stringResource(
                                R.string.downloads_storage_free,
                                DownloadStorageManager.formatSize(storageFreeBytes),
                            )
                        } else {
                            null
                        },
                        fraction = storageFraction,
                    )
                }
            }
            } // end header Box/Column

            if (downloads.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(headerAlpha),
                ) {
                    FilterTabs(
                        filter = filter,
                        counts = mapOf(
                            DownloadsFilter.ALL to downloads.size,
                            DownloadsFilter.ACTIVE to activeCount,
                            DownloadsFilter.READY to readyCount,
                            DownloadsFilter.FAILED to failedCount,
                        ),
                        onFilter = { filter = it },
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.downloads_search_hint),
                        modifier = Modifier.padding(horizontal = 22.dp),
                    )

                    if (hasManageActions) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BsGhostButton(
                                text = if (manageOpen) {
                                    stringResource(R.string.downloads_manage_hide)
                                } else {
                                    stringResource(R.string.downloads_manage)
                                },
                                onClick = { manageOpen = !manageOpen },
                            )
                        }
                        AnimatedVisibility(visible = manageOpen) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 14.dp),
                            ) {
                                if (downloadingOrPending > 0) {
                                    BsGhostButton(
                                        text = stringResource(R.string.downloads_pause_all),
                                        onClick = onPauseAll,
                                    )
                                }
                                if (pausedCount > 0 || failedCount > 0) {
                                    BsGhostButton(
                                        text = stringResource(R.string.downloads_resume_all),
                                        onClick = onResumeAll,
                                    )
                                }
                                if (readyCount > 0) {
                                    BsGhostButton(
                                        text = stringResource(R.string.downloads_clear_completed),
                                        onClick = onClearCompleted,
                                    )
                                }
                                if (failedCount > 0) {
                                    BsGhostButton(
                                        text = stringResource(R.string.downloads_retry_failed),
                                        onClick = onRetryFailed,
                                    )
                                    BsGhostButton(
                                        text = stringResource(R.string.downloads_clear_failed),
                                        onClick = onClearFailed,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            when {
                downloads.isEmpty() -> DownloadsEmptyState(modifier = Modifier.fillMaxSize())
                filtered.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.downloads_no_matches),
                            style = MaterialTheme.typography.bodyLarge,
                            color = BsColors.MistDim,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 48.dp, top = 4.dp),
                    ) {
                        itemsIndexed(filtered, key = { _, task -> task.id }) { index, task ->
                            DownloadShelfRow(
                                task = task,
                                index = index,
                                onOpen = { onOpen(task) },
                                onPause = { onPause(task) },
                                onResume = { onResume(task) },
                                onCancel = { onCancel(task) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun statusRank(status: DownloadManager.DownloadStatus): Int = when (status) {
    DownloadManager.DownloadStatus.DOWNLOADING -> 0
    DownloadManager.DownloadStatus.PENDING -> 1
    DownloadManager.DownloadStatus.PAUSED -> 2
    DownloadManager.DownloadStatus.FAILED -> 3
    DownloadManager.DownloadStatus.COMPLETED -> 4
    DownloadManager.DownloadStatus.CANCELLED -> 5
}

@Composable
private fun StorageStrip(
    usedLabel: String,
    freeLabel: String?,
    fraction: Float,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = usedLabel, style = MaterialTheme.typography.labelMedium, color = BsColors.MistFaint)
            if (!freeLabel.isNullOrBlank()) {
                Text(text = freeLabel, style = MaterialTheme.typography.labelMedium, color = BsColors.MistFaint)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BsColors.InkSoft),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .background(BsColors.AmberGlow, RoundedCornerShape(2.dp)),
            )
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
            .padding(horizontal = 22.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        DownloadsFilter.entries.forEach { tab ->
            val selected = filter == tab
            val label = when (tab) {
                DownloadsFilter.ALL -> stringResource(R.string.downloads_filter_all)
                DownloadsFilter.ACTIVE -> stringResource(R.string.downloads_filter_active)
                DownloadsFilter.READY -> stringResource(R.string.downloads_filter_ready)
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
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = if (count > 0) "$label  $count" else label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) BsColors.Mist else BsColors.MistFaint,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(if (selected) 28.dp else 0.dp)
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
private fun DownloadsEmptyState(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "emptyPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "emptyGlow",
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 36.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(3.dp)
                    .alpha(glow)
                    .background(BsColors.AmberGlow, RoundedCornerShape(2.dp)),
            )
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.downloads_empty_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontFamily = BsDisplayFont),
                color = BsColors.Mist,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.downloads_empty_message),
                style = MaterialTheme.typography.bodyLarge,
                color = BsColors.MistDim,
            )
        }
    }
}

@Composable
private fun DownloadShelfRow(
    task: DownloadManager.DownloadTask,
    index: Int,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(task.id) { shown = true }
    val enterDelay = (index * 40).coerceAtMost(280)
    val rowAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(360, delayMillis = enterDelay, easing = FastOutSlowInEasing),
        label = "downloadRowAlpha",
    )
    val rowOffset by animateFloatAsState(
        targetValue = if (shown) 0f else 12f,
        animationSpec = tween(360, delayMillis = enterDelay, easing = FastOutSlowInEasing),
        label = "downloadRowOffset",
    )

    val statusLabel = when (task.status) {
        DownloadManager.DownloadStatus.PENDING -> stringResource(R.string.download_status_pending)
        DownloadManager.DownloadStatus.DOWNLOADING -> stringResource(R.string.download_status_downloading)
        DownloadManager.DownloadStatus.PAUSED -> stringResource(R.string.download_status_paused)
        DownloadManager.DownloadStatus.COMPLETED -> stringResource(R.string.download_status_completed)
        DownloadManager.DownloadStatus.FAILED -> stringResource(R.string.download_status_failed)
        DownloadManager.DownloadStatus.CANCELLED -> stringResource(R.string.download_status_cancelled)
    }
    val statusColor = when (task.status) {
        DownloadManager.DownloadStatus.COMPLETED -> BsColors.Success
        DownloadManager.DownloadStatus.FAILED -> BsColors.Danger
        DownloadManager.DownloadStatus.DOWNLOADING -> BsColors.Amber
        DownloadManager.DownloadStatus.PAUSED -> BsColors.MistDim
        else -> BsColors.MistFaint
    }
    val isProgressing = task.status == DownloadManager.DownloadStatus.DOWNLOADING ||
        task.status == DownloadManager.DownloadStatus.PENDING ||
        task.status == DownloadManager.DownloadStatus.PAUSED
    val primaryAction: Pair<String, () -> Unit>? = when (task.status) {
        DownloadManager.DownloadStatus.COMPLETED ->
            stringResource(R.string.download_open) to onOpen
        DownloadManager.DownloadStatus.DOWNLOADING, DownloadManager.DownloadStatus.PENDING ->
            stringResource(R.string.download_pause) to onPause
        DownloadManager.DownloadStatus.PAUSED, DownloadManager.DownloadStatus.FAILED ->
            stringResource(
                if (task.status == DownloadManager.DownloadStatus.FAILED) {
                    R.string.download_retry
                } else {
                    R.string.download_resume
                },
            ) to onResume
        else -> null
    }
    val secondaryAction: Pair<String, () -> Unit> = when (task.status) {
        DownloadManager.DownloadStatus.COMPLETED, DownloadManager.DownloadStatus.FAILED,
        DownloadManager.DownloadStatus.CANCELLED,
        -> stringResource(R.string.download_delete) to onCancel
        else -> stringResource(R.string.download_cancel) to onCancel
    }
    val meta = buildString {
        append(statusLabel)
        append("  ·  ")
        append(task.providerName)
        val sizeLabel = when {
            task.status == DownloadManager.DownloadStatus.COMPLETED && task.fileSize > 0 ->
                DownloadStorageManager.formatSize(task.fileSize)
            task.fileSize > 0 -> stringResource(
                R.string.download_size_progress,
                DownloadStorageManager.formatSize(task.downloadedBytes),
                DownloadStorageManager.formatSize(task.fileSize),
            )
            task.status == DownloadManager.DownloadStatus.DOWNLOADING ->
                stringResource(R.string.download_progress_unknown)
            else -> null
        }
        if (!sizeLabel.isNullOrBlank()) {
            append("  ·  ")
            append(sizeLabel)
        }
    }

    // Keep shelf rows measurable so LazyColumn does not eagerly compose the whole list.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = rowAlpha
                translationY = rowOffset
            }
            .clickable(enabled = task.canOpen, onClick = onOpen)
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(3.dp)
                        .height(if (isProgressing) 42.dp else 34.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (task.status == DownloadManager.DownloadStatus.DOWNLOADING) {
                                BsColors.AmberGlow
                            } else {
                                Brush.verticalGradient(listOf(statusColor.copy(alpha = 0.85f), statusColor.copy(alpha = 0.25f)))
                            },
                        ),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = BsColors.Mist,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = BsColors.MistFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!task.errorMessage.isNullOrBlank()) {
                        Text(
                            text = task.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = BsColors.Danger,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (isProgressing) {
                        Spacer(modifier = Modifier.height(12.dp))
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
                    if (primaryAction != null) {
                        Text(
                            text = primaryAction.first,
                            style = MaterialTheme.typography.labelLarge,
                            color = BsColors.AmberBright,
                            modifier = Modifier
                                .clickable(onClick = primaryAction.second)
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                        )
                    }
                    Text(
                        text = secondaryAction.first,
                        style = MaterialTheme.typography.labelMedium,
                        color = BsColors.MistFaint,
                        modifier = Modifier
                            .clickable(onClick = secondaryAction.second)
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BsColors.Hairline),
            )
    }
}
