package com.betterstreamflix.widgets

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        WidgetUpdateScheduler.updateAllWidgets(applicationContext)
        return Result.success()
    }
}
