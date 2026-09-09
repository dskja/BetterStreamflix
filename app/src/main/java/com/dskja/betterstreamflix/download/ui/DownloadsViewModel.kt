package com.dskja.betterstreamflix.download.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dskja.betterstreamflix.download.DownloadConnectivityMonitor
import com.dskja.betterstreamflix.download.DownloadController
import com.dskja.betterstreamflix.download.DownloadItemState
import com.dskja.betterstreamflix.download.DownloadRepository
import com.dskja.betterstreamflix.download.DownloadStorage
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val appContext: Context,
) : ViewModel() {
    private val repo = DownloadRepository.get(appContext)
    private val filter = MutableStateFlow(DownloadsFilter.ALL)

    val storageLabel: StateFlow<String> = MutableStateFlow(
        appContext.getString(
            com.dskja.betterstreamflix.R.string.downloads_storage_chip,
            DownloadStorage.formatBytes(DownloadStorage.usedBytes(appContext)),
            DownloadStorage.formatBytes(DownloadStorage.freeBytes(appContext)),
        ),
    )

    val lowSpace: StateFlow<Boolean> = MutableStateFlow(DownloadStorage.isLowSpace(appContext))

    val wifiPaused: StateFlow<Boolean> = combine(
        DownloadConnectivityMonitor.status,
        MutableStateFlow(UserPreferences.downloadWifiOnly),
    ) { status, wifiOnly ->
        wifiOnly && status.type == com.dskja.betterstreamflix.download.DownloadNetworkType.CELLULAR
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val rows: StateFlow<List<DownloadRowUiModel>> = combine(
        repo.observeAll(),
        repo.observeSeasonPacks(),
        filter,
    ) { items, packs, selectedFilter ->
        val providerFilter = UserPreferences.downloadFilterCurrentProvider
        val providerName = UserPreferences.currentProvider?.name
        var filtered = items
        if (providerFilter && !providerName.isNullOrBlank()) {
            filtered = filtered.filter { it.providerName == providerName }
        }
        filtered = when (selectedFilter) {
            DownloadsFilter.ALL -> filtered
            DownloadsFilter.DOWNLOADING -> filtered.filter {
                DownloadItemState.fromKey(it.state).isActive
            }
            DownloadsFilter.COMPLETED -> filtered.filter {
                it.state == DownloadItemState.COMPLETED.name
            }
            DownloadsFilter.FAILED -> filtered.filter {
                it.state == DownloadItemState.FAILED.name
            }
        }
        val active = filtered.filter { DownloadItemState.fromKey(it.state).isActive }
        val completed = filtered.filter { it.state == DownloadItemState.COMPLETED.name }
        val failed = filtered.filter { it.state == DownloadItemState.FAILED.name }
        buildList {
            val relevantPacks = packs.filter { pack ->
                filtered.any { it.seasonPackId == pack.id } ||
                    selectedFilter == DownloadsFilter.ALL
            }
            if (active.isNotEmpty()) {
                add(DownloadRowUiModel.Header("Active"))
                active.forEach { add(DownloadRowUiModel.Item(it)) }
            }
            if (relevantPacks.isNotEmpty() && selectedFilter != DownloadsFilter.FAILED) {
                add(DownloadRowUiModel.Header("Seasons"))
                relevantPacks.forEach { add(DownloadRowUiModel.SeasonPack(it)) }
            }
            if (completed.isNotEmpty()) {
                add(DownloadRowUiModel.Header("Completed"))
                completed.forEach { add(DownloadRowUiModel.Item(it)) }
            }
            if (failed.isNotEmpty()) {
                add(DownloadRowUiModel.Header("Failed"))
                failed.forEach { add(DownloadRowUiModel.Item(it)) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(value: DownloadsFilter) {
        filter.value = value
    }

    fun currentFilter(): DownloadsFilter = filter.value

    fun refreshStorage() {
        (storageLabel as MutableStateFlow).value = appContext.getString(
            com.dskja.betterstreamflix.R.string.downloads_storage_chip,
            DownloadStorage.formatBytes(DownloadStorage.usedBytes(appContext)),
            DownloadStorage.formatBytes(DownloadStorage.freeBytes(appContext)),
        )
        (lowSpace as MutableStateFlow).value = DownloadStorage.isLowSpace(appContext)
    }

    fun pause(id: String) = viewModelScope.launch { repo.pause(id) }
    fun resume(id: String) = viewModelScope.launch { repo.resume(id) }
    fun remove(id: String) = viewModelScope.launch {
        repo.remove(id)
        refreshStorage()
    }
    fun pauseAll() = viewModelScope.launch { repo.pauseAll() }
    fun resumeAll() = viewModelScope.launch { repo.resumeAll() }
    fun clearCompleted() = viewModelScope.launch {
        repo.clearCompleted()
        refreshStorage()
    }
    fun clearFailed() = viewModelScope.launch {
        repo.clearFailed()
        refreshStorage()
    }

    fun retry(id: String) = viewModelScope.launch {
        val item = repo.getById(id) ?: return@launch
        val videoType = DownloadController.deserializeVideoType(item.videoTypeJson) ?: return@launch
        // Remove failed entry then re-prepare via UI layer; here just resume if paused, else remove+signal
        repo.remove(id)
        // Caller should re-open options; for failed retry we resume Media3 if request still exists
    }
}
