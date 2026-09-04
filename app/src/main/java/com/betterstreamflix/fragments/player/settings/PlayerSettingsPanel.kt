package com.betterstreamflix.fragments.player.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.betterstreamflix.compose.theme.BsMotion
import com.betterstreamflix.compose.theme.BsTheme
import com.betterstreamflix.fragments.player.settings.PlayerSettingsView.Item
import com.betterstreamflix.fragments.player.settings.PlayerSettingsView.Setting

@Composable
fun PlayerSettingsPanel(
    setting: Setting,
    items: List<Item>,
    title: String,
    isTvLayout: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onItemClick: (Item) -> Unit,
    modifier: Modifier = Modifier,
    refreshKey: Int = 0,
) {
    val colors = BsTheme.colors
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val selectedIndex = remember(items, refreshKey) {
        items.indexOfFirst { PlayerSettingsPresentation.isSelected(it) }.takeIf { it >= 0 } ?: 0
    }
    val focusRequesters = remember(items.size, refreshKey) {
        List(items.size) { FocusRequester() }
    }

    LaunchedEffect(setting, refreshKey, items.size) {
        if (isTvLayout && items.isNotEmpty()) {
            val target = selectedIndex.coerceIn(0, items.lastIndex)
            listState.scrollToItem(target)
            runCatching { focusRequesters[target].requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.InkElevated)
            .padding(horizontal = if (isTvLayout) 18.dp else 12.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (setting != Setting.MAIN) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = null,
                    tint = colors.Mist,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onBack,
                        )
                        .padding(6.dp),
                )
                Box(modifier = Modifier.width(4.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.Mist,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isTvLayout) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = colors.Mist,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onClose,
                        )
                        .padding(6.dp),
                )
            }
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(if (isTvLayout) 6.dp else 2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items,
                key = { index, item ->
                    "$refreshKey-$index-${item::class.java.name}-${PlayerSettingsPresentation.mainText(context, item)}"
                },
            ) { index, item ->
                SettingsRow(
                    item = item,
                    isTvLayout = isTvLayout,
                    focusRequester = focusRequesters.getOrNull(index),
                    onClick = { onItemClick(item) },
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    item: Item,
    isTvLayout: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = BsTheme.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && isTvLayout) 1.03f else 1f,
        animationSpec = BsMotion.focusSpec(),
        label = "settings-row-scale",
    )
    val title = PlayerSettingsPresentation.mainText(context, item)
    val subtitle = PlayerSettingsPresentation.subText(context, item, isTvLayout)
    val selected = PlayerSettingsPresentation.isSelected(item)
    val chevron = PlayerSettingsPresentation.showsChevron(item)
    val swatch = PlayerSettingsPresentation.colorSwatch(item)
    val icon = PlayerSettingsPresentation.iconRes(item)
    val spacing = PlayerSettingsPresentation.spacingFor(item)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.top, bottom = spacing.bottom)
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = isTvLayout)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    focused && isTvLayout -> colors.InkPanel
                    else -> Color.Transparent
                },
            )
            .border(
                width = if (focused && isTvLayout) 1.5.dp else 0.dp,
                color = if (focused && isTvLayout) colors.AmberBright else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = if (isTvLayout) 14.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatch != null) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(swatch)),
            )
            Box(modifier = Modifier.width(12.dp))
        } else if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.Mist,
                modifier = Modifier.size(22.dp),
            )
            Box(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.Mist,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.MistDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.Amber,
                modifier = Modifier.size(20.dp),
            )
        } else if (chevron) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.MistDim,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
