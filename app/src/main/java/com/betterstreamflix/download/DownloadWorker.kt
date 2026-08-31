package com.betterstreamflix.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = DownloadRepository(applicationContext)
        repository.migrateFromSharedPrefsIfNeeded()
        DownloadQueueProcessor(applicationContext).processQueue()
        return Result.success()
    }
}
