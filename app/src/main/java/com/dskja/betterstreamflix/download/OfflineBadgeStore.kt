package com.dskja.betterstreamflix.download

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

object OfflineBadgeStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var keysFlow: StateFlow<Set<String>>? = null

    fun completedKeys(context: Context): StateFlow<Set<String>> {
        keysFlow?.let { return it }
        synchronized(this) {
            keysFlow?.let { return it }
            return DownloadRepository.get(context)
                .observeCompletedKeys()
                .stateIn(scope, SharingStarted.Eagerly, emptySet())
                .also { keysFlow = it }
        }
    }

    fun isCompleted(context: Context, contentKey: String): Boolean =
        completedKeys(context).value.contains(contentKey)
}
