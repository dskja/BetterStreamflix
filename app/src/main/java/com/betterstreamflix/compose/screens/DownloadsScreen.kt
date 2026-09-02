package com.betterstreamflix.compose.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsBrandMark
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.theme.BsColors
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
        animationSpec = tween(420),
        label = "downloadsHeaderAlpha",
    )

    var filter by remember { mutableStateOf(DownloadsFilter.ALL) }
    var query by remember { mutableStateOf("") }

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

    BsAtmosphere {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .alpha(headerAlpha),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    BsBrandMark(compact = true)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.downloads_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = BsColors.Mist,
                    )
                    if (downloads.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.downloads_count_summary,
                                readyCount,
                                activeCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = BsColors.MistFaint,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                BsGhostButton(text = "‹", onClick = onBack)
            }

            if (storageUsedBytes > 0 || storageFreeBytes > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .alpha(headerAlpha),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.download_storage_used,
                                DownloadStorageManager.formatSize(storageUsedBytes),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = BsColors.MistFaint,
                        )
                        if (storageFreeBytes > 0) {
                            Text(
                                text = stringResource(
                                    R.string.downloads_storage_free,
                                    DownloadStorageManager.formatSize(storageFreeBytes),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = BsColors.MistFaint,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { storageFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = BsColors.SeaGlass,
                        trackColor = BsColors.InkSoft,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (downloads.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .alpha(headerAlpha),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.downloads_search_hint),
                            color = BsColors.MistFaint,
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BsColors.Amber,
                        unfocusedBorderColor = BsColors.Hairline,
                        focusedContainerColor = BsColors.InkPanel,
                        unfocusedContainerColor = BsColors.InkPanel,
                        focusedTextColor = BsColors.Mist,
                        unfocusedTextColor = BsColors.Mist,
                        cursorColor = BsColors.Amber,
                    ),
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .alpha(headerAlpha),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChipItem(
                        label = stringResource(R.string.downloads_filter_all),
                        selected = filter == DownloadsFilter.ALL,
                        count = downloads.size,
                        onClick = { filter = DownloadsFilter.ALL },
                    )
                    FilterChipItem(
                        label = stringResource(R.string.downloads_filter_active),
                        selected = filter == DownloadsFilter.ACTIVE,
                        count = activeCount,
                        onClick = { filter = DownloadsFilter.ACTIVE },
                    )
                    FilterChipItem(
                        label = stringResource(R.string.downloads_filter_ready),
                        selected = filter == DownloadsFilter.READY,
                        count = readyCount,
                        onClick = { filter = DownloadsFilter.READY },
                    )
                    FilterChipItem(
                        label = stringResource(R.string.downloads_filter_failed),
                        selected = filter == DownloadsFilter.FAILED,
                        count = failedCount,
                        onClick = { filter = DownloadsFilter.FAILED },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .alpha(headerAlpha),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
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

            AnimatedVisibility(
                visible = downloads.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.downloads_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = BsColors.Mist,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BsEmptyState(message = stringResource(R.string.downloads_empty_message))
                    }
                }
            }

            if (downloads.isNotEmpty()) {
                if (filtered.isEmpty()) {
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
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(filtered, key = { it.id }) { task ->
                            DownloadRow(
                                task = task,
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
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = if (count > 0) "$label · $count" else label,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = BsColors.Amber,
            selectedLabelColor = BsColors.Ink,
            containerColor = BsColors.InkPanel,
            labelColor = BsColors.MistDim,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = BsColors.Hairline,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
private fun DownloadRow(
    task: DownloadManager.DownloadTask,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
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
    val percent = (task.progressFraction * 100).toInt().coerceIn(0, 100)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = task.canOpen, onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BsColors.InkSoft)
                    .border(1.dp, BsColors.Hairline, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor),
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
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill(label = statusLabel, color = statusColor)
                    Text(
                        text = task.providerName,
                        style = MaterialTheme.typography.labelSmall,
                        color = BsColors.MistFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
            }

            Column(horizontalAlignment = Alignment.End) {
                ProgressMeta(task)
                if (task.status == DownloadManager.DownloadStatus.DOWNLOADING ||
                    task.status == DownloadManager.DownloadStatus.PAUSED ||
                    (task.status == DownloadManager.DownloadStatus.PENDING && task.fileSize > 0)
                ) {
                    Text(
                        text = stringResource(R.string.download_progress_percent, percent),
                        style = MaterialTheme.typography.labelMedium,
                        color = BsColors.AmberBright,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        if (task.status == DownloadManager.DownloadStatus.DOWNLOADING ||
            task.status == DownloadManager.DownloadStatus.PENDING ||
            task.status == DownloadManager.DownloadStatus.PAUSED
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            if (task.fileSize > 0L) {
                LinearProgressIndicator(
                    progress = { task.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = BsColors.Amber,
                    trackColor = BsColors.InkSoft,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = BsColors.Amber,
                    trackColor = BsColors.InkSoft,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (task.status) {
                DownloadManager.DownloadStatus.COMPLETED -> {
                    BsGhostButton(text = stringResource(R.string.download_open), onClick = onOpen)
                    BsGhostButton(text = stringResource(R.string.download_delete), onClick = onCancel)
                }
                DownloadManager.DownloadStatus.DOWNLOADING, DownloadManager.DownloadStatus.PENDING -> {
                    BsGhostButton(text = stringResource(R.string.download_pause), onClick = onPause)
                    BsGhostButton(text = stringResource(R.string.download_cancel), onClick = onCancel)
                }
                DownloadManager.DownloadStatus.PAUSED -> {
                    BsGhostButton(text = stringResource(R.string.download_resume), onClick = onResume)
                    BsGhostButton(text = stringResource(R.string.download_cancel), onClick = onCancel)
                }
                DownloadManager.DownloadStatus.FAILED -> {
                    BsGhostButton(text = stringResource(R.string.download_retry), onClick = onResume)
                    BsGhostButton(text = stringResource(R.string.download_delete), onClick = onCancel)
                }
                DownloadManager.DownloadStatus.CANCELLED -> {
                    BsGhostButton(text = stringResource(R.string.download_delete), onClick = onCancel)
                }
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

@Composable
private fun StatusPill(label: String, color: Color) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun ProgressMeta(task: DownloadManager.DownloadTask) {
    val label = when {
        task.status == DownloadManager.DownloadStatus.COMPLETED && task.fileSize > 0 ->
            DownloadStorageManager.formatSize(task.fileSize)
        task.fileSize > 0 -> stringResource(
            R.string.download_size_progress,
            DownloadStorageManager.formatSize(task.downloadedBytes),
            DownloadStorageManager.formatSize(task.fileSize),
        )
        task.status == DownloadManager.DownloadStatus.DOWNLOADING ->
            stringResource(R.string.download_progress_unknown)
        else -> ""
    }
    if (label.isNotBlank()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = BsColors.MistDim,
        )
    }
}
