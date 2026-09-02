package com.betterstreamflix.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.betterstreamflix.compose.theme.BetterStreamflixTheme

/**
 * Host for Compose screens.
 *
 * IMPORTANT: Do not name the screen composable `Content()` — that clashes with
 * [ComposeView.Content] inside [ComposeView.setContent] and causes infinite
 * recomposition / StackOverflowError on startup.
 */
abstract class ComposeHostFragment : Fragment() {

    @Composable
    abstract fun ScreenContent()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            BetterStreamflixTheme {
                ScreenContent()
            }
        }
    }
}
