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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/** Shared mobile inset under the floating bottom nav. */
object BsLayout {
    val NavBottomInset = 110.dp
    val ScreenGutter = 20.dp
    val HeroRadius = 32.dp
    val CardRadius = 24.dp
}

/** Pulse panel — soft glass surface, amber focus. */
@Composable
fun BsGlassPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    corner: androidx.compose.ui.unit.Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    BsTheme.colors.Amber.copy(alpha = 0.14f)
                } else {
                    BsTheme.colors.InkPanel.copy(alpha = 0.92f)
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    BsTheme.colors.AmberBright.copy(alpha = 0.65f)
                } else {
                    BsTheme.colors.HairlineStrong
                },
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
        // Top amber wash — Peacock depth without purple glow kitsch
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            BsTheme.colors.Amber.copy(alpha = 0.14f),
                            BsTheme.colors.AmberMuted.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 8.dp)
                .size(220.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            BsTheme.colors.AmberBright.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        content()
    }
}

@Composable
fun BsBrandMark(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Text(
        text = stringResource(R.string.app_name),
        style = if (compact) {
            MaterialTheme.typography.titleMedium
        } else {
            MaterialTheme.typography.headlineMedium
        },
        color = BsTheme.colors.AmberBright,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = padding, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isTvLayout) 48.dp else 42.dp)
                        .clip(RoundedCornerShape(50))
                        .background(BsTheme.colors.InkSoft)
                        .border(1.dp, BsTheme.colors.HairlineStrong, RoundedCornerShape(50))
                        .clickable(onClick = onProviderClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = providerName?.firstOrNull()?.uppercase()
                            ?: stringResource(R.string.app_name).take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = BsTheme.colors.AmberBright,
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.labelMedium,
                        color = BsTheme.colors.MistDim,
                    )
                    Text(
                        text = providerName?.let {
                            stringResource(R.string.home_hero_provider_subtitle, it)
                        } ?: stringResource(R.string.home_hero_subtitle),
                        style = MaterialTheme.typography.titleMedium,
                        color = BsTheme.colors.Mist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(BsTheme.colors.InkPanel.copy(alpha = 0.88f))
                    .border(1.dp, BsTheme.colors.HairlineStrong, RoundedCornerShape(50))
                    .clickable(onClick = onProviderClick),
                contentAlignment = Alignment.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(BsTheme.colors.Mist),
                        )
                    }
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = padding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                BsGlassFilterChip(
                    label = stringResource(R.string.main_menu_movies),
                    selected = true,
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
        Spacer(modifier = Modifier.height(14.dp))
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
            .background(BsTheme.colors.GlassTopBar),
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
                    BsGhostButton(
                        text = stringResource(R.string.settings_back),
                        onClick = onBack,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (showBrand) {
                        BsBrandMark(compact = true)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
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
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .width(3.dp)
                    .height(14.dp)
                    .background(BsTheme.colors.Amber, RoundedCornerShape(2.dp)),
            )
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
    BsGlassPanel(modifier = modifier.fillMaxWidth(), corner = 18.dp) {
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
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = BsMotion.pressSpec(),
        label = "primaryPress",
    )
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .height(54.dp)
            .scale(scale)
            .clip(shape)
            .background(BsTheme.colors.PulseCta)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF1A1200),
        )
    }
}

@Composable
fun BsGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    TextButton(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(BsTheme.colors.InkPanel.copy(alpha = 0.72f))
            .border(1.dp, BsTheme.colors.HairlineStrong, shape),
        colors = ButtonDefaults.textButtonColors(contentColor = BsTheme.colors.Mist),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BsHeroBanner(
    brandVisible: Boolean = true,
    title: String,
    subtitle: String?,
    imageUrl: String?,
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
        targetValue = if (visible) 0f else 18f,
        animationSpec = BsMotion.HeroRise,
        label = "heroRise",
    )
    LaunchedEffect(Unit) { visible = true }

    val cardRadius = BsLayout.HeroRadius
    val shape = RoundedCornerShape(cardRadius)
    val sidePad = if (compact) 28.dp else 20.dp
    val cardHeight = if (compact) 500.dp else 448.dp

    Box(
        modifier = modifier
            .padding(horizontal = sidePad)
            .fillMaxWidth()
            .alpha(alpha),
    ) {
        // Peacock under-glow — sits OUTSIDE the clipped card
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .height(56.dp)
                .offset(y = 18.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BsTheme.colors.Amber.copy(alpha = 0.55f),
                            BsTheme.colors.AmberMuted.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .shadow(
                    elevation = 28.dp,
                    shape = shape,
                    ambientColor = BsTheme.colors.Amber.copy(alpha = 0.35f),
                    spotColor = BsTheme.colors.Amber.copy(alpha = 0.45f),
                )
                .clip(shape)
                .background(BsTheme.colors.InkSoft)
                .border(1.dp, BsTheme.colors.HairlineStrong, shape),
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
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                BsTheme.colors.Ink.copy(alpha = 0.35f),
                                BsTheme.colors.Ink.copy(alpha = 0.78f),
                                BsTheme.colors.Ink.copy(alpha = 0.96f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(y = rise.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 22.dp),
            ) {
                if (brandVisible) {
                    Text(
                        text = stringResource(R.string.home_featured_fallback).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                        color = BsTheme.colors.AmberBright,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayMedium,
                    color = BsTheme.colors.Mist,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BsTheme.colors.MistDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BsPrimaryButton(
                        text = ctaLabel,
                        onClick = onCta,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    if (!secondaryCtaLabel.isNullOrBlank() && onSecondaryCta != null) {
                        BsGhostButton(
                            text = secondaryCtaLabel,
                            onClick = onSecondaryCta,
                        )
                    }
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
            .width(112.dp)
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
                .height(158.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BsTheme.colors.InkSoft),
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
        corner = 20.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .width(3.dp)
                    .height(32.dp)
                    .background(BsTheme.colors.Amber, RoundedCornerShape(2.dp)),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = BsTheme.colors.Mist,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BsPosterCard(
    title: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
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
            .width(112.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .then(
                if (focused) Modifier.border(2.dp, BsTheme.colors.AmberBright.copy(alpha = 0.75f), RoundedCornerShape(BsLayout.CardRadius))
                else Modifier,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(158.dp)
                .clip(RoundedCornerShape(BsLayout.CardRadius))
                .background(BsTheme.colors.InkSoft),
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
                        .clip(RoundedCornerShape(14.dp))
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
                        .clip(RoundedCornerShape(14.dp))
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
            focused -> 1.05f
            selected -> 1.02f
            else -> 1f
        },
        animationSpec = BsMotion.focusSpec(),
        label = "glassFilterScale",
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(shape)
            .then(
                when {
                    selected -> Modifier.background(BsTheme.colors.PulseCta)
                    focused -> Modifier.background(BsTheme.colors.InkSoft)
                    else -> Modifier.background(BsTheme.colors.InkPanel.copy(alpha = 0.72f))
                },
            )
            .border(
                1.dp,
                when {
                    selected -> Color.Transparent
                    focused -> BsTheme.colors.AmberBright.copy(alpha = 0.55f)
                    else -> BsTheme.colors.Hairline
                },
                shape,
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color(0xFF1A1200) else BsTheme.colors.Mist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BsTheme.colors.InkPanel.copy(alpha = 0.88f))
            .border(
                1.dp,
                if (focused) BsTheme.colors.AmberBright.copy(alpha = 0.55f) else BsTheme.colors.Hairline,
                shape,
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(BsTheme.colors.AmberBright),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = BsTheme.colors.Mist),
            modifier = Modifier
                .fillMaxWidth()
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
fun BsEmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        BsGlassPanel(corner = 24.dp) {
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
                    text = stringResource(R.string.bs_empty_title),
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
        BsGlassPanel(corner = 24.dp) {
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
                    .clip(RoundedCornerShape(BsLayout.CardRadius))
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
        corner = 24.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BsTheme.colors.PulseCta),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF1A1200),
                    )
                }
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
