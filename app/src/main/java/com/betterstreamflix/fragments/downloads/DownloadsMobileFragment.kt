package com.betterstreamflix.fragments.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.betterstreamflix.compose.screens.DownloadsScreen
import com.betterstreamflix.download.DownloadFeature
import com.betterstreamflix.download.OfflinePlaybackHelper

class DownloadsMobileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            DownloadsScreen(
                downloads = DownloadFeature.list(requireContext()),
                onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                onOpen = { task ->
                    OfflinePlaybackHelper.playLocal(requireContext(), task.filePath, task.title)
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let { (it as? ComposeView)?.setContent {
            DownloadsScreen(
                downloads = DownloadFeature.list(requireContext()),
                onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                onOpen = { task ->
                    OfflinePlaybackHelper.playLocal(requireContext(), task.filePath, task.title)
                },
            )
        } }
    }
}
