package com.streamflixreborn.streamflix.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.database.SerienStreamDatabase

class FixSerienStreamUrlsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {

        val db = SerienStreamDatabase.getInstance(applicationContext)
        val sql = db.openHelper.writableDatabase

        try {

            sql.execSQL("""
UPDATE tv_shows
SET 
    poster = CASE
        WHEN poster LIKE '%s.to//%' THEN REPLACE(poster,'s.to//','s.to/')
        WHEN poster LIKE '%serienstream.to//%' THEN REPLACE(poster,'s.to//','s.to/')
        WHEN poster NOT LIKE '%https%' AND poster IS NOT NULL AND poster != '' 
            THEN 'https://s.to/' || LTRIM(poster, '/')
        ELSE poster
    END,
    banner = CASE
        WHEN banner LIKE '%s.to//%' THEN REPLACE(banner,'s.to//','s.to/')
        WHEN banner LIKE '%serienstream.to//%' THEN REPLACE(banner,'s.to//','s.to/')
        WHEN banner NOT LIKE '%https%' AND banner IS NOT NULL AND banner != '' 
            THEN 'https://s.to/' || LTRIM(banner, '/')
        ELSE banner
    END
""")

            return Result.success()

        } catch (e: Exception) {
            return Result.retry()
        }
    }
}