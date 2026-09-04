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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * Liquid-glass panel shell — translucent fill, specular top edge, hairline border.
 */
@Composable
fun BsGlassPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    corner: androidx.compose.ui.unit.Dp = 18.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) BsTheme.colors.GlassPanelSelected else BsTheme.colors.GlassPanel)
            .border(
                width = 1.dp,
                color = if (selected) BsTheme.colors.FocusRing else BsTheme.colors.Hairline,
                shape = shape,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .background(BsTheme.colors.SpecularEdge),
        )
        content()
    }
}

@Composable
fun BsAtmosphere(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = BsTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.Atmosphere),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .fillMaxHeight(0.55f)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.SeaGlassSoft.copy(alpha = 0.26f),
                            colors.SeaGlassSoft.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .fillMaxHeight(0.48f)
                .align(Alignment.BottomStart)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.Amber.copy(alpha = 0.22f),
                            colors.Amber.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .fillMaxHeight(0.28f)
                .align(Alignment.Center)
                .offset(y = (-40).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.SpecularSoft,
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.AtmosphereSheen),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.CinemaVignette),
        )
        content()
    }
}

@Composable
fun BsBrandMark(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val pulse = rememberInfiniteTransition(label = "brandPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(BsMotion.BrandPulse, RepeatMode.Reverse),
        label = "brandGlow",
    )
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.bs_brand_mark),
            style = if (compact) {
                MaterialTheme.typography.titleSmall.copy(letterSpacing = 1.4.sp)
            } else {
                MaterialTheme.typography.headlineMedium.copy(letterSpacing = 1.8.sp)
            },
            color = BsTheme.colors.Mist,
        )
        Box(
            modifier = Modifier
                .padding(top = if (compact) 5.dp else 8.dp)
                .width(if (compact) 40.dp else 64.dp)
                .height(if (compact) 2.dp else 3.dp)
                .graphicsLayer { alpha = glow }
                .background(BsTheme.colors.AmberGlow, RoundedCornerShape(2.dp)),
        )
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BsTheme.colors.SpecularEdge),
        )
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
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            actions()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BsTheme.colors.Hairline),
        )
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
                    .background(BsTheme.colors.AmberGlow, RoundedCornerShape(2.dp)),
            )
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = BsTheme.colors.MistDim,
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
    BsGlassPanel(modifier = modifier.fillMaxWidth(), corner = 14.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BsTheme.colors.BannerStrip)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(BsTheme.colors.AmberBright),
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
        animationSpec = BsMotion.PressSpring,
        label = "primaryPress",
    )
    Button(
        onClick = onClick,
        modifier = modifier
            .height(50.dp)
            .scale(scale),
        shape = RoundedCornerShape(14.dp),
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = BsTheme.colors.Amber,
            contentColor = BsTheme.colors.Ink,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BsGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BsTheme.colors.GlassSoft)
            .border(1.dp, BsTheme.colors.Hairline, RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.textButtonColors(contentColor = BsTheme.colors.AmberBright),
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 420.dp else 560.dp)
            .alpha(alpha),
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .background(BsTheme.colors.SpecularEdge),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(y = rise.dp)
                .padding(horizontal = 22.dp, vertical = 36.dp),
        ) {
            if (brandVisible) {
                BsBrandMark()
                Spacer(modifier = Modifier.height(22.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = BsTheme.colors.Mist,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = BsTheme.colors.MistDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BsPrimaryButton(text = ctaLabel, onClick = onCta)
                if (!secondaryCtaLabel.isNullOrBlank() && onSecondaryCta != null) {
                    BsGhostButton(text = secondaryCtaLabel, onClick = onSecondaryCta)
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp)
            .alpha(rowAlpha),
    ) {
        BsSectionHeader(title = title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(items, key = { index, item -> "${itemKeyOf(item)}#$index" }) { _, item ->
                if (showProgress) {
                    BsContinueWatchingCard(
                        title = labelOf(item),
                        imageUrl = imageOf(item),
                        progress = progressOf(item),
                        subtitle = continueSubtitleOf(item),
                        onClick = { onItemClick(item, showProgress) },
                        onLongClick = { onItemLongClick(item) },
                    )
                } else {
                    BsPosterCard(
                        title = labelOf(item),
                        imageUrl = imageOf(item),
                        onClick = { onItemClick(item, showProgress) },
                        onLongClick = { onItemLongClick(item) },
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
        animationSpec = BsMotion.FocusSpring,
        label = "cwScale",
    )
    Column(
        modifier = modifier
            .width(148.dp)
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
                .height(200.dp)
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
                    trackColor = BsTheme.colors.Ink.copy(alpha = 0.40f),
                )
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = BsTheme.colors.AmberBright,
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
        animationSpec = BsMotion.FocusSpring,
        label = "genreTileScale",
    )
    BsGlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        selected = focused,
        corner = 16.dp,
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
                    .background(BsTheme.colors.AmberGlow, RoundedCornerShape(2.dp)),
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
        animationSpec = BsMotion.FocusSpring,
        label = "posterScale",
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
            )
            .then(
                if (focused) Modifier.border(2.dp, BsTheme.colors.FocusRing, RoundedCornerShape(12.dp))
                else Modifier,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(186.dp)
                .clip(RoundedCornerShape(14.dp))
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
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, BsTheme.colors.Ink.copy(alpha = 0.80f)),
                        ),
                    ),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.15.sp),
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
        animationSpec = BsMotion.FocusSpring,
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
        corner = 14.dp,
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
                            .background(BsTheme.colors.AmberGlow),
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
        animationSpec = BsMotion.FocusSpring,
        label = "glassFilterScale",
    )
    Box(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    selected || focused -> BsTheme.colors.GlassPanelSelected
                    else -> BsTheme.colors.GlassPanel
                },
            )
            .border(
                1.dp,
                when {
                    focused -> BsTheme.colors.FocusRing
                    selected -> BsTheme.colors.FocusRing
                    else -> BsTheme.colors.Hairline
                },
                RoundedCornerShape(14.dp),
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected || focused) BsTheme.colors.Mist else BsTheme.colors.MistFaint,
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BsTheme.colors.GlassPanel)
            .border(
                1.dp,
                if (focused) BsTheme.colors.FocusRing else BsTheme.colors.Hairline,
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(BsTheme.colors.Amber),
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
        BsGlassPanel(corner = 20.dp) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .width(48.dp)
                        .height(3.dp)
                        .background(BsTheme.colors.AmberGlow, RoundedCornerShape(2.dp)),
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
        BsGlassPanel(corner = 20.dp) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
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
        animationSpec = BsMotion.FocusSpring,
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
        corner = 14.dp,
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
        animationSpec = BsMotion.FocusSpring,
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
