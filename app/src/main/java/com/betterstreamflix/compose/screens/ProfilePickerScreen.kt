package com.betterstreamflix.compose.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsAtmosphere
import com.betterstreamflix.compose.components.BsEmptyState
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsGlassPanel
import com.betterstreamflix.compose.components.BsTopBar
import com.betterstreamflix.compose.theme.BsMotion
import com.betterstreamflix.compose.theme.BsTheme
import com.betterstreamflix.utils.UserProfiles

@Composable
fun ProfilePickerScreen(
    profiles: List<UserProfiles.Profile>,
    activeId: String,
    isTvLayout: Boolean = false,
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
    var manageProfile by remember { mutableStateOf<UserProfiles.Profile?>(null) }
    val horizontalPadding = if (isTvLayout) 32.dp else 20.dp
    val minCell = if (isTvLayout) 160.dp else 132.dp

    BsAtmosphere {
        Column(modifier = Modifier.fillMaxSize()) {
            BsTopBar(
                title = stringResource(R.string.profile_picker_title),
                showBrand = true,
                horizontalPadding = horizontalPadding,
                actions = {
                    BsGhostButton(
                        text = stringResource(R.string.profile_picker_add),
                        onClick = { showAddDialog = true },
                    )
                },
            )
            if (profiles.isEmpty()) {
                BsEmptyState(
                    message = stringResource(R.string.profile_picker_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = minCell),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = horizontalPadding,
                        vertical = 20.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        ProfileAvatarCard(
                            profile = profile,
                            isActive = profile.id == activeId,
                            onSelect = { onSelect(profile) },
                            onManage = { manageProfile = profile },
                        )
                    }
                }
            }
        }

        manageProfile?.let { profile ->
            ProfileManageDialog(
                profile = profile,
                onToggleKids = { checked ->
                    onToggleKids(profile, checked)
                    manageProfile = profile.copy(isKids = checked)
                },
                onEdit = {
                    manageProfile = null
                    editingProfile = profile
                    editName = profile.name
                },
                onDelete = if (profiles.size > 1) {
                    {
                        manageProfile = null
                        deletingProfile = profile
                    }
                } else {
                    null
                },
                onDismiss = { manageProfile = null },
            )
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
                containerColor = BsTheme.colors.InkElevated,
            )
        }
    }
}

@Composable
private fun ProfileAvatarCard(
    profile: UserProfiles.Profile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onManage: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = BsMotion.FocusSpring,
        label = "profileAvatarScale",
    )
    val initials = profileInitials(profile.name)
    val avatarTint = avatarColor(profile.id)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onSelect,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(avatarTint)
                    .border(
                        width = if (isActive || focused) 3.dp else 1.dp,
                        color = when {
                            focused -> BsTheme.colors.FocusRing
                            isActive -> BsTheme.colors.Amber
                            else -> BsTheme.colors.Hairline
                        },
                        shape = CircleShape,
                    ),
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 1.2.sp),
                    color = BsTheme.colors.Mist,
                )
            }
            if (isActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BsTheme.colors.Amber)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.profile_picker_active),
                        style = MaterialTheme.typography.labelSmall,
                        color = BsTheme.colors.Ink,
                    )
                }
            }
        }
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleMedium,
            color = BsTheme.colors.Mist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        val meta = buildList {
            if (profile.isKids) add(stringResource(R.string.profile_picker_kids))
            profile.parentalMaxAge?.let { add(stringResource(R.string.profile_max_age_label, it)) }
        }.joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = BsTheme.colors.MistFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        BsGhostButton(
            text = stringResource(R.string.profile_picker_edit_name),
            onClick = onManage,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ProfileManageDialog(
    profile: UserProfiles.Profile,
    onToggleKids: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(profile.name) },
        text = {
            BsGlassPanel(corner = 14.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.profile_picker_kids),
                            color = BsTheme.colors.Mist,
                        )
                        Switch(
                            checked = profile.isKids,
                            onCheckedChange = onToggleKids,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BsTheme.colors.Ink,
                                checkedTrackColor = BsTheme.colors.Amber,
                            ),
                        )
                    }
                    BsGhostButton(
                        text = stringResource(R.string.profile_picker_edit_name),
                        onClick = onEdit,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    onDelete?.let {
                        BsGhostButton(
                            text = stringResource(R.string.profile_picker_delete),
                            onClick = it,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            BsGhostButton(text = stringResource(android.R.string.ok), onClick = onDismiss)
        },
        containerColor = BsTheme.colors.InkElevated,
    )
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
                    focusedBorderColor = BsTheme.colors.Amber,
                    cursorColor = BsTheme.colors.Amber,
                ),
            )
        },
        confirmButton = {
            BsGhostButton(text = stringResource(android.R.string.ok), onClick = onConfirm)
        },
        dismissButton = {
            BsGhostButton(text = stringResource(android.R.string.cancel), onClick = onDismiss)
        },
        containerColor = BsTheme.colors.InkElevated,
    )
}

private fun profileInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

private fun avatarColor(id: String): Color {
    val palette = listOf(
        Color(0xFF1F3A4A),
        Color(0xFF3A2F1F),
        Color(0xFF2A3A2F),
        Color(0xFF3A2432),
        Color(0xFF24323A),
        Color(0xFF2F2A3A),
    )
    val index = (id.hashCode().and(0x7FFFFFFF)) % palette.size
    return palette[index]
}
