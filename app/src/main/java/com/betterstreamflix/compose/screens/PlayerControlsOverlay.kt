package com.betterstreamflix.compose.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.compose.theme.BsMotion
import com.betterstreamflix.fragments.player.PlayerPlaybackController
import kotlin.math.max

private const val SEEK_STEP_MS = 10_000L

@Composable
fun PlayerControlsOverlay(
    state: PlayerPlaybackController.PlaybackUiState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationMs = max(state.durationMs, 1L)
    val positionFraction = (state.positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val playPauseLabel = if (state.isPlaying) {
        stringResource(R.string.player_pause)
    } else {
        stringResource(R.string.player_play)
    }
    val rewindLabel = stringResource(R.string.player_seek_back)
    val forwardLabel = stringResource(R.string.player_seek_forward)
    val playScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 1.04f,
        animationSpec = BsMotion.PressSpring,
        label = "playScale",
    )
    val bufferPulse = rememberInfiniteTransition(label = "bufferPulse")
    val bufferAlpha by bufferPulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "bufferAlpha",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BsColors.PlayerGlass)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.title.isNotBlank()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = BsColors.Mist,
                    maxLines = 1,
                )
                if (state.subtitle.isNotBlank()) {
                    Text(
                        text = state.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = BsColors.MistDim,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(36.dp)
                        .height(2.dp)
                        .background(BsColors.AmberGlow, RoundedCornerShape(1.dp)),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlayerSeekChip(label = "−10", contentDescription = rewindLabel) {
                onSeek((state.positionMs - SEEK_STEP_MS).coerceAtLeast(0L))
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .scale(playScale)
                    .clip(CircleShape)
                    .background(BsColors.Amber)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onPlayPause,
                    )
                    .semantics { contentDescription = playPauseLabel },
                contentAlignment = Alignment.Center,
            ) {
                if (state.isPlaying) {
                    Text(
                        text = "❚❚",
                        style = MaterialTheme.typography.titleMedium,
                        color = BsColors.Ink,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = playPauseLabel,
                        tint = BsColors.Ink,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            PlayerSeekChip(label = "+10", contentDescription = forwardLabel) {
                onSeek((state.positionMs + SEEK_STEP_MS).coerceAtMost(durationMs))
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = if (state.isPlaying) {
                    stringResource(R.string.player_overlay_live)
                } else {
                    playPauseLabel
                },
                style = MaterialTheme.typography.labelSmall,
                color = BsColors.AmberBright,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = formatPlaybackTime(state.positionMs),
                style = MaterialTheme.typography.labelLarge,
                color = BsColors.Mist,
            )
            Slider(
                modifier = Modifier.weight(1f),
                value = positionFraction,
                onValueChange = { fraction ->
                    onSeek((fraction * durationMs).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = BsColors.AmberBright,
                    activeTrackColor = BsColors.Amber,
                    inactiveTrackColor = BsColors.InkSoft,
                ),
            )
            Text(
                text = formatPlaybackTime(state.durationMs),
                style = MaterialTheme.typography.labelLarge,
                color = BsColors.MistDim,
            )
        }

        if (state.isBuffering) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.alpha(bufferAlpha),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(BsColors.AmberBright),
                )
                Text(
                    text = stringResource(R.string.player_buffering),
                    style = MaterialTheme.typography.labelMedium,
                    color = BsColors.AmberBright,
                )
            }
        }
    }
}

@Composable
private fun PlayerSeekChip(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BsColors.GlassPanel)
            .border(1.dp, BsColors.HairlineStrong, RoundedCornerShape(14.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(14.dp))
                .background(BsColors.SpecularEdge)
                .alpha(0.35f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = BsColors.AmberBright,
        )
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
