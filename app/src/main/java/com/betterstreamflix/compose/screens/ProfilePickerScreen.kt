package com.betterstreamflix.compose.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
import com.betterstreamflix.utils.UserProfiles

@Composable
fun ProfilePickerScreen(
    profiles: List<UserProfiles.Profile>,
    activeId: String,
    onSelect: (UserProfiles.Profile) -> Unit = {},
) {
    BetterStreamflixTheme {
        Scaffold(topBar = { BsTopBar(title = "Profiles") }) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(profile) },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            val badge = buildList {
                                if (profile.isKids) add("Kids")
                                profile.parentalMaxAge?.let { add("Max age $it") }
                                if (profile.id == activeId) add("Active")
                            }.joinToString(" · ")
                            if (badge.isNotBlank()) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
