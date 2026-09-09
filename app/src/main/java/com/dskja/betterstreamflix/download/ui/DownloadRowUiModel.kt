package com.dskja.betterstreamflix.download.ui

import com.dskja.betterstreamflix.download.DownloadItemEntity
import com.dskja.betterstreamflix.download.DownloadItemState
import com.dskja.betterstreamflix.download.DownloadKind
import com.dskja.betterstreamflix.download.DownloadSeasonPackEntity
import com.dskja.betterstreamflix.download.DownloadStorage

sealed class DownloadRowUiModel {
    abstract val id: String

    data class Item(
        val entity: DownloadItemEntity,
    ) : DownloadRowUiModel() {
        override val id: String get() = entity.id
        val state: DownloadItemState get() = DownloadItemState.fromKey(entity.state)
        val isMovie: Boolean get() = entity.kind == DownloadKind.MOVIE.name
        val progressText: String
            get() {
                val used = DownloadStorage.formatBytes(entity.bytesDownloaded)
                val total = if (entity.contentLength > 0) {
                    DownloadStorage.formatBytes(entity.contentLength)
                } else {
                    "?"
                }
                val speed = if (entity.speedBytesPerSec > 0) {
                    DownloadStorage.formatBytes(entity.speedBytesPerSec)
                } else {
                    "—"
                }
                val eta = if (entity.etaSeconds > 0) {
                    val m = entity.etaSeconds / 60
                    if (m > 0) "${m}m" else "${entity.etaSeconds}s"
                } else {
                    "—"
                }
                return "${entity.progressPct}% · $used / $total · $speed/s · ~$eta"
            }
    }

    data class SeasonPack(
        val pack: DownloadSeasonPackEntity,
    ) : DownloadRowUiModel() {
        override val id: String get() = pack.id
        val state: DownloadItemState get() = DownloadItemState.fromKey(pack.state)
    }

    data class Header(val title: String) : DownloadRowUiModel() {
        override val id: String get() = "header_$title"
    }
}

enum class DownloadsFilter {
    ALL,
    DOWNLOADING,
    COMPLETED,
    FAILED,
}
