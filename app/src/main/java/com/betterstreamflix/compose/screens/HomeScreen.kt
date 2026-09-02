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
import com.betterstreamflix.compose.components.posterOf
import com.betterstreamflix.models.Category
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow

@Composable
fun HomeScreen(
    categories: List<Category> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    scrollToCategoryName: String? = null,
    onProviderClick: () -> Unit = {},
    onItemClick: (AppAdapter.Item) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val featured = categories
        .firstOrNull { it.name == Category.FEATURED || it.name.equals("Featured", ignoreCase = true) }
        ?.list
        ?.firstOrNull()
        ?: categories.firstOrNull { it.list.isNotEmpty() }?.list?.firstOrNull()
    val rows = categories.filter { it.list.isNotEmpty() && it.name != Category.FEATURED }

    LaunchedEffect(scrollToCategoryName, rows) {
        val target = scrollToCategoryName ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.name == target }
        if (index >= 0) {
            // +1 for hero item
            listState.animateScrollToItem(index + 1)
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
                            else -> "Watch something great"
                        }.ifBlank { "Watch something great" }
                        val heroImage = featured?.let(::posterOf)
                        BsHeroBanner(
                            title = heroTitle,
                            subtitle = "Stream from your favorite providers — curated for tonight.",
                            imageUrl = heroImage,
                            ctaLabel = if (featured != null) "Open now" else "Choose provider",
                            onCta = {
                                if (featured != null) onItemClick(featured) else onProviderClick()
                            },
                        )
                    }
                    itemsIndexed(
                        rows,
                        key = { index, category -> "${category.name}#$index" },
                    ) { _, category ->
                        BsContentRow(
                            title = category.name.ifBlank { "Featured" },
                            items = category.list,
                            labelOf = ::itemLabel,
                            onItemClick = onItemClick,
                        )
                    }
                    item(key = "provider-link") {
                        BsGhostButton(
                            text = "Switch provider",
                            onClick = onProviderClick,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 20.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun itemLabel(item: AppAdapter.Item): String = when (item) {
    is Movie -> item.title.ifBlank { item.id }
    is TvShow -> item.title.ifBlank { item.id }
    is Episode -> item.title?.ifBlank { item.id } ?: item.id
    else -> item.toString()
}
