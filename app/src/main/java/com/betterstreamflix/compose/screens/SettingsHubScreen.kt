package com.betterstreamflix.compose.screens

import androidx.compose.runtime.Composable

/**
 * Legacy entry point kept for backward compatibility with existing callers.
 * Delegates to [SettingsExperience] rendering the [SettingsDestination.Hub] destination.
 */
@Composable
fun SettingsHubScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    onNavigate: (SettingsDestination) -> Unit = {},
    isTv: Boolean = false,
) {
    SettingsExperience(
        destination = SettingsDestination.Hub,
        onNavigate = onNavigate,
        state = state,
        actions = actions,
        isTv = isTv,
    )
}
