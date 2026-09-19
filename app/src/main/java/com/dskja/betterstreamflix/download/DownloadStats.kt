package com.dskja.betterstreamflix.download

import android.content.Context
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.utils.UserPreferences

/**
 * Snapshot helpers for Downloads tab + Settings summaries.
 */
data class DownloadQueueStats(
    val active: Int,
    val completed: Int,
    val failed: Int,
    val watched: Int,
    val total: Int,
) {
    val hasWork: Boolean get() = active > 0 || failed > 0
}

object DownloadStats {

    fun fromItems(items: List<DownloadItemEntity>): DownloadQueueStats {
        var active = 0
        var completed = 0
        var failed = 0
        var watched = 0
        items.forEach { item ->
            when (DownloadItemState.fromKey(item.state)) {
                DownloadItemState.QUEUED,
                DownloadItemState.PREPARING,
                DownloadItemState.DOWNLOADING,
                DownloadItemState.PAUSED,
                -> active++
                DownloadItemState.COMPLETED -> {
                    completed++
                    if (item.watchedOffline) watched++
                }
                DownloadItemState.FAILED -> failed++
                DownloadItemState.REMOVING -> Unit
            }
        }
        return DownloadQueueStats(
            active = active,
            completed = completed,
            failed = failed,
            watched = watched,
            total = items.size,
        )
    }

    fun queueSummary(context: Context, stats: DownloadQueueStats): String {
        if (stats.total == 0) {
            return context.getString(R.string.settings_download_queue_empty)
        }
        return context.getString(
            R.string.settings_download_queue_summary,
            stats.active,
            stats.completed,
            stats.failed,
        )
    }

    fun storageSummary(context: Context): String {
        val used = DownloadStorage.formatBytes(DownloadStorage.usedBytes(context))
        val free = DownloadStorage.formatBytes(DownloadStorage.freeBytes(context))
        val soft = UserPreferences.downloadSoftLimitGb
        return if (soft > 0) {
            context.getString(
                R.string.settings_download_storage_detail,
                used,
                free,
                soft,
            )
        } else {
            context.getString(R.string.downloads_storage_chip, used, free)
        }
    }

    fun clampSoftLimitGb(raw: String?): Int {
        val value = raw?.trim()?.toIntOrNull() ?: return UserPreferences.downloadSoftLimitGb
        return value.coerceIn(0, 500)
    }

    fun hubDownloadsSummary(context: Context, items: List<DownloadItemEntity>): String {
        val stats = fromItems(items)
        return if (stats.total == 0) {
            context.getString(R.string.settings_screen_downloads_summary)
        } else {
            queueSummary(context, stats)
        }
    }
}
