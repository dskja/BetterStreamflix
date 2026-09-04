package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.theme.BsTheme

@Composable
fun CloudSyncConflictDialog(
    onKeepLocal: () -> Unit,
    onUseCloud: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_conflict_title), color = BsTheme.colors.Mist) },
        text = {
            Text(
                text = stringResource(R.string.sync_conflict_message),
                color = BsTheme.colors.MistDim,
            )
        },
        confirmButton = {
            BsGhostButton(
                text = stringResource(R.string.sync_conflict_keep_local),
                onClick = onKeepLocal,
            )
        },
        dismissButton = {
            Column {
                BsGhostButton(
                    text = stringResource(R.string.sync_conflict_use_cloud),
                    onClick = onUseCloud,
                )
                BsGhostButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                )
            }
        },
        containerColor = BsTheme.colors.GlassStrong,
    )
}
