package com.betterstreamflix.compose.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.betterstreamflix.R
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.compose.theme.BsTheme
import com.betterstreamflix.compose.theme.BsMotion
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Genre
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.utils.format

/** Arc panel — near-black fill, quiet border, vermilion focus. */
@Composable
fun BsGlassPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    corner: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) BsTheme.colors.InkSoft else BsTheme.colors.InkPanel)
            .border(
                width = 1.dp,
                color = if (selected) BsTheme.colors.Amber.copy(alpha = 0.55f) else BsTheme.colors.Hairline,
                shape = shape,
            ),
        content = { content() },
    )
}

@Composable
fun BsAtmosphere(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BsTheme.colors.Ink),
    ) {
        content()
    }
}

@Composable
fun BsBrandMark(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.app_name).take(6).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = if (compact) 1.8.sp else 2.4.sp,
            ),
            color = BsTheme.colors.Amber,
        )
        Text(
            text = stringResource(R.string.bs_brand_mark).drop(6).uppercase(),
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
            color = BsTheme.colors.Mist,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
fun BsHomeChrome(
    providerName: String?,
    onProviderClick: () -> Unit,
    onMoviesClick: () -> Unit,
    onTvShowsClick: () -> Unit,
    modifier: Modifier = Modifier,
    isTvLayout: Boolean = false,
) {
    val padding = if (isTvLayout) 32.dp else 20.dp
    var providerFocused by remember { mutableStateOf(false) }
    val providerScale by animateFloatAsState(
        targetValue = if (providerFocused) 1.08f else 1f,
        animationSpec = BsMotion.focusSpec(),
        label = "providerAvatarScale",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = padding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BsBrandMark(compact = true)
            Box(
                modifier = Modifier
                    .size(if (isTvLayout) 44.dp else 38.dp)
                    .scale(providerScale)
                    .clip(RoundedCornerShape(50))
                    .background(if (providerFocused) BsTheme.colors.Amber else BsTheme.colors.InkPanel)
                    .border(
                        1.dp,
                        if (providerFocused) BsTheme.colors.Mist else BsTheme.colors.Hairline,
                        RoundedCornerShape(50),
                    )
                    .onFocusChanged { providerFocused = it.isFocused }
                    .focusable()
                    .clickable(onClick = onProviderClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = providerName?.firstOrNull()?.uppercase()
                        ?: stringResource(R.string.app_name).take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = BsTheme.colors.Mist,
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = padding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                BsGlassFilterChip(
                    label = stringResource(R.string.main_menu_home),
                    selected = true,
                    onClick = {},
                )
            }
            item {
                BsGlassFilterChip(
                    label = stringResource(R.string.main_menu_movies),
                    selected = false,
                    onClick = onMoviesClick,
                )
            }
            item {
                BsGlassFilterChip(
                    label = stringResource(R.string.main_menu_tv_shows),
                    selected = false,
                    onClick = onTvShowsClick,
                )
            }
            item {
                BsGlassFilterChip(
                    label = providerName ?: stringResource(R.string.main_menu_change_provider),
                    selected = false,
                    onClick = onProviderClick,
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
fun BsTopBar(
    title: String,
    modifier: Modifier = Modifier,
    showBrand: Boolean = false,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BsTheme.colors.Ink),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    BsIconButton(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                        onClick = onBack,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                if (showBrand) {
                    BsBrandMark(compact = true)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .width(1.dp)
                            .height(28.dp)
                            .background(BsTheme.colors.HairlineStrong),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = BsTheme.colors.Mist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = BsTheme.colors.MistDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
            actions()
        }
    }
}

@Composable
fun BsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = (-0.2).sp),
                color = BsTheme.colors.Mist,
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun BsStatusBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    BsGlassPanel(modifier = modifier.fillMaxWidth(), corner = 10.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BsTheme.colors.Amber.copy(alpha = 0.10f))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(6.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(BsTheme.colors.Amber),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = BsTheme.colors.AmberBright,
            )
        }
    }
}

@Composable
fun BsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    containerColor: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.98f
            focused -> 1.04f
            else -> 1f
        },
        animationSpec = BsMotion.pressSpec(),
        label = "primaryPress",
    )
    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = 1.dp,
                color = if (focused) BsTheme.colors.Mist.copy(alpha = 0.85f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            ),
        shape = RoundedCornerShape(10.dp),
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor ?: BsTheme.colors.Amber,
            contentColor = BsTheme.colors.Mist,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        leadingIcon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(18.dp),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BsGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = BsMotion.focusSpec(),
        label = "ghostFocus",
    )
    TextButton(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) BsTheme.colors.InkSoft else BsTheme.colors.InkPanel)
            .border(
                1.dp,
                if (focused) BsTheme.colors.MistDim else BsTheme.colors.HairlineStrong,
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused },
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (focused) BsTheme.colors.Mist else BsTheme.colors.MistDim,
        ),
    ) {
        leadingIcon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(18.dp),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BsIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = BsMotion.focusSpec(),
        label = "iconButtonFocus",
    )
    Box(
        modifier = modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (focused) BsTheme.colors.Amber else BsTheme.colors.InkPanel.copy(alpha = 0.88f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) BsTheme.colors.Mist else BsTheme.colors.HairlineStrong,
                shape = CircleShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = BsTheme.colors.Mist,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun BsHeroBanner(
    brandVisible: Boolean = true,
    title: String,
    subtitle: String?,
    imageUrl: String?,
    metadata: List<String> = emptyList(),
    ctaLabel: String,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryCtaLabel: String? = null,
    onSecondaryCta: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = BsMotion.HeroFade,
        label = "heroAlpha",
    )
    val rise by animateFloatAsState(
        targetValue = if (visible) 0f else 14f,
        animationSpec = BsMotion.HeroRise,
        label = "heroRise",
    )
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = modifier
            .padding(horizontal = if (compact) 28.dp else 12.dp)
            .fillMaxWidth()
            .height(if (compact) 460.dp else 397.dp)
            .alpha(alpha)
            .clip(RoundedCornerShape(22.dp))
            .background(BsTheme.colors.InkSoft)
            .border(1.dp, BsTheme.colors.Hairline, RoundedCornerShape(22.dp)),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(BsTheme.colors.InkSoft),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BsTheme.colors.HeroWash),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BsTheme.colors.HeroSideWash),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(y = rise.dp)
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            if (brandVisible) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(BsTheme.colors.Ink.copy(alpha = 0.76f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(BsTheme.colors.Amber),
                    )
                    Text(
                        text = stringResource(R.string.home_featured_fallback).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = BsTheme.colors.Mist,
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                color = BsTheme.colors.Mist,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (metadata.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    metadata.take(4).forEachIndexed { index, item ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(BsTheme.colors.MistFaint),
                            )
                        }
                        Text(
                            text = item,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (index == 0 && item.startsWith("★")) {
                                BsTheme.colors.SeaGlass
                            } else {
                                BsTheme.colors.MistDim
                            },
                        )
                    }
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BsTheme.colors.MistDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BsPrimaryButton(
                    text = ctaLabel,
                    onClick = onCta,
                    leadingIcon = Icons.Filled.PlayArrow,
                )
                if (!secondaryCtaLabel.isNullOrBlank() && onSecondaryCta != null) {
                    BsGhostButton(
                        text = secondaryCtaLabel,
                        onClick = onSecondaryCta,
                        leadingIcon = Icons.Outlined.Info,
                    )
                }
            }
        }
    }
}

@Composable
fun BsContentRow(
    title: String,
    items: List<AppAdapter.Item>,
    labelOf: (AppAdapter.Item) -> String,
    modifier: Modifier = Modifier,
    imageOf: (AppAdapter.Item) -> String? = { posterOf(it) },
    onItemClick: (AppAdapter.Item, fromContinueWatching: Boolean) -> Unit = { _, _ -> },
    onItemLongClick: (AppAdapter.Item) -> Unit = {},
    showProgress: Boolean = false,
) {
    if (items.isEmpty()) return
    var entered by remember { mutableStateOf(false) }
    val rowAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = BsMotion.SoftEnter,
        label = "rowAlpha",
    )
    LaunchedEffect(title) { entered = true }
    val listState = rememberLazyListState()
    var restoredFocusKey by remember(title) { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp)
            .alpha(rowAlpha),
    ) {
        BsSectionHeader(title = title)
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(items, key = { index, item -> "${itemKeyOf(item)}#$index" }) { index, item ->
                val itemKey = itemKeyOf(item)
                val focusRequester = remember(itemKey) { FocusRequester() }
                LaunchedEffect(restoredFocusKey, items) {
                    if (restoredFocusKey == itemKey) {
                        runCatching { focusRequester.requestFocus() }
                    }
                }
                val focusModifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            restoredFocusKey = itemKey
                        }
                    }
                if (showProgress) {
                    BsContinueWatchingCard(
                        title = labelOf(item),
                        imageUrl = imageOf(item),
                        progress = progressOf(item),
                        subtitle = continueSubtitleOf(item),
                        onClick = { onItemClick(item, showProgress) },
                        onLongClick = { onItemLongClick(item) },
                        modifier = focusModifier,
                    )
                } else {
                    BsPosterCard(
                        title = labelOf(item),
                        imageUrl = imageOf(item),
                        subtitle = metadataOf(item).take(2).joinToString(" · ").ifBlank { null },
                        onClick = { onItemClick(item, showProgress) },
                        onLongClick = { onItemLongClick(item) },
                        modifier = focusModifier,
                    )
                }
            }
        }
    }
}

fun progressOf(item: AppAdapter.Item): Float? = when (item) {
    is Movie -> item.watchHistory?.let { history ->
        if (history.durationMillis > 0L) {
            (history.lastPlaybackPositionMillis.toFloat() / history.durationMillis).coerceIn(0f, 1f)
        } else null
    }
    is Episode -> item.watchHistory?.let { history ->
        if (history.durationMillis > 0L) {
            (history.lastPlaybackPositionMillis.toFloat() / history.durationMillis).coerceIn(0f, 1f)
        } else null
    }
    else -> null
}

fun continueSubtitleOf(item: AppAdapter.Item): String? = when (item) {
    is Episode -> {
        val season = item.season?.number?.takeIf { it > 0 }
        when {
            season != null -> "S$season E${item.number}"
            item.number > 0 -> "E${item.number}"
            else -> null
        }
    }
    is Movie -> item.released?.format("yyyy")
    else -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BsContinueWatchingCard(
    title: String,
    imageUrl: String?,
    progress: Float?,
    subtitle: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = BsMotion.focusSpec(),
        label = "cwScale",
    )
    Column(
        modifier = modifier
            .width(124.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BsTheme.colors.InkSoft)
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) BsTheme.colors.Amber else BsTheme.colors.Hairline,
                    RoundedCornerShape(12.dp),
                ),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (progress != null && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = BsTheme.colors.Amber,
                    trackColor = BsTheme.colors.Ink.copy(alpha = 0.45f),
                )
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = BsTheme.colors.MistFaint,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = BsTheme.colors.Mist,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
    }
}

fun itemKeyOf(item: AppAdapter.Item): String = when (item) {
    is Movie -> "movie:${item.id}"
    is TvShow -> "tv:${item.id}"
    is Episode -> "episode:${item.id}"
    is Genre -> "genre:${item.id}"
    else -> item.hashCode().toString()
}

fun itemLabelOf(item: AppAdapter.Item): String = when (item) {
    is Movie -> item.title.ifBlank { item.id }
    is TvShow -> item.title.ifBlank { item.id }
    is Episode -> item.title?.ifBlank { item.id } ?: item.id
    is Genre -> item.name.ifBlank { item.id }
    else -> item.toString()
}

fun posterOf(item: AppAdapter.Item): String? = when (item) {
    is Movie -> item.poster ?: item.banner
    is TvShow -> item.poster ?: item.banner
    is Episode -> item.poster ?: item.tvShow?.poster
    else -> null
}

fun bannerOf(item: AppAdapter.Item): String? = when (item) {
    is Movie -> item.banner ?: item.poster
    is TvShow -> item.banner ?: item.poster
    is Episode -> item.tvShow?.banner ?: item.poster ?: item.tvShow?.poster
    else -> posterOf(item)
}

fun metadataOf(item: AppAdapter.Item): List<String> = buildList {
    when (item) {
        is Movie -> {
            item.rating?.takeIf { it > 0.0 }?.let { add("★ ${"%.1f".format(it)}") }
            item.released?.let { add(it.format("yyyy")) }
            item.quality?.takeIf { it.isNotBlank() }?.let(::add)
            item.runtime?.takeIf { it > 0 }?.let { add("${it}m") }
        }
        is TvShow -> {
            item.rating?.takeIf { it > 0.0 }?.let { add("★ ${"%.1f".format(it)}") }
            item.released?.let { add(it.format("yyyy")) }
            item.quality?.takeIf { it.isNotBlank() }?.let(::add)
        }
        is Episode -> {
            item.season?.number?.takeIf { it > 0 }?.let { add("S$it") }
            item.number.takeIf { it > 0 }?.let { add("E$it") }
            item.released?.let { add(it.format("yyyy")) }
        }
    }
}

@Composable
fun BsGenreTile(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = BsMotion.focusSpec(),
        label = "genreTileScale",
    )
    BsGlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        selected = focused,
        corner = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = BsTheme.colors.Mist,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (focused) BsTheme.colors.Amber else BsTheme.colors.InkSoft,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = BsTheme.colors.Mist,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BsPosterCard(
    title: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    subtitle: String? = null,
    fillWidth: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.07f else 1f,
        animationSpec = BsMotion.focusSpec(),
        label = "posterScale",
    )
    Column(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(124.dp))
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillWidth) Modifier.aspectRatio(0.70f) else Modifier.height(176.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(BsTheme.colors.InkSoft)
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) BsTheme.colors.Amber else BsTheme.colors.Hairline,
                    RoundedCornerShape(12.dp),
                ),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, BsTheme.colors.Ink.copy(alpha = 0.75f)),
                        ),
                    ),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = BsTheme.colors.Mist,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = BsTheme.colors.MistFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun BsSearchResultRow(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isGenre: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = BsMotion.focusSpec(),
        label = "searchRowScale",
    )
    BsGlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        selected = focused,
        corner = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isGenre || imageUrl.isNullOrBlank()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 56.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BsTheme.colors.InkSoft),
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BsTheme.colors.Amber),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BsTheme.colors.InkSoft),
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = BsTheme.colors.Mist,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = BsTheme.colors.MistFaint,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun BsGlassFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            focused -> 1.06f
            selected -> 1.02f
            else -> 1f
        },
        animationSpec = BsMotion.focusSpec(),
        label = "glassFilterScale",
    )
    Box(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selected -> BsTheme.colors.Mist
                    focused -> BsTheme.colors.InkSoft
                    else -> Color.Transparent
                },
            )
            .border(
                1.dp,
                when {
                    selected -> BsTheme.colors.Mist
                    focused -> BsTheme.colors.Amber
                    else -> BsTheme.colors.Hairline
                },
                RoundedCornerShape(8.dp),
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                selected -> BsTheme.colors.Ink
                focused -> BsTheme.colors.Mist
                else -> BsTheme.colors.MistDim
            },
        )
    }
}

@Composable
fun BsGlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BsTheme.colors.InkPanel)
            .border(
                1.dp,
                if (focused) BsTheme.colors.Amber.copy(alpha = 0.55f) else BsTheme.colors.Hairline,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = if (focused) BsTheme.colors.Amber else BsTheme.colors.MistFaint,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(20.dp),
        )
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(BsTheme.colors.Amber),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = BsTheme.colors.Mist),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = BsTheme.colors.MistFaint,
                        )
                    }
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            IconButton(
                onClick = { onValueChange("") },
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.search_clear_input),
                    tint = BsTheme.colors.MistDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun BsLoadMoreFooter(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loadMore")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "loadMorePulse",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BsGlassPanel(corner = 12.dp) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 28.dp, vertical = 14.dp)
                    .width(72.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BsTheme.colors.AmberBright.copy(alpha = pulse)),
            )
        }
    }
}

@Composable
fun BsEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        BsGlassPanel(corner = 12.dp) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .width(40.dp)
                        .height(2.dp)
                        .background(BsTheme.colors.Amber, RoundedCornerShape(1.dp)),
                )
                Text(
                    text = title ?: stringResource(R.string.bs_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = BsTheme.colors.Mist,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BsTheme.colors.MistDim,
                )
            }
        }
    }
}

@Composable
fun BsErrorState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        contentAlignment = Alignment.Center,
    ) {
        BsGlassPanel(corner = 12.dp) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BsTheme.colors.Danger),
                )
                Text(
                    text = stringResource(R.string.bs_error_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = BsTheme.colors.Danger,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BsTheme.colors.MistDim,
                )
            }
        }
    }
}

@Composable
fun BsShimmerRow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmerPulse",
    )
    Row(
        modifier = modifier.padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .size(width = 124.dp, height = 178.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BsTheme.colors.InkSoft.copy(alpha = pulse)),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BsProviderChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    healthy: Boolean = true,
    favorite: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = BsMotion.focusSpec(),
        label = "providerChipScale",
    )
    BsGlassPanel(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        selected = selected || focused,
        corner = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (favorite) {
                    Text(
                        text = "★",
                        style = MaterialTheme.typography.titleMedium,
                        color = BsTheme.colors.AmberBright,
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = BsTheme.colors.Mist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!healthy) {
                Text(
                    text = stringResource(R.string.provider_status_offline),
                    style = MaterialTheme.typography.labelSmall,
                    color = BsTheme.colors.Danger,
                )
            }
        }
    }
}

@Composable
fun BsSettingsItem(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = BsTheme.colors.Mist)
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = BsTheme.colors.MistDim,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(BsTheme.colors.Hairline),
        )
    }
}

@Composable
fun TvScaleOnFocus(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = BsMotion.focusSpec(),
        label = "tvFocusScale",
    )
    content(
        modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
    )
}

@Composable
fun BsDownloadProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = BsTheme.colors.Amber,
        trackColor = BsTheme.colors.InkSoft,
    )
}
