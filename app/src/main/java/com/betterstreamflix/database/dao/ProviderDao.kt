package com.betterstreamflix.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProviderEntity)

    @Query("SELECT * FROM providers WHERE isEnabled = 1 ORDER BY displayName")
    fun getEnabledProviders(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY displayName")
    fun getAllProviders(): Flow<List<ProviderEntity>>

    @Query("UPDATE providers SET isEnabled = :enabled WHERE name = :name")
    suspend fun setEnabled(name: String, enabled: Boolean)

    @Query("UPDATE providers SET lastUsed = :timestamp WHERE name = :name")
    suspend fun updateLastUsed(name: String, timestamp: Long)

    @Query("UPDATE providers SET domain = :domain WHERE name = :name")
    suspend fun updateDomain(name: String, domain: String)

    @Query("SELECT * FROM providers WHERE name = :name LIMIT 1")
    suspend fun get(name: String): ProviderEntity?

    @Query("DELETE FROM providers WHERE name = :name")
    suspend fun delete(name: String)
}
