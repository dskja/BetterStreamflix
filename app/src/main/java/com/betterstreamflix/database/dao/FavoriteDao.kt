package com.betterstreamflix.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE type = :type ORDER BY addedAt DESC")
    fun getByType(type: String): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoId = :videoId AND providerName = :providerName)")
    suspend fun isFavorite(videoId: String, providerName: String): Boolean

    @Query("DELETE FROM favorites WHERE videoId = :videoId AND providerName = :providerName")
    suspend fun delete(videoId: String, providerName: String)

    @Query("DELETE FROM favorites")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int
}
