package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsErrorState
import com.betterstreamflix.compose.components.BsProviderChip
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
import com.betterstreamflix.fragments.providers.LanguageChip
import com.betterstreamflix.models.Provider
import com.betterstreamflix.providers.ProviderHealthMonitor

@Composable
fun ProvidersScreen(
    providers: List<Provider>,
    languageChips: List<LanguageChip>,
    searchQuery: String,
    isLoading: Boolean,
    errorMessage: String? = null,
    onSearchQueryChange: (String) -> Unit = {},
    onLanguageChipSelected: (LanguageChip) -> Unit = {},
    onProviderSelected: (Provider) -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    BetterStreamflixTheme {
        Scaffold(
            topBar = {
                BsTopBar(
                    title = stringResource(R.string.providers_choose_title),
                    actions = {
                        TextButton(onClick = onOpenMarketplace) {
                            Text(stringResource(R.string.provider_marketplace_title))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.providers_search_hint)) },
                    singleLine = true,
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(languageChips, key = { it.code ?: "all" }) { chip ->
                        FilterChip(
                            selected = chip.isSelected,
                            onClick = { onLanguageChipSelected(chip) },
                            label = { Text(chip.name) },
                        )
                    }
                }

                when {
                    isLoading -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    errorMessage != null -> {
                        BsErrorState(
                            message = errorMessage,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(providers, key = { index, provider -> "${provider.name}#$index" }) { _, provider ->
                                val unhealthy = !ProviderHealthMonitor.isHealthy(provider.provider.name)
                                val label = buildString {
                                    append(provider.name)
                                    if (unhealthy) append(" ⚠")
                                    if (provider.isFavorite) append(" ★")
                                }
                                BsProviderChip(
                                    label = label,
                                    selected = false,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { onProviderSelected(provider) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
