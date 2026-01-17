package com.streamflixreborn.streamflix.database.dao

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.streamflixreborn.streamflix.models.TvShow
import kotlinx.coroutines.flow.Flow
import androidx.room.Transaction
import com.streamflixreborn.streamflix.utils.UserPreferences

@Dao
interface TvShowDao {

    @Query("SELECT * FROM tv_shows")
    fun getAllForBackup(): List<TvShow>

    @Query("SELECT * FROM tv_shows WHERE id = :id")
    fun getById(id: String): TvShow?

    @Query("SELECT * FROM tv_shows WHERE id = :id")
    fun getByIdAsFlow(id: String): Flow<TvShow?>

    @Query("SELECT * FROM tv_shows WHERE id IN (:ids)")
    fun getByIds(ids: List<String>): Flow<List<TvShow>>

    @Query("SELECT * FROM tv_shows WHERE isFavorite = 1")
    fun getFavorites(): Flow<List<TvShow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(tvShow: TvShow)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(tvShow: TvShow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tvShows: List<TvShow>)

    @Query("SELECT * FROM tv_shows")
    fun getAll(): Flow<List<TvShow>>

    @Query("SELECT * FROM tv_shows WHERE poster IS NULL or poster = ''")
    suspend fun getAllWithNullPoster(): List<TvShow>

    @Query("SELECT id FROM tv_shows")
    suspend fun getAllIds(): List<String>

    @Query("SELECT * FROM tv_shows WHERE LOWER(title) LIKE '%' || :query || '%' LIMIT :limit OFFSET :offset")
    suspend fun searchTvShows(query: String, limit: Int, offset: Int): List<TvShow>

    @Query("DELETE FROM tv_shows")
    fun deleteAll()

    @Transaction
    fun save(tvShow: TvShow) {
        val provider = UserPreferences.currentProvider?.name ?: "Unknown"
        val existing = getById(tvShow.id)
        if (existing != null) {
            val merged = existing.merge(tvShow)
            update(merged)
            Log.d("DatabaseVerify", "[$provider] REAL-TIME UPDATE TV Show: ${merged.title} (Fav: ${merged.isFavorite}, Watching: ${merged.isWatching})")
        } else {
            insert(tvShow)
            Log.d("DatabaseVerify", "[$provider] REAL-TIME INSERT TV Show: ${tvShow.title} (Fav: ${tvShow.isFavorite})")
        }
    }

    @Transaction
    fun setFavoriteWithLog(id: String, favorite: Boolean) {
        val provider = UserPreferences.currentProvider?.name ?: "Unknown"
        setFavorite(id, favorite)
        Log.d("DatabaseVerify", "[$provider] REAL-TIME Favorite Toggled: ID $id -> $favorite")
    }

    @Query("UPDATE tv_shows SET isFavorite = :favorite WHERE id = :id")
    fun setFavorite(id: String, favorite: Boolean)
}
