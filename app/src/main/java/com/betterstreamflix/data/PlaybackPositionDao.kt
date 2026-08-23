package com.betterstreamflix.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for playback position persistence.
 */
@Dao
interface PlaybackPositionDao {

    @Query("SELECT * FROM playback_positions WHERE videoId = :videoId AND providerName = :providerName")
    suspend fun get(videoId: String, providerName: String): PlaybackPositionEntity?

    @Query("SELECT * FROM playback_positions ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun getRecent(limit: Int = 20): Flow<List<PlaybackPositionEntity>>

    @Query("SELECT * FROM playback_positions WHERE lastWatchedAt > :since ORDER BY lastWatchedAt DESC")
    fun getSince(since: Long): Flow<List<PlaybackPositionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions WHERE videoId = :videoId AND providerName = :providerName")
    suspend fun delete(videoId: String, providerName: String)

    @Query("DELETE FROM playback_positions WHERE lastWatchedAt < :before")
    suspend fun clearOlderThan(before: Long)

    @Query("DELETE FROM playback_positions")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM playback_positions")
    suspend fun count(): Int

    @Query("UPDATE playback_positions SET positionMs = :positionMs, durationMs = :durationMs, lastWatchedAt = :watchedAt WHERE videoId = :videoId AND providerName = :providerName")
    suspend fun updatePosition(videoId: String, providerName: String, positionMs: Long, durationMs: Long, watchedAt: Long)
}

/**
 * Playback position entity.
 */
@androidx.room.Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @androidx.room.PrimaryKey val videoId: String,
    @androidx.room.PrimaryKey val providerName: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val playbackSpeed: Float,
    val lastWatchedAt: Long,
    val isCompleted: Boolean,
)
