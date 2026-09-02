package com.betterstreamflix.fragments.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betterstreamflix.compose.screens.DownloadsScreen
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
import com.betterstreamflix.download.DownloadFeature
import com.betterstreamflix.download.DownloadLiveStats
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
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        render(view as ComposeView)
    }

    private fun render(composeView: ComposeView) {
        composeView.setContent {
            BetterStreamflixTheme {
                val downloads by repository.observeTasks()
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val liveSpeeds by DownloadLiveStats.speeds.collectAsStateWithLifecycle(initialValue = emptyMap())
                val storageUsed = remember(downloads) {
                    DownloadStorageManager.getDownloadSize(requireContext())
                }
                val storageFree = remember {
                    DownloadStorageManager.getAvailableSpace(requireContext())
                }
                DownloadsScreen(
                    downloads = downloads,
                    storageUsedBytes = storageUsed,
                    storageFreeBytes = storageFree,
                    liveSpeeds = liveSpeeds,
                    onOpen = { task -> OfflinePlaybackHelper.playLocal(requireContext(), task) },
                    onPause = { task -> DownloadManager.pauseDownload(requireContext(), task.id) },
                    onResume = { task -> DownloadFeature.retry(requireContext(), task.id) },
                    onCancel = { task -> DownloadManager.cancelDownload(requireContext(), task.id) },
                    onClearCompleted = { DownloadFeature.clearCompleted(requireContext()) },
                    onPauseAll = { DownloadFeature.pauseAllActive(requireContext()) },
                    onResumeAll = { DownloadFeature.resumeAllPaused(requireContext()) },
                    onRetryFailed = { DownloadFeature.retryAllFailed(requireContext()) },
                    onClearFailed = { DownloadFeature.clearFailed(requireContext()) },
                )
            }
        }
    }
}
