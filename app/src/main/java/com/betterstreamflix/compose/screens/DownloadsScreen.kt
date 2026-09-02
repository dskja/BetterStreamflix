package com.betterstreamflix.compose.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsBrandMark
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsPrimaryButton
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.download.DownloadManager
import com.betterstreamflix.download.DownloadStorageManager

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
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val headerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(420),
        label = "downloadsHeaderAlpha",
    )

    val active = downloads.filter {
        it.status == DownloadManager.DownloadStatus.PENDING ||
            it.status == DownloadManager.DownloadStatus.DOWNLOADING ||
            it.status == DownloadManager.DownloadStatus.PAUSED
    }
    val ready = downloads.filter { it.status == DownloadManager.DownloadStatus.COMPLETED }
    val failed = downloads.filter { it.status == DownloadManager.DownloadStatus.FAILED }

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
                }
                BsGhostButton(text = "‹", onClick = onBack)
            }

            if (storageUsedBytes > 0 || storageFreeBytes > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .alpha(headerAlpha),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (storageUsedBytes > 0) {
                        Text(
                            text = stringResource(
                                R.string.download_storage_used,
                                DownloadStorageManager.formatSize(storageUsedBytes),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = BsColors.MistFaint,
                        )
                    }
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
            }

            if (downloads.isEmpty()) {
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
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (active.isNotEmpty()) {
                        item {
                            SectionLabel(stringResource(R.string.downloads_section_active))
                        }
                        items(active, key = { it.id }) { task ->
                            DownloadRow(
                                task = task,
                                onOpen = { onOpen(task) },
                                onPause = { onPause(task) },
                                onResume = { onResume(task) },
                                onCancel = { onCancel(task) },
                            )
                        }
                    }
                    if (ready.isNotEmpty()) {
                        item {
                            SectionLabel(stringResource(R.string.downloads_section_ready))
                        }
                        items(ready, key = { it.id }) { task ->
                            DownloadRow(
                                task = task,
                                onOpen = { onOpen(task) },
                                onPause = { onPause(task) },
                                onResume = { onResume(task) },
                                onCancel = { onCancel(task) },
                            )
                        }
                        item {
                            BsGhostButton(
                                text = stringResource(R.string.downloads_clear_completed),
                                onClick = onClearCompleted,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                    if (failed.isNotEmpty()) {
                        item {
                            SectionLabel(stringResource(R.string.downloads_section_failed))
                        }
                        items(failed, key = { it.id }) { task ->
                            DownloadRow(
                                task = task,
                                onOpen = { onOpen(task) },
                                onPause = { onPause(task) },
                                onResume = { onResume(task) },
                                onCancel = { onCancel(task) },
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = BsColors.AmberMuted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = task.canOpen, onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = BsColors.Mist,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(task.providerName)
                        append(" · ")
                        append(statusLabel)
                        if (!task.errorMessage.isNullOrBlank()) {
                            append(" — ")
                            append(task.errorMessage)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            ProgressMeta(task)
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
        task.fileSize > 0 && task.downloadedBytes > 0 ->
            stringResource(R.string.download_progress_percent, (task.progressFraction * 100).toInt())
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
