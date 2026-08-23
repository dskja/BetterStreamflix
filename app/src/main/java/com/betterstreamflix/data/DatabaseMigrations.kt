package com.betterstreamflix.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations — centralized migration definitions for
 * the Room database.
 */
object DatabaseMigrations {

    /**
     * Migration from version 1 to 2: Add favorites table.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS favorites (
                    videoId TEXT NOT NULL,
                    providerName TEXT NOT NULL,
                    title TEXT NOT NULL,
                    type TEXT NOT NULL,
                    posterUrl TEXT,
                    addedAt INTEGER NOT NULL,
                    PRIMARY KEY(videoId, providerName)
                )
            """)
        }
    }

    /**
     * Migration from version 2 to 3: Add downloads table.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS downloads (
                    id TEXT NOT NULL PRIMARY KEY,
                    videoId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    providerName TEXT NOT NULL,
                    filePath TEXT,
                    fileSize INTEGER NOT NULL DEFAULT 0,
                    downloadedBytes INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    createdAt INTEGER NOT NULL,
                    completedAt INTEGER
                )
            """)
        }
    }

    /**
     * Migration from version 3 to 4: Add cached_metadata table.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS cached_metadata (
                    title TEXT NOT NULL,
                    type TEXT NOT NULL,
                    tmdbId INTEGER,
                    overview TEXT,
                    posterPath TEXT,
                    backdropPath TEXT,
                    rating REAL,
                    releaseDate TEXT,
                    genres TEXT,
                    runtime INTEGER,
                    cachedAt INTEGER NOT NULL,
                    PRIMARY KEY(title, type)
                )
            """)
        }
    }

    /**
     * Migration from version 4 to 5: Add providers table.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS providers (
                    name TEXT NOT NULL PRIMARY KEY,
                    displayName TEXT NOT NULL,
                    baseUrl TEXT NOT NULL,
                    isEnabled INTEGER NOT NULL DEFAULT 1,
                    priority INTEGER NOT NULL DEFAULT 0,
                    lastUsed INTEGER
                )
            """)
        }
    }

    /**
     * Migration from version 5 to 6: Add index on watch_history watchedAt.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_watchedAt ON watch_history(watchedAt)")
        }
    }

    /**
     * Get all migrations.
     */
    fun getAll(): Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
    )
}
