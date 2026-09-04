package com.betterstreamflix.compose.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsGlassPanel
import com.betterstreamflix.compose.theme.BsMotion
import com.betterstreamflix.compose.theme.BsTheme

data class NextEpisodeOverlayUiState(
    val posterUrl: String?,
    val meta: String,
    val title: String,
    val countdownLabel: String,
    val requestInitialFocus: Boolean = false,
)

/**
 * Next-episode card for mobile + TV. Driven by [NextEpisodeOverlayLogic] via the shared manager.
 * Play / Dismiss own FocusRequesters so TV can leave the GONE Exo chrome alone.
 */
@Composable
fun NextEpisodeOverlayCard(
    state: NextEpisodeOverlayUiState?,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isTvLayout: Boolean = false,
    onFocusWithinChanged: ((Boolean) -> Unit)? = null,
) {
    val colors = BsTheme.colors
    val playFocus = remember { FocusRequester() }
    val dismissFocus = remember { FocusRequester() }
    var hasFocusWithin by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn(BsMotion.PlayerFade) + scaleIn(
            animationSpec = BsMotion.FocusSpring,
            initialScale = 0.94f,
        ),
        exit = fadeOut(BsMotion.PlayerFade) + scaleOut(
            animationSpec = BsMotion.PlayerFade,
            targetScale = 0.96f,
        ),
        modifier = modifier,
    ) {
        val ui = state ?: return@AnimatedVisibility
        LaunchedEffect(ui.requestInitialFocus, ui.meta, ui.title) {
            if (isTvLayout && ui.requestInitialFocus) {
                runCatching { playFocus.requestFocus() }
            }
        }

        val cardAlpha = when {
            !isTvLayout -> 1f
            hasFocusWithin -> 0.96f
            else -> 0.72f
        }
        val cardWidth = if (isTvLayout) 316.dp else 252.dp
        val posterW = if (isTvLayout) 96.dp else 82.dp
        val posterH = if (isTvLayout) 54.dp else 46.dp

        BsGlassPanel(
            modifier = Modifier
                .widthIn(max = cardWidth)
                .width(cardWidth)
                .alpha(cardAlpha)
                .onFocusChanged { focusState ->
                    val focused = focusState.hasFocus || focusState.isFocused
                    if (focused != hasFocusWithin) {
                        hasFocusWithin = focused
                        onFocusWithinChanged?.invoke(focused)
                    }
                },
            selected = hasFocusWithin && isTvLayout,
        ) {
            Row(
                modifier = Modifier.padding(if (isTvLayout) 12.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isTvLayout) 12.dp else 10.dp),
            ) {
                AsyncImage(
                    model = ui.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = posterW, height = posterH)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.InkPanel),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.player_next_episode_up_next),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.MistDim,
                    )
                    Text(
                        text = ui.meta,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.Mist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = ui.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.Mist,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = ui.countdownLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.AmberBright,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (isTvLayout) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NextEpisodeActionChip(
                                label = stringResource(R.string.player_next_episode_action),
                                onClick = onPlay,
                                modifier = Modifier
                                    .focusRequester(playFocus)
                                    .focusProperties { right = dismissFocus; left = dismissFocus },
                            )
                            NextEpisodeIconChip(
                                contentDescription = stringResource(R.string.player_next_episode_dismiss),
                                onClick = onDismiss,
                                modifier = Modifier
                                    .focusRequester(dismissFocus)
                                    .focusProperties { left = playFocus; right = playFocus },
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.player_next_episode_action),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.Ink,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.Amber)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = onPlay,
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        Text(
                            text = stringResource(R.string.player_next_episode_dismiss),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.MistDim,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = onDismiss,
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NextEpisodeActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BsTheme.colors
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) colors.Amber else colors.Amber.copy(alpha = 0.85f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.Specular else colors.Hairline,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = label },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.Ink,
        )
    }
}

@Composable
private fun NextEpisodeIconChip(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BsTheme.colors
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(36.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.InkPanel.copy(alpha = if (focused) 0.95f else 0.7f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.AmberBright else colors.Hairline,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = contentDescription,
            tint = colors.Mist,
            modifier = Modifier.size(18.dp),
        )
    }
}
