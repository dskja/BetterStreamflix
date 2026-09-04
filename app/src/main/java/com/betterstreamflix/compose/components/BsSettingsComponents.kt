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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.theme.BsColors
import com.betterstreamflix.compose.theme.BsMotion

/**
 * Small uppercase section label used to separate groups of settings rows.
 */
@Composable
fun BsSettingsSectionLabel(
    title: String,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 10.dp)
                .width(3.dp)
                .height(12.dp)
                .background(BsColors.AmberGlow, RoundedCornerShape(2.dp)),
        )
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = BsColors.MistDim,
        )
    }
}

/**
 * A hub destination tile — liquid-glass navigation entry into a settings section.
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
        targetValue = if (focused) 1.015f else 1f,
        animationSpec = BsMotion.FocusSpring,
        label = "navTileScale",
    )
    BsGlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        selected = focused,
        corner = 16.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(if (subtitle.isNullOrBlank()) 20.dp else 34.dp)
                        .background(BsColors.AmberGlow, RoundedCornerShape(2.dp)),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
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
 * Full-width liquid-glass theme picker row (safe inside LazyColumn — no nested LazyRow).
 */
@Composable
fun BsThemePickRow(
    title: String,
    selected: Boolean,
    accent: Color,
    canvas: Color,
    soft: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            focused -> 1.01f
            selected -> 1.005f
            else -> 1f
        },
        animationSpec = BsMotion.FocusSpring,
        label = "themePickScale",
    )
    BsGlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 6.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        selected = selected || focused,
        corner = 18.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(canvas)
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BsColors.Specular),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .width(32.dp)
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .width(16.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(soft.copy(alpha = 0.85f)),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) BsColors.Mist else BsColors.MistDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (selected) {
                        stringResource(R.string.settings_theme_selected)
                    } else {
                        stringResource(R.string.settings_theme_gallery_subtitle)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) accent else BsColors.MistFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(50))
                    .border(
                        width = 1.5.dp,
                        color = if (selected) accent else BsColors.HairlineStrong,
                        shape = RoundedCornerShape(50),
                    )
                    .then(
                        if (selected) {
                            Modifier.background(accent.copy(alpha = 0.25f))
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(accent),
                    )
                }
            }
        }
    }
}

/**
 * Visual theme swatch used in compact galleries (TV or legacy). Prefer [BsThemePickRow].
 */
@Composable
fun BsThemeGalleryCard(
    title: String,
    selected: Boolean,
    accent: Color,
    canvas: Color,
    soft: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BsThemePickRow(
        title = title,
        selected = selected,
        accent = accent,
        canvas = canvas,
        soft = soft,
        onClick = onClick,
        modifier = modifier,
        horizontalPadding = 0.dp,
    )
}

/**
 * Premium glass-style settings feature card (e.g. Immersive Mode).
 */
@Composable
fun BsSettingsFeatureCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
) {
    BsGlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onCheckedChange(!checked) },
            ),
        selected = checked,
        corner = 18.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 14.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = BsColors.Mist)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BsColors.MistDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
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
    }
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
    confirmLabel: String? = null,
    dismissLabel: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val resolvedConfirm = confirmLabel ?: stringResource(R.string.dialog_save)
    val resolvedDismiss = dismissLabel ?: stringResource(R.string.option_cancel)
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
                Text(text = resolvedConfirm, color = BsColors.AmberBright)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = resolvedDismiss, color = BsColors.MistDim)
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
                Text(text = stringResource(R.string.dialog_close), color = BsColors.AmberBright)
            }
        },
    )
}
