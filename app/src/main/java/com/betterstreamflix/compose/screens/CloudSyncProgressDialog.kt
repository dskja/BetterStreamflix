package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.theme.BsTheme
import com.betterstreamflix.sync.CloudSyncProgress

@Composable
fun CloudSyncProgressDialog(
    progress: CloudSyncProgress?,
) {
    val message = progressMessage(progress)
    val fraction = if (progress?.stage == CloudSyncProgress.Stage.UPLOADING && progress.total > 0) {
        progress.current.toFloat() / progress.total.toFloat()
    } else {
        null
    }
    GlassProgressDialog(
        title = stringResource(R.string.cloud_sync_progress_title),
        message = message,
        progressFraction = fraction,
    )
}

@Composable
fun GlassProgressDialog(
    title: String,
    message: String,
    progressFraction: Float? = null,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = BsTheme.colors.Mist,
            )
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (progressFraction == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = BsTheme.colors.Amber,
                        trackColor = BsTheme.colors.InkSoft,
                        strokeWidth = 3.dp,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BsTheme.colors.MistDim,
                    )
                    if (progressFraction != null) {
                        LinearProgressIndicator(
                            progress = { progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = BsTheme.colors.Amber,
                            trackColor = BsTheme.colors.InkSoft,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = BsTheme.colors.GlassStrong,
    )
}

@Composable
private fun progressMessage(progress: CloudSyncProgress?): String {
    val context = LocalContext.current
    if (progress == null) {
        return stringResource(R.string.cloud_sync_progress_connecting)
    }
    return when (progress.stage) {
        CloudSyncProgress.Stage.AUTHENTICATING ->
            stringResource(R.string.cloud_sync_progress_authenticating)
        CloudSyncProgress.Stage.CHECKING_CLOUD ->
            stringResource(R.string.cloud_sync_progress_checking_cloud)
        CloudSyncProgress.Stage.PREPARING_LOCAL ->
            stringResource(R.string.cloud_sync_progress_preparing_local)
        CloudSyncProgress.Stage.MERGING ->
            stringResource(R.string.cloud_sync_progress_merging)
        CloudSyncProgress.Stage.UPLOADING ->
            stringResource(
                R.string.cloud_sync_progress_uploading,
                progress.current,
                progress.total,
            )
        CloudSyncProgress.Stage.APPLYING_CLOUD ->
            context.resources.getQuantityString(
                R.plurals.cloud_sync_progress_applying_cloud,
                progress.total,
                progress.total,
            )
        CloudSyncProgress.Stage.FINALIZING ->
            stringResource(R.string.cloud_sync_progress_finalizing)
    }
}
