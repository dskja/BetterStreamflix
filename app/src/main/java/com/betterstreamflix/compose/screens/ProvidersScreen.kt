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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsErrorState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsGlassFilterChip
import com.betterstreamflix.compose.components.BsGlassSearchField
import com.betterstreamflix.compose.components.BsProviderChip
import com.betterstreamflix.compose.components.BsPrimaryButton
import com.betterstreamflix.compose.components.BsShimmerRow
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BsTheme
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
    isTvLayout: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onLanguageChipSelected: (LanguageChip) -> Unit = {},
    onProviderSelected: (Provider) -> Unit = {},
    onProviderFavoriteToggle: (Provider) -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val horizontalPadding = if (isTvLayout) 32.dp else 20.dp

    BsAtmosphere {
        Column(modifier = Modifier.fillMaxSize()) {
            BsTopBar(
                title = stringResource(R.string.providers_choose_title),
                showBrand = true,
                horizontalPadding = horizontalPadding,
                actions = {
                    BsGhostButton(
                        text = stringResource(R.string.provider_marketplace_title),
                        onClick = onOpenMarketplace,
                    )
                },
            )
            BsGlassSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = stringResource(R.string.providers_search_hint),
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.providers_favorite_hint),
                color = BsTheme.colors.MistFaint,
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 2.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(languageChips, key = { it.code ?: "all" }) { chip ->
                    BsGlassFilterChip(
                        label = chip.name,
                        selected = chip.isSelected,
                        onClick = { onLanguageChipSelected(chip) },
                    )
                }
            }
            when {
                isLoading -> {
                    BsShimmerRow()
                }
                errorMessage != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BsErrorState(message = errorMessage, modifier = Modifier.fillMaxWidth())
                        BsPrimaryButton(text = stringResource(R.string.loading_error_retry), onClick = onRetry)
                    }
                }
                providers.isEmpty() -> {
                    BsEmptyState(
                        message = stringResource(R.string.providers_empty),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(
                            providers,
                            key = { index, provider -> "${provider.name}#$index" },
                        ) { _, provider ->
                            val unhealthy = !ProviderHealthMonitor.isHealthy(provider.provider.name)
                            BsProviderChip(
                                label = provider.name,
                                selected = false,
                                healthy = !unhealthy,
                                favorite = provider.isFavorite,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onProviderSelected(provider) },
                                onLongClick = { onProviderFavoriteToggle(provider) },
                            )
                        }
                    }
                }
            }
        }
    }
}
