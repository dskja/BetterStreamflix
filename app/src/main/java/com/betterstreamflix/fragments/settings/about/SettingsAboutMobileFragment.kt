package com.betterstreamflix.fragments.settings.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.betterstreamflix.BuildConfig
import com.betterstreamflix.analytics.AnalyticsManager
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.SettingsActions
import com.betterstreamflix.compose.screens.SettingsDestination
import com.betterstreamflix.compose.screens.SettingsExperience
import com.betterstreamflix.fragments.settings.SettingsComposeBridge

class SettingsAboutMobileFragment : ComposeHostFragment() {
    @Composable
    override fun ScreenContent() {
        val state = remember { SettingsComposeBridge.buildState(requireContext()) }
        val actions = remember {
            SettingsActions(
                onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                onOpenDownloads = {},
                onThemeSelected = {},
                onLanguageSelected = {},
                onDohSelected = {},
                onQualitySelected = {},
                onToggle = { _, _ -> },
                onEditText = { _, _ -> },
                onAction = { key ->
                    if (key == "shareDiagnostics") {
                        AnalyticsManager.shareDiagnosticReport(requireContext())
                    }
                },
            )
        }
        SettingsExperience(
            destination = SettingsDestination.About,
            onNavigate = {},
            state = state.copy(versionName = BuildConfig.VERSION_NAME),
            actions = actions,
            isTv = false,
        )
    }
}
