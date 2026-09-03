package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsContentRow
import com.betterstreamflix.compose.components.BsErrorState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsHeroBanner
import com.betterstreamflix.compose.components.BsPrimaryButton
import com.betterstreamflix.compose.components.BsShimmerRow
import com.betterstreamflix.compose.components.BsStatusBanner
import com.betterstreamflix.compose.components.itemLabelOf
import com.betterstreamflix.compose.components.posterOf
import com.betterstreamflix.models.Category
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.utils.UserPreferences

@Composable
fun HomeScreen(
    categories: List<Category> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    isTvLayout: Boolean = false,
    isOffline: Boolean = false,
    isStaleCache: Boolean = false,
    parentalSessionLabel: String? = null,
    onRetry: () -> Unit = {},
    scrollToCategoryName: String? = null,
    onProviderClick: () -> Unit = {},
    onItemClick: (AppAdapter.Item, fromContinueWatching: Boolean) -> Unit = { _, _ -> },
    onItemLongClick: (AppAdapter.Item) -> Unit = {},
) {
    val horizontalPadding = if (isTvLayout) 32.dp else 16.dp
    val listState = rememberLazyListState()
    val featured = categories
        .firstOrNull { it.name == Category.FEATURED || it.name.equals("Featured", ignoreCase = true) }
        ?.list
        ?.firstOrNull()
        ?: categories.firstOrNull { it.list.isNotEmpty() }?.list?.firstOrNull()
    val rows = categories.filter { it.list.isNotEmpty() && it.name != Category.FEATURED }
    val hasStatusBanner = isOffline || isStaleCache
    val hasParentalBanner = !parentalSessionLabel.isNullOrBlank()

    LaunchedEffect(scrollToCategoryName, rows, hasStatusBanner, hasParentalBanner) {
        val target = scrollToCategoryName ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.name == target }
        val scrollIndex = homeCategoryScrollIndex(
            categoryIndex = index,
            hasStatusBanner = hasStatusBanner,
            hasParentalBanner = hasParentalBanner,
        )
        if (scrollIndex >= 0) {
            listState.animateScrollToItem(scrollIndex)
        }
    }

    BsAtmosphere {
        when {
            isLoading -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(48.dp))
                    BsShimmerRow()
                    BsShimmerRow()
                }
            }
            errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BsErrorState(message = errorMessage)
                        BsPrimaryButton(
                            text = stringResource(R.string.loading_error_retry),
                            onClick = onRetry,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "hero") {
                        val heroTitle = when (featured) {
                            is Movie -> featured.title
                            is TvShow -> featured.title
                            is Episode -> featured.tvShow?.title ?: featured.title.orEmpty()
                            else -> stringResource(R.string.home_hero_fallback_title)
                        }.ifBlank { stringResource(R.string.home_hero_fallback_title) }
                        val heroImage = featured?.let(::posterOf)
                        val providerName = UserPreferences.currentProvider?.name
                        BsHeroBanner(
                            title = heroTitle,
                            subtitle = providerName?.let {
                                stringResource(R.string.home_hero_provider_subtitle, it)
                            } ?: stringResource(R.string.home_hero_subtitle),
                            imageUrl = heroImage,
                            ctaLabel = if (featured != null) {
                                stringResource(R.string.home_hero_open_now)
                            } else {
                                stringResource(R.string.home_hero_choose_provider)
                            },
                            onCta = {
                                if (featured != null) onItemClick(featured, false) else onProviderClick()
                            },
                        )
                    }
                    if (isOffline) {
                        item(key = "offline-banner") {
                            BsStatusBanner(
                                message = stringResource(R.string.home_banner_offline),
                                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 10.dp),
                            )
                        }
                    } else if (isStaleCache) {
                        item(key = "stale-banner") {
                            BsStatusBanner(
                                message = stringResource(R.string.home_banner_stale),
                                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 10.dp),
                            )
                        }
                    }
                    if (!parentalSessionLabel.isNullOrBlank()) {
                        item(key = "parental-chip") {
                            BsStatusBanner(
                                message = parentalSessionLabel,
                                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 6.dp),
                            )
                        }
                    }
                    itemsIndexed(
                        rows,
                        key = { index, category -> "${category.name}#$index" },
                    ) { _, category ->
                        BsContentRow(
                            title = category.name.ifBlank { stringResource(R.string.home_featured_fallback) },
                            items = category.list,
                            labelOf = ::itemLabelOf,
                            showProgress = category.name == Category.CONTINUE_WATCHING,
                            onItemClick = { item, fromCw -> onItemClick(item, fromCw) },
                            onItemLongClick = onItemLongClick,
                        )
                    }
                    item(key = "provider-link") {
                        BsGhostButton(
                            text = stringResource(R.string.home_switch_provider),
                            onClick = onProviderClick,
                            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * LazyColumn index for a home category row, accounting for the hero and optional
 * status / parental banners that precede category items.
 */
internal fun homeCategoryScrollIndex(
    categoryIndex: Int,
    hasStatusBanner: Boolean,
    hasParentalBanner: Boolean,
): Int {
    if (categoryIndex < 0) return -1
    var offset = 1 // hero
    if (hasStatusBanner) offset++
    if (hasParentalBanner) offset++
    return categoryIndex + offset
}
