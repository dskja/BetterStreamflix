package com.betterstreamflix.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.betterstreamflix.database.dao.TvShowDao
import com.betterstreamflix.models.TvShow

@Database(entities = [TvShow::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class SerienStreamDatabase: RoomDatabase() {
    abstract fun tvShowDao(): TvShowDao

    companion object {
        @Volatile private var instance: SerienStreamDatabase? = null

        fun getInstance(context: Context): SerienStreamDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SerienStreamDatabase::class.java,
                    "serien_stream.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                .also { instance = it }
            }
        }
    }
}