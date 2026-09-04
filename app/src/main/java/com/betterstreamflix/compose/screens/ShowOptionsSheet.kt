package com.betterstreamflix.compose.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.betterstreamflix.R
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsGlassPanel
import com.betterstreamflix.compose.components.posterOf
import com.betterstreamflix.compose.theme.BsMotion
import com.betterstreamflix.compose.theme.BsTheme
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.ui.ShowOptionsActions
import com.betterstreamflix.utils.format
import kotlinx.coroutines.CoroutineScope
import androidx.lifecycle.lifecycleScope
import com.betterstreamflix.utils.toActivity

data class ShowOptionsAction(
    val id: String,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
fun ShowOptionsSheet(
    item: AppAdapter.Item,
    isTvLayout: Boolean,
    onDismiss: () -> Unit,
    onOpenTvShow: (TvShow) -> Unit = {},
    canOpenTvShow: Boolean = false,
    scope: CoroutineScope? = null,
) {
    val context = LocalContext.current
    val fallbackScope = rememberCoroutineScope()
    val resolvedScope = scope ?: context.toActivity()?.lifecycleScope ?: fallbackScope
    val database = remember { AppDatabase.getInstance(context) }
    val actions = remember(item, canOpenTvShow) {
        buildShowOptionsActions(
            context = context,
            database = database,
            item = item,
            canOpenTvShow = canOpenTvShow,
            scope = resolvedScope,
            onOpenTvShow = onOpenTvShow,
            onDismiss = onDismiss,
        )
    }
    val header = remember(item) { showOptionsHeader(context, item) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(isTvLayout, actions.size) {
        if (isTvLayout && actions.isNotEmpty()) {
            firstFocus.requestFocus()
        }
    }

    val shape = if (isTvLayout) {
        RoundedCornerShape(0.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isTvLayout) Modifier.fillMaxHeight() else Modifier)
            .clip(shape),
    ) {
        BsAtmosphere(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isTvLayout) Modifier.fillMaxHeight() else Modifier)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (isTvLayout) 20.dp else 16.dp,
                        vertical = if (isTvLayout) 28.dp else 12.dp,
                    ),
            ) {
                if (!isTvLayout) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 10.dp)
                            .width(44.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BsTheme.colors.MistFaint),
                    )
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AsyncImage(
                        model = header.posterUrl,
                        contentDescription = header.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(if (isTvLayout) 118.dp else 92.dp)
                            .height(if (isTvLayout) 168.dp else 132.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BsTheme.colors.InkSoft),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = header.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = BsTheme.colors.Mist,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (header.subtitle.isNotBlank()) {
                            Text(
                                text = header.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BsTheme.colors.MistDim,
                                modifier = Modifier.padding(top = 6.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                actions.forEachIndexed { index, action ->
                    ShowOptionsActionRow(
                        label = action.label,
                        onClick = action.onClick,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .then(
                                if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            ),
                    )
                }

                ShowOptionsActionRow(
                    label = stringResource(R.string.option_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    muted = true,
                )
            }
        }
    }
}

@Composable
private fun ShowOptionsActionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = BsMotion.FocusSpring,
        label = "showOptionsFocus",
    )
    BsGlassPanel(
        modifier = modifier
            .fillMaxWidth()
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
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (muted) BsTheme.colors.MistDim else BsTheme.colors.Mist,
                modifier = Modifier.weight(1f),
            )
            if (!muted) {
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (focused) BsTheme.colors.Amber else BsTheme.colors.MistFaint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private data class ShowOptionsHeader(
    val title: String,
    val subtitle: String,
    val posterUrl: String?,
)

private fun showOptionsHeader(context: android.content.Context, item: AppAdapter.Item): ShowOptionsHeader {
    return when (item) {
        is Movie -> ShowOptionsHeader(
            title = item.title,
            subtitle = item.released?.format("yyyy").orEmpty(),
            posterUrl = posterOf(item),
        )
        is TvShow -> ShowOptionsHeader(
            title = item.title,
            subtitle = item.released?.format("yyyy").orEmpty(),
            posterUrl = posterOf(item),
        )
        is Episode -> {
            val subtitle = item.season?.takeIf { it.number != 0 }?.let { season ->
                context.getString(
                    R.string.episode_item_info,
                    season.number,
                    item.number,
                    item.title ?: context.getString(R.string.episode_number, item.number),
                )
            } ?: context.getString(
                R.string.episode_item_info_episode_only,
                item.number,
                item.title ?: context.getString(R.string.episode_number, item.number),
            )
            ShowOptionsHeader(
                title = item.tvShow?.title.orEmpty(),
                subtitle = subtitle,
                posterUrl = posterOf(item),
            )
        }
        else -> ShowOptionsHeader(title = "", subtitle = "", posterUrl = null)
    }
}

private fun buildShowOptionsActions(
    context: android.content.Context,
    database: AppDatabase,
    item: AppAdapter.Item,
    canOpenTvShow: Boolean,
    scope: CoroutineScope,
    onOpenTvShow: (TvShow) -> Unit,
    onDismiss: () -> Unit,
): List<ShowOptionsAction> {
    val actions = mutableListOf<ShowOptionsAction>()
    when (item) {
        is Episode -> {
            val tvShow = item.tvShow
            if (canOpenTvShow && tvShow != null) {
                actions += ShowOptionsAction(
                    id = "open_tv_show",
                    label = context.getString(R.string.option_episode_open_tv_show),
                ) {
                    onOpenTvShow(tvShow)
                    onDismiss()
                }
            }
            actions += ShowOptionsAction(
                id = "download",
                label = context.getString(R.string.option_download),
            ) {
                ShowOptionsActions.enqueueDownload(context, item, scope)
                onDismiss()
            }
            actions += ShowOptionsAction(
                id = "watched",
                label = context.getString(
                    if (item.isWatched) R.string.option_show_unwatched else R.string.option_show_watched,
                ),
            ) {
                ShowOptionsActions.toggleWatched(context, item)
                onDismiss()
            }
            actions += ShowOptionsAction(
                id = "mark_previous",
                label = context.getString(
                    if (item.isWatched) {
                        R.string.option_show_mark_all_previous_unwatched
                    } else {
                        R.string.option_show_mark_all_previous_watched
                    },
                ),
            ) {
                ShowOptionsActions.markAllPreviousWatched(context, item)
                onDismiss()
            }
            val showClear = item.watchHistory != null || (item.tvShow?.isWatching ?: false)
            if (showClear) {
                actions += ShowOptionsAction(
                    id = "clear",
                    label = context.getString(R.string.option_clear_program),
                ) {
                    ShowOptionsActions.clearProgress(context, item)
                    onDismiss()
                }
            }
        }
        is Movie -> {
            val freshMovie = database.movieDao().getById(item.id) ?: item
            actions += ShowOptionsAction(
                id = "download",
                label = context.getString(R.string.option_download),
            ) {
                ShowOptionsActions.enqueueDownload(context, item, scope)
                onDismiss()
            }
            actions += ShowOptionsAction(
                id = "favorite",
                label = context.getString(
                    if (freshMovie.isFavorite) R.string.option_show_unfavorite else R.string.option_show_favorite,
                ),
            ) {
                ShowOptionsActions.toggleFavorite(context, item, scope)
                onDismiss()
            }
            actions += ShowOptionsAction(
                id = "watched",
                label = context.getString(
                    if (freshMovie.isWatched) R.string.option_show_unwatched else R.string.option_show_watched,
                ),
            ) {
                ShowOptionsActions.toggleWatched(context, item)
                onDismiss()
            }
            if (freshMovie.watchHistory != null) {
                actions += ShowOptionsAction(
                    id = "clear",
                    label = context.getString(R.string.option_clear_program),
                ) {
                    ShowOptionsActions.clearProgress(context, item)
                    onDismiss()
                }
            }
            if (freshMovie.lastPlayedAtMillis != null) {
                actions += ShowOptionsAction(
                    id = "remove_recent",
                    label = context.getString(R.string.option_remove_recently_watched),
                ) {
                    ShowOptionsActions.removeRecentlyWatched(context, item)
                    onDismiss()
                }
            }
        }
        is TvShow -> {
            val freshTvShow = database.tvShowDao().getById(item.id) ?: item
            actions += ShowOptionsAction(
                id = "favorite",
                label = context.getString(
                    if (freshTvShow.isFavorite) R.string.option_show_unfavorite else R.string.option_show_favorite,
                ),
            ) {
                ShowOptionsActions.toggleFavorite(context, item, scope)
                onDismiss()
            }
            val showClear = freshTvShow.isWatching ||
                database.episodeDao().hasAnyWatchHistoryForTvShow(freshTvShow.id)
            if (showClear) {
                actions += ShowOptionsAction(
                    id = "clear",
                    label = context.getString(R.string.option_clear_program),
                ) {
                    ShowOptionsActions.clearWatchingProgress(context, item)
                    onDismiss()
                }
            }
            if (freshTvShow.lastPlayedAtMillis != null) {
                actions += ShowOptionsAction(
                    id = "remove_recent",
                    label = context.getString(R.string.option_remove_recently_watched),
                ) {
                    ShowOptionsActions.removeRecentlyWatched(context, item)
                    onDismiss()
                }
            }
        }
    }
    return actions
}
