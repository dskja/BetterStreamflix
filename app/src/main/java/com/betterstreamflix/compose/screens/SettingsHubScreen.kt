package com.betterstreamflix.compose.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsSettingsItem
import com.betterstreamflix.compose.components.BsTopBar

@Composable
fun SettingsHubScreen(
    items: List<Pair<String, String?>> = emptyList(),
) {
    BsAtmosphere {
        Column(modifier = Modifier.fillMaxSize()) {
            BsTopBar(title = "Settings", showBrand = true)
            items.forEach { (title, subtitle) ->
                BsSettingsItem(title = title, subtitle = subtitle)
            }
        }
    }
}
