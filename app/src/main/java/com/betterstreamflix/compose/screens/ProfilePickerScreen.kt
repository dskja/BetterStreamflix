package com.betterstreamflix.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BsColors
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

    BsAtmosphere {
        Column(modifier = Modifier.fillMaxSize()) {
            BsTopBar(
                title = stringResource(R.string.profile_picker_title),
                showBrand = true,
                actions = {
                    BsGhostButton(
                        text = stringResource(R.string.profile_picker_add),
                        onClick = { showAddDialog = true },
                    )
                },
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BsColors.Hairline, RoundedCornerShape(14.dp))
                            .background(BsColors.InkPanel, RoundedCornerShape(14.dp))
                            .clickable { onSelect(profile) }
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = BsColors.Mist,
                                modifier = Modifier.weight(1f),
                            )
                            BsGhostButton(
                                text = stringResource(R.string.profile_picker_edit_name),
                                onClick = {
                                    editingProfile = profile
                                    editName = profile.name
                                },
                            )
                            if (profiles.size > 1) {
                                BsGhostButton(
                                    text = stringResource(R.string.profile_picker_delete),
                                    onClick = { deletingProfile = profile },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.profile_picker_kids),
                                color = BsColors.MistDim,
                            )
                            Switch(
                                checked = profile.isKids,
                                onCheckedChange = { checked -> onToggleKids(profile, checked) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = BsColors.Ink,
                                    checkedTrackColor = BsColors.Amber,
                                ),
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
                                color = BsColors.MistFaint,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            ProfileDialog(
                title = stringResource(R.string.profile_picker_add),
                value = newProfileName,
                onValueChange = { newProfileName = it },
                onConfirm = {
                    onAddProfile(newProfileName)
                    newProfileName = ""
                    showAddDialog = false
                },
                onDismiss = { showAddDialog = false },
            )
        }
        editingProfile?.let { profile ->
            ProfileDialog(
                title = stringResource(R.string.profile_picker_edit_name),
                value = editName,
                onValueChange = { editName = it },
                onConfirm = {
                    onEditName(profile, editName)
                    editingProfile = null
                },
                onDismiss = { editingProfile = null },
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
                    BsGhostButton(
                        text = stringResource(R.string.profile_picker_delete),
                        onClick = {
                            onDeleteProfile(profile)
                            deletingProfile = null
                        },
                    )
                },
                dismissButton = {
                    BsGhostButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { deletingProfile = null },
                    )
                },
                containerColor = BsColors.InkElevated,
            )
        }
    }
}

@Composable
private fun ProfileDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(stringResource(R.string.profile_picker_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BsColors.Amber,
                    cursorColor = BsColors.Amber,
                ),
            )
        },
        confirmButton = {
            BsGhostButton(text = stringResource(android.R.string.ok), onClick = onConfirm)
        },
        dismissButton = {
            BsGhostButton(text = stringResource(android.R.string.cancel), onClick = onDismiss)
        },
        containerColor = BsColors.InkElevated,
    )
}
