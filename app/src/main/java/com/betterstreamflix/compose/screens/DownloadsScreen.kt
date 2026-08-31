package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsDownloadProgress
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
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
    BetterStreamflixTheme {
        Scaffold(
            topBar = {
                BsTopBar(title = stringResource(R.string.downloads_title))
            },
        ) { padding ->
            if (downloads.isEmpty()) {
                BsEmptyState(
                    message = stringResource(R.string.downloads_empty_message),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = task.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${task.providerName} · ${task.status.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (task.status == DownloadManager.DownloadStatus.DOWNLOADING ||
                task.status == DownloadManager.DownloadStatus.PENDING
            ) {
                val progress = if (task.fileSize > 0) {
                    task.downloadedBytes.toFloat() / task.fileSize.toFloat()
                } else {
                    0f
                }
                BsDownloadProgress(
                    progress = progress,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (task.status == DownloadManager.DownloadStatus.COMPLETED) {
                    TextButton(onClick = onOpen) { Text(stringResource(R.string.download_open)) }
                }
                if (task.status == DownloadManager.DownloadStatus.DOWNLOADING ||
                    task.status == DownloadManager.DownloadStatus.PENDING
                ) {
                    TextButton(onClick = onPause) { Text(stringResource(R.string.download_pause)) }
                }
                if (task.status == DownloadManager.DownloadStatus.PAUSED ||
                    task.status == DownloadManager.DownloadStatus.FAILED
                ) {
                    TextButton(onClick = onResume) { Text(stringResource(R.string.download_resume)) }
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.download_cancel)) }
            }
        }
    }
}
