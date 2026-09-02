package com.betterstreamflix.fragments.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.DownloadsScreen
import com.betterstreamflix.download.DownloadFeature
import com.betterstreamflix.download.DownloadLiveStats
import com.betterstreamflix.download.DownloadManager
import com.betterstreamflix.download.DownloadRepository
import com.betterstreamflix.download.DownloadStorageManager
import com.betterstreamflix.download.OfflinePlaybackHelper

class DownloadsMobileFragment : ComposeHostFragment() {

    private val repository by lazy { DownloadRepository(requireContext()) }

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DownloadFeature.ensureNotificationPermission(requireContext())
    }

    @Composable
    override fun ScreenContent() {
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
