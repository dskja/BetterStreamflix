package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
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
import com.betterstreamflix.compose.components.BsProviderChip
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
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

    BetterStreamflixTheme {
        Scaffold(topBar = { BsTopBar(title = stringResource(R.string.provider_marketplace_title)) }) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text("Filter by language")
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            listOf(null to "All", "en" to "EN", "de" to "DE", "it" to "IT", "es" to "ES").forEach { (code, label) ->
                                FilterChip(
                                    selected = languageFilter == code,
                                    onClick = { languageFilter = code },
                                    label = { Text(label) },
                                    modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                                )
                            }
                        }
                    }
                    items(providers, key = { it.name }) { provider ->
                        val healthy = ProviderHealthMonitor.isHealthy(provider.name)
                        BsProviderChip(
                            label = "${provider.name} (${provider.language})${if (!healthy) " ⚠" else ""}",
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
