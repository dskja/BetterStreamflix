package com.betterstreamflix.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.compose.theme.BsMotion

/**
 * Small uppercase section label used to separate groups of settings rows.
 */
@Composable
fun BsSettingsSectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = BsColors.MistFaint,
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 8.dp),
    )
}

/**
 * A hub destination tile — a large, focusable navigation entry with an amber hairline
 * accent, used on the Settings hub to route into a settings sub-section.
 */
@Composable
fun BsSettingsNavTile(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    accentHint: String? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = BsMotion.FocusSpring,
        label = "navTileScale",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) BsColors.InkPanel else BsColors.InkElevated)
            .border(
                width = 1.dp,
                color = if (focused) BsColors.FocusRing else BsColors.Hairline,
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (subtitle.isNullOrBlank()) 20.dp else 34.dp)
                    .background(BsColors.AmberGlow, RoundedCornerShape(2.dp)),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = BsColors.Mist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = BsColors.MistDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
        if (!accentHint.isNullOrBlank()) {
            Text(
                text = accentHint,
                style = MaterialTheme.typography.labelSmall,
                color = BsColors.AmberBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = "\u203A",
                style = MaterialTheme.typography.titleLarge,
                color = BsColors.MistFaint,
            )
        }
    }
}

/**
 * A toggle row backed by a Material3 [Switch] with Obsidian colors.
 */
@Composable
fun BsSettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onCheckedChange(!checked) },
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = BsColors.Mist)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BsColors.MistDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BsColors.Ink,
                checkedTrackColor = BsColors.Amber,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = BsColors.MistDim,
                uncheckedTrackColor = BsColors.InkSoft,
                uncheckedBorderColor = BsColors.Hairline,
            ),
        )
    }
    BsSettingsHairline()
}

/**
 * A row that shows the current value of a setting and opens a picker/dialog on click.
 */
@Composable
fun BsSettingsValueRow(
    title: String,
    valueLabel: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .background(if (focused) BsColors.InkPanel else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = BsColors.Mist)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BsColors.MistDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.labelLarge,
            color = BsColors.AmberBright,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    BsSettingsHairline()
}

/**
 * A tappable action row, e.g. "Export backup" or "Reset domain". Supports a destructive style.
 */
@Composable
fun BsSettingsActionRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val titleColor = if (destructive) BsColors.Danger else BsColors.Mist
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .background(if (focused) BsColors.InkPanel else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BsColors.MistDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Text(
            text = "\u203A",
            style = MaterialTheme.typography.titleLarge,
            color = if (destructive) BsColors.Danger else BsColors.MistFaint,
        )
    }
    BsSettingsHairline()
}

@Composable
private fun BsSettingsHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(BsColors.Hairline),
    )
}

/**
 * A dialog that lets the user type or edit a text value (optionally password-masked).
 */
@Composable
fun BsSettingsTextFieldDialog(
    title: String,
    initial: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    subtitle: String? = null,
    confirmLabel: String = "Save",
    dismissLabel: String = "Cancel",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        containerColor = BsColors.InkElevated,
        titleContentColor = BsColors.Mist,
        textContentColor = BsColors.MistDim,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge, color = BsColors.Mist) },
        text = {
            Column {
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = BsColors.MistDim,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BsColors.Mist,
                        unfocusedTextColor = BsColors.Mist,
                        focusedBorderColor = BsColors.Amber,
                        unfocusedBorderColor = BsColors.Hairline,
                        cursorColor = BsColors.Amber,
                        focusedContainerColor = BsColors.InkPanel,
                        unfocusedContainerColor = BsColors.InkPanel,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(text = confirmLabel, color = BsColors.AmberBright)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissLabel, color = BsColors.MistDim)
            }
        },
    )
}

/**
 * A dialog that lets the user select one option from a list of value/label pairs.
 */
@Composable
fun BsSettingsChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        containerColor = BsColors.InkElevated,
        titleContentColor = BsColors.Mist,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge, color = BsColors.Mist) },
        text = {
            LazyColumn {
                items(options) { (value, label) ->
                    val isSelected = value == selectedValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { onSelect(value) },
                            )
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(value) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = BsColors.Amber,
                                unselectedColor = BsColors.MistFaint,
                            ),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) BsColors.Mist else BsColors.MistDim,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Close", color = BsColors.AmberBright)
            }
        },
    )
}
