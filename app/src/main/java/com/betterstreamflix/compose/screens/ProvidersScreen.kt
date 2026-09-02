package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsErrorState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsProviderChip
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BsColors
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
    BsAtmosphere {
        Column(modifier = Modifier.fillMaxSize()) {
            BsTopBar(
                title = stringResource(R.string.providers_choose_title),
                showBrand = true,
                actions = {
                    BsGhostButton(
                        text = stringResource(R.string.provider_marketplace_title),
                        onClick = onOpenMarketplace,
                    )
                },
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.providers_search_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BsColors.Amber,
                    unfocusedBorderColor = BsColors.Hairline,
                    focusedContainerColor = BsColors.InkPanel,
                    unfocusedContainerColor = BsColors.InkPanel,
                    focusedTextColor = BsColors.Mist,
                    unfocusedTextColor = BsColors.Mist,
                    cursorColor = BsColors.Amber,
                ),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(languageChips, key = { it.code ?: "all" }) { chip ->
                    FilterChip(
                        selected = chip.isSelected,
                        onClick = { onLanguageChipSelected(chip) },
                        label = { Text(chip.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BsColors.Amber,
                            selectedLabelColor = BsColors.Ink,
                            containerColor = BsColors.InkPanel,
                            labelColor = BsColors.MistDim,
                        ),
                    )
                }
            }
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BsColors.Amber)
                    }
                }
                errorMessage != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BsErrorState(message = errorMessage, modifier = Modifier.fillMaxWidth())
                        BsGhostButton(text = stringResource(R.string.loading_error_retry), onClick = onRetry)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(
                            providers,
                            key = { index, provider -> "${provider.name}#$index" },
                        ) { _, provider ->
                            val unhealthy = !ProviderHealthMonitor.isHealthy(provider.provider.name)
                            val label = buildString {
                                append(provider.name)
                                if (provider.isFavorite) append("  ★")
                            }
                            BsProviderChip(
                                label = label,
                                selected = false,
                                healthy = !unhealthy,
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
