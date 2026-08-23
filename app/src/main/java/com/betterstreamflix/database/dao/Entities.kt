package com.betterstreamflix.database.dao

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val title: String,
    val providerName: String,
    val thumbnailUrl: String?,
    val watchedAt: Long,
    val positionMs: Long,
    val durationMs: Long,
    val progressPercent: Float,
    val type: String,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val title: String,
    val providerName: String,
    val thumbnailUrl: String?,
    val type: String,
    val addedAt: Long,
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val videoId: String,
    val title: String,
    val providerName: String,
    val url: String,
    val filePath: String,
    val fileSize: Long,
    val downloadedBytes: Long,
    val status: String,
    val createdAt: Long,
    val completedAt: Long?,
    val errorMessage: String?,
)

@Entity(tableName = "cached_metadata")
data class CachedMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String,
    val tmdbId: Int?,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val genres: String,
    val year: String,
    val cachedAt: Long,
)

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val name: String,
    val displayName: String,
    val language: String,
    val isEnabled: Boolean,
    val lastUsed: Long,
    val domain: String,
)
