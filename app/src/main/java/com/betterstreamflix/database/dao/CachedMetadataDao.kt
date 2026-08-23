package com.betterstreamflix.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CachedMetadataEntity)

    @Query("SELECT * FROM cached_metadata WHERE title = :title AND type = :type LIMIT 1")
    suspend fun get(title: String, type: String): CachedMetadataEntity?

    @Query("SELECT * FROM cached_metadata WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun getByTmdbId(tmdbId: Int): CachedMetadataEntity?

    @Query("DELETE FROM cached_metadata WHERE cachedAt < :before")
    suspend fun clearOlderThan(before: Long)

    @Query("DELETE FROM cached_metadata")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM cached_metadata")
    suspend fun count(): Int
}
