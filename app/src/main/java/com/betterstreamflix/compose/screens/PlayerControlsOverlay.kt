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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.theme.BsMotion
import com.betterstreamflix.compose.theme.BsTheme
import com.betterstreamflix.fragments.player.PlayerPlaybackController
import kotlin.math.max

private const val SEEK_STEP_MS = 10_000L

/**
 * Premium Liquid Glass player chrome — the only visible controls.
 * Anchored bottom so gesture / video taps still reach PlayerView.
 */
@Composable
fun PlayerControlsOverlay(
    state: PlayerPlaybackController.PlaybackUiState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onPip: (() -> Unit)? = null,
    onAspectRatio: (() -> Unit)? = null,
    onExternalPlayer: (() -> Unit)? = null,
    onPreviousEpisode: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    onSkipIntro: (() -> Unit)? = null,
    onToggleLock: (() -> Unit)? = null,
    onCaptions: (() -> Unit)? = null,
) {
    if (!visible) return

    val colors = BsTheme.colors

    if (state.isLocked) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(colors.PlayerGlass)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (onToggleLock != null) {
                PlayerGlassChip(
                    label = stringResource(R.string.player_unlock_controls),
                    onClick = onToggleLock,
                )
            }
        }
        return
    }

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
        targetValue = if (state.isPlaying) 1f else 1.05f,
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
            .background(colors.PlayerGlass)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                PlayerGlassIconButton(
                    onClick = onBack,
                    contentDescription = stringResource(R.string.settings_back),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = colors.Mist,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title.ifBlank { stringResource(R.string.player_title_offline) },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.Mist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.subtitle.isNotBlank()) {
                    Text(
                        text = state.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.MistDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (onSettings != null) {
                PlayerGlassIconButton(
                    onClick = onSettings,
                    contentDescription = stringResource(R.string.player_settings),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = colors.AmberBright,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .width(40.dp)
                .height(2.dp)
                .background(colors.AmberGlow, RoundedCornerShape(1.dp)),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.canGoPrevious && onPreviousEpisode != null) {
                PlayerSeekChip(
                    label = "‹ Ep",
                    contentDescription = stringResource(R.string.player_previous_episode),
                ) { onPreviousEpisode() }
            }
            PlayerSeekChip(label = "−10", contentDescription = rewindLabel) {
                onSeek((state.positionMs - SEEK_STEP_MS).coerceAtLeast(0L))
            }
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .scale(playScale)
                    .focusable()
                    .clip(CircleShape)
                    .background(colors.Amber)
                    .border(1.dp, colors.Specular, CircleShape)
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
                        color = colors.Ink,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = playPauseLabel,
                        tint = colors.Ink,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            PlayerSeekChip(label = "+10", contentDescription = forwardLabel) {
                onSeek((state.positionMs + SEEK_STEP_MS).coerceAtMost(durationMs))
            }
            if (state.canGoNext && onNextEpisode != null) {
                PlayerSeekChip(
                    label = "Ep ›",
                    contentDescription = stringResource(R.string.player_next_episode_button),
                ) { onNextEpisode() }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (state.isPlaying) {
                    stringResource(R.string.player_overlay_live)
                } else {
                    playPauseLabel
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.AmberBright,
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
                color = colors.Mist,
            )
            Slider(
                modifier = Modifier.weight(1f),
                value = positionFraction,
                onValueChange = { fraction ->
                    onSeek((fraction * durationMs).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = colors.AmberBright,
                    activeTrackColor = colors.Amber,
                    inactiveTrackColor = colors.InkSoft.copy(alpha = 0.7f),
                ),
            )
            Text(
                text = formatPlaybackTime(state.durationMs),
                style = MaterialTheme.typography.labelLarge,
                color = colors.MistDim,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.showSkipIntro && onSkipIntro != null) {
                PlayerGlassChip(
                    label = stringResource(R.string.player_skip_intro),
                    onClick = onSkipIntro,
                )
            }
            if (onCaptions != null) {
                PlayerGlassChip(
                    label = stringResource(R.string.player_captions_short),
                    onClick = onCaptions,
                )
            }
            if (onToggleLock != null) {
                PlayerGlassChip(
                    label = stringResource(R.string.player_lock_controls),
                    onClick = onToggleLock,
                )
            }
            if (onAspectRatio != null) {
                PlayerGlassChip(
                    label = stringResource(R.string.player_aspect_ratio_fit),
                    onClick = onAspectRatio,
                )
            }
            if (onPip != null) {
                PlayerGlassChip(label = "PiP", onClick = onPip)
            }
            if (onExternalPlayer != null) {
                PlayerGlassChip(
                    label = stringResource(R.string.player_external_player_title),
                    onClick = onExternalPlayer,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (state.isBuffering) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.alpha(bufferAlpha),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.AmberBright),
                    )
                    Text(
                        text = stringResource(R.string.player_buffering),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.AmberBright,
                    )
                }
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
    val colors = BsTheme.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = BsMotion.FocusSpring,
        label = "seekChipScale",
    )
    Box(
        modifier = Modifier
            .height(44.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) colors.GlassPanelSelected else colors.GlassPanel)
            .border(
                1.dp,
                if (focused) colors.FocusRing else colors.HairlineStrong,
                RoundedCornerShape(14.dp),
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = colors.AmberBright,
        )
    }
}

@Composable
private fun PlayerGlassChip(
    label: String,
    onClick: () -> Unit,
) {
    val colors = BsTheme.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = BsMotion.FocusSpring,
        label = "glassChipScale",
    )
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = colors.AmberBright,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) colors.GlassPanelSelected else colors.GlassPanel)
            .border(
                1.dp,
                if (focused) colors.FocusRing else colors.Hairline,
                RoundedCornerShape(12.dp),
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun PlayerGlassIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    val colors = BsTheme.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        animationSpec = BsMotion.FocusSpring,
        label = "glassIconScale",
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(CircleShape)
            .background(if (focused) colors.GlassPanelSelected else colors.GlassPanel)
            .border(1.dp, if (focused) colors.FocusRing else colors.Hairline, CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
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
