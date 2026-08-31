package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.betterstreamflix.compose.components.BsSettingsItem
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BetterStreamflixTheme

@Composable
fun SettingsHubScreen(
    items: List<Pair<String, String?>> = emptyList(),
) {
    BetterStreamflixTheme {
        Scaffold(topBar = { BsTopBar(title = "Settings") }) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items.forEach { (title, subtitle) ->
                    BsSettingsItem(title = title, subtitle = subtitle)
                }
            }
        }
    }
}
