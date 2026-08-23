package com.betterstreamflix.data

/**
 * Entity converter — converts between Room entities and domain models.
 */
object EntityConverter {

    /**
     * Convert a search history entity to a domain model.
     */
    fun toSearchHistoryModel(entity: SearchHistoryEntity): SearchHistoryModel {
        return SearchHistoryModel(
            query = entity.query,
            resultCount = entity.resultCount,
            searchedAt = entity.searchedAt,
        )
    }

    /**
     * Convert a playback position entity to a domain model.
     */
    fun toPlaybackPositionModel(entity: PlaybackPositionEntity): PlaybackPositionModel {
        return PlaybackPositionModel(
            videoId = entity.videoId,
            providerName = entity.providerName,
            title = entity.title,
            positionMs = entity.positionMs,
            durationMs = entity.durationMs,
            playbackSpeed = entity.playbackSpeed,
            lastWatchedAt = entity.lastWatchedAt,
            isCompleted = entity.isCompleted,
        )
    }

    /**
     * Convert a playback position model to an entity.
     */
    fun toPlaybackPositionEntity(model: PlaybackPositionModel): PlaybackPositionEntity {
        return PlaybackPositionEntity(
            videoId = model.videoId,
            providerName = model.providerName,
            title = model.title,
            positionMs = model.positionMs,
            durationMs = model.durationMs,
            playbackSpeed = model.playbackSpeed,
            lastWatchedAt = model.lastWatchedAt,
            isCompleted = model.isCompleted,
        )
    }

    data class SearchHistoryModel(
        val query: String,
        val resultCount: Int,
        val searchedAt: Long,
    )

    data class PlaybackPositionModel(
        val videoId: String,
        val providerName: String,
        val title: String,
        val positionMs: Long,
        val durationMs: Long,
        val playbackSpeed: Float,
        val lastWatchedAt: Long,
        val isCompleted: Boolean,
    )
}
