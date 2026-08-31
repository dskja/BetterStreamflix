package com.betterstreamflix.compose.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.betterstreamflix.compose.components.BsDownloadProgress
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
import com.betterstreamflix.download.DownloadManager

@Composable
fun DownloadsScreen(
    downloads: List<DownloadManager.DownloadTask>,
    onBack: () -> Unit = {},
    onOpen: (DownloadManager.DownloadTask) -> Unit = {},
) {
    BetterStreamflixTheme {
        Scaffold(
            topBar = { BsTopBar(title = "Downloads") },
        ) { padding ->
            if (downloads.isEmpty()) {
                BsEmptyState(
                    message = "No downloads yet",
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
                        DownloadRow(task = task, onClick = { onOpen(task) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    task: DownloadManager.DownloadTask,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
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
        }
    }
}
