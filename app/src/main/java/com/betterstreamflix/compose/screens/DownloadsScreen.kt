package com.betterstreamflix.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsDownloadProgress
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.download.DownloadManager

@Composable
fun DownloadsScreen(
    downloads: List<DownloadManager.DownloadTask>,
    storageUsedMb: Long = 0,
    onBack: () -> Unit = {},
    onOpen: (DownloadManager.DownloadTask) -> Unit = {},
    onPause: (DownloadManager.DownloadTask) -> Unit = {},
    onResume: (DownloadManager.DownloadTask) -> Unit = {},
    onCancel: (DownloadManager.DownloadTask) -> Unit = {},
) {
    BsAtmosphere {
        Column(modifier = Modifier.fillMaxSize()) {
            BsTopBar(
                title = stringResource(R.string.downloads_title),
                showBrand = true,
            )
            if (storageUsedMb > 0) {
                Text(
                    text = "$storageUsedMb MB offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = BsColors.MistFaint,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (downloads.isEmpty()) {
                BsEmptyState(
                    message = stringResource(R.string.downloads_empty_message),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(downloads, key = { it.id }) { task ->
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

@Composable
private fun DownloadRow(
    task: DownloadManager.DownloadTask,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .border(1.dp, BsColors.Hairline, RoundedCornerShape(14.dp))
            .background(BsColors.InkPanel, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(text = task.title, style = MaterialTheme.typography.titleMedium, color = BsColors.Mist)
        Text(
            text = "${task.providerName} · ${task.status.name.lowercase()}",
            style = MaterialTheme.typography.bodyMedium,
            color = BsColors.MistDim,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (task.status == DownloadManager.DownloadStatus.DOWNLOADING ||
            task.status == DownloadManager.DownloadStatus.PENDING
        ) {
            val progress = if (task.fileSize > 0) {
                task.downloadedBytes.toFloat() / task.fileSize.toFloat()
            } else {
                0f
            }
            BsDownloadProgress(progress = progress, modifier = Modifier.padding(top = 12.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (task.status == DownloadManager.DownloadStatus.COMPLETED) {
                BsGhostButton(text = stringResource(R.string.download_open), onClick = onOpen)
            }
            if (task.status == DownloadManager.DownloadStatus.DOWNLOADING ||
                task.status == DownloadManager.DownloadStatus.PENDING
            ) {
                BsGhostButton(text = stringResource(R.string.download_pause), onClick = onPause)
            }
            if (task.status == DownloadManager.DownloadStatus.PAUSED ||
                task.status == DownloadManager.DownloadStatus.FAILED
            ) {
                BsGhostButton(text = stringResource(R.string.download_resume), onClick = onResume)
            }
            BsGhostButton(text = stringResource(R.string.download_cancel), onClick = onCancel)
        }
    }
}
