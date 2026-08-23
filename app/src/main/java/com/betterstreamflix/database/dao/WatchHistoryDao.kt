package com.betterstreamflix.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun getAll(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE progressPercent > 0.01 AND progressPercent < 0.9 ORDER BY watchedAt DESC")
    fun getContinueWatching(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE videoId = :videoId AND providerName = :providerName LIMIT 1")
    suspend fun get(videoId: String, providerName: String): WatchHistoryEntity?

    @Query("DELETE FROM watch_history WHERE videoId = :videoId AND providerName = :providerName")
    suspend fun delete(videoId: String, providerName: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()

    @Query("DELETE FROM watch_history WHERE watchedAt < :before")
    suspend fun clearOlderThan(before: Long)
}
