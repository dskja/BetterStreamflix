package com.betterstreamflix.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for search history persistence in Room database.
 */
@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun getRecentSearches(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history WHERE query LIKE '%' || :partial || '%' ORDER BY searchedAt DESC LIMIT :limit")
    fun searchHistory(partial: String, limit: Int = 10): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteByQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()

    @Query("DELETE FROM search_history WHERE searchedAt < :before")
    suspend fun clearOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun count(): Int
}

/**
 * Search history entity.
 */
@androidx.room.Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val resultCount: Int,
    val searchedAt: Long,
)
