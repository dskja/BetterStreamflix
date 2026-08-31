package com.betterstreamflix.compose.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
import com.betterstreamflix.utils.UserProfiles

@Composable
fun ProfilePickerScreen(
    profiles: List<UserProfiles.Profile>,
    activeId: String,
    onSelect: (UserProfiles.Profile) -> Unit = {},
    onAddProfile: (String) -> Unit = {},
    onDeleteProfile: (UserProfiles.Profile) -> Unit = {},
    onEditName: (UserProfiles.Profile, String) -> Unit = { _, _ -> },
    onToggleKids: (UserProfiles.Profile, Boolean) -> Unit = { _, _ -> },
) {
    var editingProfile by remember { mutableStateOf<UserProfiles.Profile?>(null) }
    var editName by remember { mutableStateOf("") }
    var deletingProfile by remember { mutableStateOf<UserProfiles.Profile?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    BetterStreamflixTheme {
        Scaffold(
            topBar = {
                BsTopBar(
                    title = stringResource(R.string.profile_picker_title),
                    actions = {
                        TextButton(onClick = { showAddDialog = true }) {
                            Text(stringResource(R.string.profile_picker_add))
                        }
                    },
                )
            },
        ) { padding ->
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        editingProfile = profile
                                        editName = profile.name
                                    },
                                ) {
                                    Text(stringResource(R.string.profile_picker_edit_name))
                                }
                                if (profiles.size > 1) {
                                    TextButton(onClick = { deletingProfile = profile }) {
                                        Text(stringResource(R.string.profile_picker_delete))
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.profile_picker_kids))
                                Switch(
                                    checked = profile.isKids,
                                    onCheckedChange = { checked -> onToggleKids(profile, checked) },
                                )
                            }
                            val badge = buildList {
                                if (profile.isKids) add(stringResource(R.string.profile_picker_kids))
                                profile.parentalMaxAge?.let { add("Max age $it") }
                                if (profile.id == activeId) add(stringResource(R.string.profile_picker_active))
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

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(stringResource(R.string.profile_picker_add)) },
                text = {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text(stringResource(R.string.profile_picker_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onAddProfile(newProfileName)
                            newProfileName = ""
                            showAddDialog = false
                        },
                    ) {
                        Text(stringResource(R.string.profile_picker_add))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }

        editingProfile?.let { profile ->
            AlertDialog(
                onDismissRequest = { editingProfile = null },
                title = { Text(stringResource(R.string.profile_picker_edit_name)) },
                text = {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.profile_picker_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onEditName(profile, editName)
                            editingProfile = null
                        },
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingProfile = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }

        deletingProfile?.let { profile ->
            AlertDialog(
                onDismissRequest = { deletingProfile = null },
                title = { Text(stringResource(R.string.profile_picker_delete)) },
                text = {
                    Text(stringResource(R.string.profile_picker_delete_confirm, profile.name))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteProfile(profile)
                            deletingProfile = null
                        },
                    ) {
                        Text(stringResource(R.string.profile_picker_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingProfile = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
    }
}
