package com.dskja.betterstreamflix.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_items ORDER BY sortIndex ASC, updatedAt DESC")
    fun observeAll(): Flow<List<DownloadItemEntity>>

    @Query("SELECT * FROM download_items WHERE state IN (:states) ORDER BY sortIndex ASC, updatedAt DESC")
    fun observeByStates(states: List<String>): Flow<List<DownloadItemEntity>>

    @Query("SELECT * FROM download_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadItemEntity?

    @Query("SELECT * FROM download_items WHERE contentKey = :contentKey LIMIT 1")
    suspend fun getByContentKey(contentKey: String): DownloadItemEntity?

    @Query("SELECT * FROM download_items WHERE media3Id = :media3Id LIMIT 1")
    suspend fun getByMedia3Id(media3Id: String): DownloadItemEntity?

    @Query("SELECT contentKey FROM download_items WHERE state = :state")
    fun observeCompletedKeys(state: String = DownloadItemState.COMPLETED.name): Flow<List<String>>

    @Query("SELECT contentKey FROM download_items WHERE state = :state")
    suspend fun completedKeys(state: String = DownloadItemState.COMPLETED.name): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadItemEntity)

    @Update
    suspend fun update(item: DownloadItemEntity)

    @Query("DELETE FROM download_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM download_items WHERE state = :state")
    suspend fun deleteByState(state: String)

    @Query("DELETE FROM download_items")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM download_items WHERE state IN (:states)")
    suspend fun countByStates(states: List<String>): Int

    @Query("SELECT * FROM download_season_packs ORDER BY updatedAt DESC")
    fun observeSeasonPacks(): Flow<List<DownloadSeasonPackEntity>>

    @Query("SELECT * FROM download_season_packs WHERE id = :id LIMIT 1")
    suspend fun getSeasonPack(id: String): DownloadSeasonPackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeasonPack(pack: DownloadSeasonPackEntity)

    @Query("DELETE FROM download_season_packs WHERE id = :id")
    suspend fun deleteSeasonPack(id: String)

    @Query("SELECT * FROM download_items WHERE seasonPackId = :packId ORDER BY sortIndex ASC")
    suspend fun itemsForPack(packId: String): List<DownloadItemEntity>
}
