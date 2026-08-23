package com.betterstreamflix.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migration helpers.
 */
object DatabaseMigrations {

    /**
     * Migration from version 1 to 2: Add watch_history and favorites tables.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS watch_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    videoId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    providerName TEXT NOT NULL,
                    thumbnailUrl TEXT,
                    watchedAt INTEGER NOT NULL,
                    positionMs INTEGER NOT NULL,
                    durationMs INTEGER NOT NULL,
                    progressPercent REAL NOT NULL,
                    type TEXT NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS favorites (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    videoId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    providerName TEXT NOT NULL,
                    thumbnailUrl TEXT,
                    type TEXT NOT NULL,
                    addedAt INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    /**
     * Migration from version 2 to 3: Add downloads and cached_metadata tables.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS downloads (
                    id TEXT PRIMARY KEY NOT NULL,
                    videoId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    providerName TEXT NOT NULL,
                    url TEXT NOT NULL,
                    filePath TEXT NOT NULL,
                    fileSize INTEGER NOT NULL,
                    downloadedBytes INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    completedAt INTEGER,
                    errorMessage TEXT
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS cached_metadata (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    type TEXT NOT NULL,
                    tmdbId INTEGER,
                    overview TEXT NOT NULL,
                    posterPath TEXT,
                    backdropPath TEXT,
                    voteAverage REAL NOT NULL,
                    genres TEXT NOT NULL,
                    year TEXT NOT NULL,
                    cachedAt INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    /**
     * All migrations in order.
     */
    val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
