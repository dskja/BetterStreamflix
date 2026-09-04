package com.betterstreamflix.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsGlassPanel
import com.betterstreamflix.compose.components.BsPrimaryButton
import com.betterstreamflix.compose.theme.BsTheme
import com.betterstreamflix.download.DownloadPolicyManager
import com.betterstreamflix.download.DownloadStorageManager

@Composable
fun DownloadSettingsSheet(
    onDismiss: () -> Unit,
    onClearCompleted: () -> Unit,
    onClearFailed: () -> Unit,
) {
    val context = LocalContext.current
    var wifiOnly by remember { mutableStateOf(DownloadPolicyManager.isWifiOnly(context)) }
    var minBattery by remember { mutableIntStateOf(DownloadPolicyManager.getMinBatteryLevel(context)) }
    val used = remember { DownloadStorageManager.getDownloadSize(context) }
    val free = remember { DownloadStorageManager.getAvailableSpace(context) }

    Dialog(onDismissRequest = onDismiss) {
        BsGlassPanel(
            modifier = Modifier.fillMaxWidth(),
            corner = 20.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.download_settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = BsTheme.colors.Mist,
                )
                Text(
                    text = stringResource(
                        R.string.download_settings_storage,
                        DownloadStorageManager.formatSize(used),
                        DownloadStorageManager.formatSize(free),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = BsTheme.colors.MistDim,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = BsTheme.colors.Hairline)
                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.download_settings_wifi_only),
                    subtitle = stringResource(R.string.download_settings_wifi_only_hint),
                    checked = wifiOnly,
                    onChecked = {
                        wifiOnly = it
                        DownloadPolicyManager.setWifiOnly(context, it)
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsStepperRow(
                    title = stringResource(R.string.download_settings_min_battery),
                    subtitle = stringResource(R.string.download_settings_min_battery_hint, minBattery),
                    value = minBattery,
                    onDecrease = {
                        minBattery = (minBattery - 5).coerceAtLeast(0)
                        DownloadPolicyManager.setMinBatteryLevel(context, minBattery)
                    },
                    onIncrease = {
                        minBattery = (minBattery + 5).coerceAtMost(80)
                        DownloadPolicyManager.setMinBatteryLevel(context, minBattery)
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = BsTheme.colors.Hairline)
                Spacer(modifier = Modifier.height(12.dp))
                BsGhostButton(
                    text = stringResource(R.string.downloads_clear_completed),
                    onClick = onClearCompleted,
                )
                BsGhostButton(
                    text = stringResource(R.string.downloads_clear_failed),
                    onClick = onClearFailed,
                )
                Spacer(modifier = Modifier.height(12.dp))
                BsPrimaryButton(
                    text = stringResource(R.string.download_settings_done),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BsTheme.colors.GlassSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = BsTheme.colors.Mist)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = BsTheme.colors.MistDim)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
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

@Composable
private fun SettingsStepperRow(
    title: String,
    subtitle: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BsTheme.colors.GlassSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = BsTheme.colors.Mist)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = BsTheme.colors.MistDim)
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BsGhostButton(text = "−", onClick = onDecrease)
            Text(text = "$value%", style = MaterialTheme.typography.titleMedium, color = BsTheme.colors.AmberBright)
            BsGhostButton(text = "+", onClick = onIncrease)
        }
    }
}
