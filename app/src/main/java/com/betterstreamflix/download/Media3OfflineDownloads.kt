package com.betterstreamflix.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import com.betterstreamflix.utils.Constants
import java.io.File
import java.util.concurrent.Executors

@UnstableApi
object Media3OfflineDownloads {

    private const val CACHE_DIR = "media3_offline_cache"
    private const val THREAD_POOL_SIZE = 3

    @Volatile
    private var downloadManager: DownloadManager? = null

    @Volatile
    private var cache: SimpleCache? = null

    fun init(context: Context) {
        if (downloadManager != null) return
        synchronized(this) {
            if (downloadManager != null) return
            val appContext = context.applicationContext
            val databaseProvider = StandaloneDatabaseProvider(appContext)
            val cacheDir = File(appContext.filesDir, CACHE_DIR).apply { mkdirs() }
            val simpleCache = SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider)
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(Constants.USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
            val dataSourceFactory = DefaultDataSource.Factory(appContext, httpFactory)
            val manager = DownloadManager(
                appContext,
                databaseProvider,
                simpleCache,
                dataSourceFactory,
                Executors.newFixedThreadPool(THREAD_POOL_SIZE),
            ).apply {
                maxParallelDownloads = 2
            }
            cache = simpleCache
            downloadManager = manager
        }
    }

    fun requireManager(context: Context): DownloadManager {
        init(context)
        return downloadManager ?: error("Media3 DownloadManager not initialized")
    }

    fun cacheDir(context: Context): File {
        init(context)
        return File(context.applicationContext.filesDir, CACHE_DIR)
    }
}
