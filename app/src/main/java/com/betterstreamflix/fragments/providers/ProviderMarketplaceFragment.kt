package com.betterstreamflix.fragments.providers

import android.content.Intent
import androidx.compose.runtime.Composable
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.ProviderMarketplaceScreen
import com.betterstreamflix.providers.Provider
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.toActivity

class ProviderMarketplaceFragment : ComposeHostFragment() {

    @Composable
    override fun ScreenContent() {
        ProviderMarketplaceScreen(
            onProviderSelected = ::selectProvider,
        )
    }

    private fun selectProvider(provider: Provider) {
        UserPreferences.currentProvider = provider
        context?.toActivity()?.apply {
            startActivity(
                Intent(this, this::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
            finish()
        }
    }
}
