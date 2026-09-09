package com.dskja.betterstreamflix.download

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DownloadItemEntity::class,
        DownloadSeasonPackEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var instance: DownloadDatabase? = null

        fun get(context: Context): DownloadDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "downloads.db",
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
