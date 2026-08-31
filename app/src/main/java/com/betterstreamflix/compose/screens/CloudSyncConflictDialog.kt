package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.betterstreamflix.R
import com.betterstreamflix.compose.theme.BetterStreamflixTheme

@Composable
fun CloudSyncConflictDialog(
    onKeepLocal: () -> Unit,
    onUseCloud: () -> Unit,
    onDismiss: () -> Unit,
) {
    BetterStreamflixTheme {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.sync_conflict_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.sync_conflict_message))
                    TextButton(onClick = onKeepLocal) {
                        Text(stringResource(R.string.sync_conflict_keep_local))
                    }
                    TextButton(onClick = onUseCloud) {
                        Text(stringResource(R.string.sync_conflict_use_cloud))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
