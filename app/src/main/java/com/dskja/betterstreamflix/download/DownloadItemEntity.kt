package com.dskja.betterstreamflix.download

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "download_items",
    indices = [
        Index(value = ["contentKey"], unique = true),
        Index(value = ["media3Id"]),
        Index(value = ["state"]),
        Index(value = ["seasonPackId"]),
    ],
)
data class DownloadItemEntity(
    @PrimaryKey val id: String,
    val contentKey: String,
    val media3Id: String,
    val providerName: String,
    val kind: String,
    val title: String,
    val subtitle: String = "",
    val posterUrl: String = "",
    val videoTypeJson: String = "",
    val serverName: String = "",
    val serverId: String = "",
    val qualityLabel: String = "",
    val mimeType: String = "",
    val state: String = DownloadItemState.QUEUED.name,
    val bytesDownloaded: Long = 0L,
    val contentLength: Long = 0L,
    val progressPct: Int = 0,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = -1L,
    val errorCode: String = "",
    val errorMessage: String = "",
    val localUri: String = "",
    val streamUrl: String = "",
    val headersJson: String = "",
    val subtitlePathsJson: String = "[]",
    val seasonPackId: String? = null,
    val sortIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val watchedOffline: Boolean = false,
)

@Entity(tableName = "download_season_packs")
data class DownloadSeasonPackEntity(
    @PrimaryKey val id: String,
    val providerName: String,
    val tvShowId: String,
    val tvShowTitle: String,
    val seasonNumber: Int,
    val posterUrl: String = "",
    val totalEpisodes: Int = 0,
    val completedEpisodes: Int = 0,
    val failedEpisodes: Int = 0,
    val state: String = DownloadItemState.QUEUED.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
