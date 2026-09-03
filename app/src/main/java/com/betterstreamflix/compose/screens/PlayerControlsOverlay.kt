package com.betterstreamflix.compose.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.theme.BsColors
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
            .background(BsColors.ScrimBottom)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        color = BsColors.Amber,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = BsColors.Amber,
                    )
                }
            }
            Text(
                text = formatPlaybackTime(state.positionMs),
                style = MaterialTheme.typography.bodySmall,
                color = BsColors.MistDim,
            )
            Slider(
                modifier = Modifier.weight(1f),
                value = positionFraction,
                onValueChange = { fraction ->
                    onSeek((fraction * durationMs).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = BsColors.Amber,
                    activeTrackColor = BsColors.Amber,
                    inactiveTrackColor = BsColors.InkSoft,
                ),
            )
            Text(
                text = formatPlaybackTime(state.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = BsColors.MistDim,
            )
        }
        if (state.isBuffering) {
            Text(
                text = stringResource(R.string.player_buffering),
                style = MaterialTheme.typography.labelSmall,
                color = BsColors.AmberBright,
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
