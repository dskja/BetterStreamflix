package com.betterstreamflix.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.betterstreamflix.database.dao.WatchHistoryDao
import com.betterstreamflix.database.dao.WatchHistoryEntity
import com.betterstreamflix.database.dao.FavoriteDao
import com.betterstreamflix.database.dao.FavoriteEntity
import com.betterstreamflix.database.dao.DownloadDao
import com.betterstreamflix.database.dao.DownloadEntity
import com.betterstreamflix.database.dao.CachedMetadataDao
import com.betterstreamflix.database.dao.CachedMetadataEntity
import com.betterstreamflix.database.dao.ProviderDao
import com.betterstreamflix.database.dao.ProviderEntity

/**
 * App-level Room database for cross-provider data:
 * watch history, favorites, downloads, cached metadata, and provider config.
 * This is separate from the provider-specific AppDatabase.
 */
@Database(
    entities = [
        WatchHistoryEntity::class,
        FavoriteEntity::class,
        DownloadEntity::class,
        CachedMetadataEntity::class,
        ProviderEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppLevelDatabase : RoomDatabase() {

    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun downloadDao(): DownloadDao
    abstract fun metadataDao(): CachedMetadataDao
    abstract fun providerDao(): ProviderDao

    companion object {
        @Volatile
        private var INSTANCE: AppLevelDatabase? = null

        const val DB_NAME = "betterstreamflix_app.db"

        fun getInstance(context: Context): AppLevelDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppLevelDatabase::class.java,
                    DB_NAME,
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
