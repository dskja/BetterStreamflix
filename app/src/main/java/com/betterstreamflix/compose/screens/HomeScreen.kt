package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.betterstreamflix.compose.components.BsContentRow
import com.betterstreamflix.compose.components.BsShimmerRow
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
import com.betterstreamflix.models.Category

@Composable
fun HomeScreen(
    categories: List<Category> = emptyList(),
    isLoading: Boolean = false,
) {
    BetterStreamflixTheme {
        Scaffold(topBar = { BsTopBar(title = "Home") }) { padding ->
            if (isLoading) {
                BsShimmerRow(modifier = Modifier.padding(padding))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    items(categories.filter { it.list.isNotEmpty() }, key = { it.name }) { category ->
                        BsContentRow(
                            title = category.name,
                            items = category.list.map { item ->
                                when (item) {
                                    is com.betterstreamflix.models.Movie -> item.title ?: item.id
                                    is com.betterstreamflix.models.TvShow -> item.title ?: item.id
                                    is com.betterstreamflix.models.Episode -> item.title ?: item.id
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
