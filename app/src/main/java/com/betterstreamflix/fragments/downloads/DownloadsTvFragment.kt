package com.betterstreamflix.fragments.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betterstreamflix.compose.screens.DownloadsScreen
import com.betterstreamflix.download.DownloadFeature
import com.betterstreamflix.download.DownloadManager
import com.betterstreamflix.download.DownloadRepository
import com.betterstreamflix.download.DownloadStorageManager
import com.betterstreamflix.download.OfflinePlaybackHelper

class DownloadsTvFragment : Fragment() {

    private val repository by lazy { DownloadRepository(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        render(view as ComposeView)
    }

    private fun render(composeView: ComposeView) {
        composeView.setContent {
            val downloads by repository.observeTasks()
                .collectAsStateWithLifecycle(initialValue = emptyList())
            val storageMb = DownloadStorageManager.getDownloadSize(requireContext()) / (1024 * 1024)
            DownloadsScreen(
                downloads = downloads,
                storageUsedMb = storageMb,
                onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                onOpen = { task -> OfflinePlaybackHelper.playLocal(requireContext(), task) },
                onPause = { task -> DownloadManager.pauseDownload(requireContext(), task.id) },
                onResume = { task ->
                    DownloadManager.resumeDownload(requireContext(), task.id)
                    DownloadFeature.retry(requireContext(), task.id)
                },
                onCancel = { task -> DownloadManager.cancelDownload(requireContext(), task.id) },
            )
        }
    }
}
