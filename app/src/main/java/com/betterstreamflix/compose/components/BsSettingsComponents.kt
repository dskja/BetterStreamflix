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
import com.betterstreamflix.compose.theme.BsTheme
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = BsTheme.colors.Mist,
        )
    }
}

/**
 * A hub destination tile — Arc navigation entry into a settings section.
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
        corner = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = BsTheme.colors.Mist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = BsTheme.colors.MistDim,
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
                    color = BsTheme.colors.AmberBright,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = "\u203A",
                    style = MaterialTheme.typography.titleLarge,
                    color = BsTheme.colors.MistFaint,
                )
            }
        }
    }
}

/**
 * A toggle row backed by a Material3 [Switch] with theme colors + TV focus.
 */
@Composable
fun BsSettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.01f else 1f,
        animationSpec = BsMotion.FocusSpring,
        label = "settingsToggleScale",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onCheckedChange(!checked) },
            )
            .background(if (focused) BsTheme.colors.GlassSoft else Color.Transparent)
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) BsTheme.colors.Amber.copy(alpha = 0.55f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = BsTheme.colors.Mist)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BsTheme.colors.MistDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BsTheme.colors.Ink,
                checkedTrackColor = BsTheme.colors.Amber,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = BsTheme.colors.MistDim,
                uncheckedTrackColor = BsTheme.colors.InkSoft,
                uncheckedBorderColor = BsTheme.colors.Hairline,
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
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.01f else 1f,
        animationSpec = BsMotion.FocusSpring,
        label = "settingsValueScale",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .background(if (focused) BsTheme.colors.GlassSoft else Color.Transparent)
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) BsTheme.colors.Amber.copy(alpha = 0.55f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = BsTheme.colors.Mist)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BsTheme.colors.MistDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.labelLarge,
            color = BsTheme.colors.AmberBright,
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
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.01f else 1f,
        animationSpec = BsMotion.FocusSpring,
        label = "settingsActionScale",
    )
    val titleColor = if (destructive) BsTheme.colors.Danger else BsTheme.colors.Mist
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .background(if (focused) BsTheme.colors.GlassSoft else Color.Transparent)
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) BsTheme.colors.Amber.copy(alpha = 0.55f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
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
                    color = BsTheme.colors.MistDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Text(
            text = "\u203A",
            style = MaterialTheme.typography.titleLarge,
            color = if (destructive) BsTheme.colors.Danger else BsTheme.colors.MistFaint,
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
            .background(BsTheme.colors.Hairline),
    )
}

/**
 * Full-width Arc theme picker row (safe inside LazyColumn — no nested LazyRow).
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
        corner = 12.dp,
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
                        .background(BsTheme.colors.Specular),
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
                    color = if (selected) BsTheme.colors.Mist else BsTheme.colors.MistDim,
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
                    color = if (selected) accent else BsTheme.colors.MistFaint,
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
                        color = if (selected) accent else BsTheme.colors.HairlineStrong,
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
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.015f else 1f,
        animationSpec = BsMotion.FocusSpring,
        label = "featureCardScale",
    )
    BsGlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onCheckedChange(!checked) },
            ),
        selected = checked || focused,
        corner = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 14.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = BsTheme.colors.Mist)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BsTheme.colors.MistDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BsTheme.colors.Ink,
                    checkedTrackColor = BsTheme.colors.Amber,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = BsTheme.colors.MistDim,
                    uncheckedTrackColor = BsTheme.colors.InkSoft,
                    uncheckedBorderColor = BsTheme.colors.Hairline,
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
        containerColor = BsTheme.colors.GlassStrong,
        titleContentColor = BsTheme.colors.Mist,
        textContentColor = BsTheme.colors.MistDim,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge, color = BsTheme.colors.Mist) },
        text = {
            Column {
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = BsTheme.colors.MistDim,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BsTheme.colors.Mist,
                        unfocusedTextColor = BsTheme.colors.Mist,
                        focusedBorderColor = BsTheme.colors.Amber,
                        unfocusedBorderColor = BsTheme.colors.Hairline,
                        cursorColor = BsTheme.colors.Amber,
                        focusedContainerColor = BsTheme.colors.Glass,
                        unfocusedContainerColor = BsTheme.colors.GlassSoft,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(text = resolvedConfirm, color = BsTheme.colors.AmberBright)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = resolvedDismiss, color = BsTheme.colors.MistDim)
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
        containerColor = BsTheme.colors.GlassStrong,
        titleContentColor = BsTheme.colors.Mist,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge, color = BsTheme.colors.Mist) },
        text = {
            LazyColumn {
                items(options) { (value, label) ->
                    val isSelected = value == selectedValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) BsTheme.colors.GlassSoft else Color.Transparent)
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
                                selectedColor = BsTheme.colors.Amber,
                                unselectedColor = BsTheme.colors.MistFaint,
                            ),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) BsTheme.colors.Mist else BsTheme.colors.MistDim,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dialog_close), color = BsTheme.colors.AmberBright)
            }
        },
    )
}
