package com.dskja.betterstreamflix.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles actions posted from download notifications (pause/resume all, retry a
 * failed item). Runs on the main thread via [goAsync]; work happens on IO.
 */
class DownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appContext = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                val repo = DownloadRepository.get(appContext)
                when (action) {
                    ACTION_PAUSE_ALL -> repo.pauseAll()
                    ACTION_RESUME_ALL -> repo.resumeAll()
                    ACTION_RETRY -> {
                        // resume() clears the Media3 stop reason and re-queues a
                        // failed download with its stored request — right fix for
                        // transient network/Wi-Fi failures. Items whose signed URL
                        // has expired still need the in-app retry (full re-resolve).
                        val itemId = intent.getStringExtra(EXTRA_ITEM_ID)
                        if (!itemId.isNullOrEmpty()) {
                            repo.resume(itemId)
                        }
                    }
                }
                StreamflixDownloadService.start(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val PREFIX = "com.dskja.betterstreamflix.download."
        const val ACTION_PAUSE_ALL = PREFIX + "PAUSE_ALL"
        const val ACTION_RESUME_ALL = PREFIX + "RESUME_ALL"
        const val ACTION_RETRY = PREFIX + "RETRY"
        const val EXTRA_ITEM_ID = "item_id"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
