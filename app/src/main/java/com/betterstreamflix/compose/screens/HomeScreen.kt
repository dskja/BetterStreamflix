package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsContentRow
import com.betterstreamflix.compose.components.BsErrorState
import com.betterstreamflix.compose.components.BsShimmerRow
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
import com.betterstreamflix.models.Category

@Composable
fun HomeScreen(
    categories: List<Category> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    scrollToCategoryName: String? = null,
    onProviderClick: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val visibleCategories = categories.filter { it.list.isNotEmpty() }

    LaunchedEffect(scrollToCategoryName, visibleCategories) {
        val target = scrollToCategoryName ?: return@LaunchedEffect
        val index = visibleCategories.indexOfFirst { it.name == target }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    BetterStreamflixTheme {
        Scaffold(topBar = { BsTopBar(title = stringResource(R.string.main_menu_home)) }) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when {
                    isLoading -> BsShimmerRow()
                    errorMessage != null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BsErrorState(message = errorMessage)
                            Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                                Text(stringResource(R.string.loading_error_retry))
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(visibleCategories, key = { it.name }) { category ->
                                BsContentRow(
                                    title = category.name,
                                    items = category.list.map { item ->
                                        when (item) {
                                            is com.betterstreamflix.models.Movie -> item.title.ifBlank { item.id }
                                            is com.betterstreamflix.models.TvShow -> item.title.ifBlank { item.id }
                                            is com.betterstreamflix.models.Episode -> item.title?.ifBlank { item.id } ?: item.id
                                            else -> item.toString()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
