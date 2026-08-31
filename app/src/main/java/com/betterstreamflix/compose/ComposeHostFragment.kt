package com.betterstreamflix.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.betterstreamflix.compose.theme.BetterStreamflixTheme

abstract class ComposeHostFragment : Fragment() {

    @Composable
    abstract fun Content()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            BetterStreamflixTheme {
                Content()
            }
        }
    }
}
