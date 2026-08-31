package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.betterstreamflix.fragments.player.PlayerPlaybackController
import kotlin.math.max

@Composable
fun PlayerControlsOverlay(
    state: PlayerPlaybackController.PlaybackUiState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationMs = max(state.durationMs, 1L)
    val positionFraction = (state.positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onPlayPause) {
                if (state.isPlaying) {
                    Text(
                        text = "❚❚",
                        style = MaterialTheme.typography.titleMedium,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                    )
                }
            }
            Text(
                text = formatPlaybackTime(state.positionMs),
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                modifier = Modifier.weight(1f),
                value = positionFraction,
                onValueChange = { fraction ->
                    onSeek((fraction * durationMs).toLong())
                },
            )
            Text(
                text = formatPlaybackTime(state.durationMs),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.isBuffering) {
            Text(
                text = "Buffering…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatPlaybackTime(positionMs: Long): String {
    if (positionMs <= 0L) return "0:00"
    val totalSeconds = positionMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
