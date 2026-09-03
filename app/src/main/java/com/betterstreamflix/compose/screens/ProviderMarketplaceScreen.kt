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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsProviderChip
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.providers.Provider
import com.betterstreamflix.providers.ProviderHealthMonitor

@Composable
fun ProviderMarketplaceScreen(
    onProviderSelected: (Provider) -> Unit = {},
) {
    var languageFilter by remember { mutableStateOf<String?>(null) }
    val providers = remember(languageFilter) {
        Provider.providers.keys
            .filter { languageFilter == null || it.language == languageFilter }
            .sortedBy { !ProviderHealthMonitor.isHealthy(it.name) }
    }
    val languageOptions = listOf(
        null to stringResource(R.string.marketplace_lang_all),
        "en" to "EN",
        "de" to "DE",
        "it" to "IT",
        "es" to "ES",
        "fr" to "FR",
    )

    BsAtmosphere {
        Column(modifier = Modifier.fillMaxSize()) {
            BsTopBar(
                title = stringResource(R.string.provider_marketplace_title),
                showBrand = true,
            )
            Text(
                text = stringResource(R.string.marketplace_filter_language),
                style = MaterialTheme.typography.labelMedium,
                color = BsColors.MistFaint,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(languageOptions) { (code, label) ->
                    FilterChip(
                        selected = languageFilter == code,
                        onClick = { languageFilter = code },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BsColors.Amber,
                            selectedLabelColor = BsColors.Ink,
                            containerColor = BsColors.InkPanel,
                            labelColor = BsColors.MistDim,
                        ),
                    )
                }
            }
            if (providers.isEmpty()) {
                BsEmptyState(
                    message = stringResource(R.string.marketplace_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(providers, key = { it.name }) { provider ->
                        val healthy = ProviderHealthMonitor.isHealthy(provider.name)
                        BsProviderChip(
                            label = "${provider.name} (${provider.language})",
                            selected = false,
                            healthy = healthy,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onProviderSelected(provider) },
                        )
                    }
                }
            }
        }
    }
}
