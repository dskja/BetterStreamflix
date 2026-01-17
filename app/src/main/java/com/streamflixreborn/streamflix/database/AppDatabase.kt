package com.streamflixreborn.streamflix.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.streamflixreborn.streamflix.database.dao.EpisodeDao
import com.streamflixreborn.streamflix.database.dao.MovieDao
import com.streamflixreborn.streamflix.database.dao.SeasonDao
import com.streamflixreborn.streamflix.database.dao.TvShowDao
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.utils.UserPreferences

@Database(
    entities = [
        Episode::class,
        Movie::class,
        Season::class,
        TvShow::class,
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movieDao(): MovieDao

    abstract fun tvShowDao(): TvShowDao

    abstract fun seasonDao(): SeasonDao

    abstract fun episodeDao(): EpisodeDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun sanitizeProviderName(name: String): String {
            // Rimuove caratteri non validi per i nomi dei file DB, 
            // come spazi, parentesi, e li converte in lowercase.
            return name.lowercase()
                .replace("[^a-z0-9]".toRegex(), "_")
                .replace("__+".toRegex(), "_") // Sostituisce doppie underscore con una singola
                .trim('_') // Rimuove underscore iniziale/finale
        }

        fun setup(context: Context) {
            if (UserPreferences.currentProvider == null) return

            synchronized(this) {
                INSTANCE?.close() // Chiudi connessioni esistenti
                INSTANCE = buildDatabase(UserPreferences.currentProvider!!.name, context)
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(UserPreferences.currentProvider!!.name, context)
                INSTANCE = instance
                instance
            }
        }

        // Metodo per forzare il cambio di database quando cambia il provider
        fun resetInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        fun getInstanceForProvider(providerName: String, context: Context): AppDatabase {
            return buildDatabase(providerName, context)
        }

        private fun buildDatabase(providerName: String, context: Context): AppDatabase {
            val sanitizedName = sanitizeProviderName(providerName)
            return Room.databaseBuilder(
                context = context.applicationContext,
                klass = AppDatabase::class.java,
                name = "$sanitizedName.db"
            )
                .allowMainThreadQueries()
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .build()
        }


        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN watchedDate TEXT")
                db.execSQL("ALTER TABLE episodes ADD COLUMN lastEngagementTimeUtcMillis INTEGER")
                db.execSQL("ALTER TABLE episodes ADD COLUMN lastPlaybackPositionMillis INTEGER")
                db.execSQL("ALTER TABLE episodes ADD COLUMN durationMillis INTEGER")

                db.execSQL("ALTER TABLE movies ADD COLUMN watchedDate TEXT")
                db.execSQL("ALTER TABLE movies ADD COLUMN lastEngagementTimeUtcMillis INTEGER")
                db.execSQL("ALTER TABLE movies ADD COLUMN lastPlaybackPositionMillis INTEGER")
                db.execSQL("ALTER TABLE movies ADD COLUMN durationMillis INTEGER")
            }
        }

        private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE `episodes_temp` (`id` TEXT NOT NULL, `number` INTEGER NOT NULL, `title` TEXT, `poster` TEXT, `tvShow` TEXT, `season` TEXT, `released` TEXT, `isWatched` INTEGER NOT NULL, `watchedDate` TEXT, `lastEngagementTimeUtcMillis` INTEGER, `lastPlaybackPositionMillis` INTEGER, `durationMillis` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO episodes_temp SELECT * FROM episodes")
                db.execSQL("DROP TABLE episodes")
                db.execSQL("ALTER TABLE episodes_temp RENAME TO episodes")

                db.execSQL("CREATE TABLE `movies_temp` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `overview` TEXT, `runtime` INTEGER, `trailer` TEXT, `quality` TEXT, `rating` REAL, `poster` TEXT, `banner` TEXT, `released` TEXT, `isFavorite` INTEGER NOT NULL, `isWatched` INTEGER NOT NULL, `watchedDate` TEXT, `lastEngagementTimeUtcMillis` INTEGER, `lastPlaybackPositionMillis` INTEGER, `durationMillis` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO movies_temp SELECT * FROM movies")
                db.execSQL("DROP TABLE movies")
                db.execSQL("ALTER TABLE movies_temp RENAME TO movies")

                db.execSQL("CREATE TABLE `seasons_temp` (`id` TEXT NOT NULL, `number` INTEGER NOT NULL, `title` TEXT, `poster` TEXT, `tvShow` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO seasons_temp SELECT * FROM seasons")
                db.execSQL("DROP TABLE seasons")
                db.execSQL("ALTER TABLE seasons_temp RENAME TO seasons")

                db.execSQL("CREATE TABLE `tv_shows_temp` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `overview` TEXT, `runtime` INTEGER, `trailer` TEXT, `quality` TEXT, `rating` REAL, `poster` TEXT, `banner` TEXT, `released` TEXT, `isFavorite` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO tv_shows_temp SELECT * FROM tv_shows")
                db.execSQL("DROP TABLE tv_shows")
                db.execSQL("ALTER TABLE tv_shows_temp RENAME TO tv_shows")
            }
        }

        private val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tv_shows ADD COLUMN isWatching INTEGER DEFAULT 1 NOT NULL")
            }
        }

        private val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN overview TEXT")
            }
        }
    }
}
