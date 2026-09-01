package com.betterstreamflix.download

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import com.betterstreamflix.StreamFlixApp
import com.betterstreamflix.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

@UnstableApi
object Media3OfflineDownloads {

    private const val TAG = "Media3OfflineDownloads"
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
            runCatching { initializeLocked(context.applicationContext) }
                .onFailure { error ->
                    Log.e(TAG, "Offline download subsystem disabled after init failure", error)
                    releaseLocked()
                }
        }
    }

    fun managerOrNull(context: Context): DownloadManager? {
        init(context)
        val manager = downloadManager
        if (manager != null) {
            Media3DownloadSync.ensureAttached(context)
        }
        return manager
    }

    fun requireManager(context: Context): DownloadManager {
        init(context)
        val manager = downloadManager ?: error("Media3 DownloadManager not initialized")
        Media3DownloadSync.ensureAttached(context)
        return manager
    }

    fun cacheOrNull(context: Context): SimpleCache? {
        init(context)
        return cache
    }

    internal fun downloadManagerOrNull(): DownloadManager? = downloadManager

    fun cacheDir(context: Context): File {
        init(context)
        return File(context.applicationContext.filesDir, CACHE_DIR)
    }

    private fun initializeLocked(appContext: Context) {
        val databaseProvider = StandaloneDatabaseProvider(appContext)
        val cacheDir = File(appContext.filesDir, CACHE_DIR)
        val simpleCache = openSimpleCache(cacheDir, databaseProvider)
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
        // Defer listener attachment so init never blocks on Room/notification work.
        StreamFlixApp.instance.applicationScope.launch(Dispatchers.IO) {
            Media3DownloadSync.ensureAttached(appContext)
        }
    }

    /**
     * Media3 keeps an exclusive lock on the cache directory. If another live instance
     * already owns the folder we must not delete it — that would wipe offline media.
     */
    private fun openSimpleCache(
        cacheDir: File,
        databaseProvider: StandaloneDatabaseProvider,
    ): SimpleCache {
        cacheDir.mkdirs()
        return try {
            SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider)
        } catch (error: IllegalStateException) {
            Log.w(TAG, "SimpleCache unavailable (another instance may own the folder)", error)
            throw error
        }
    }

    private fun releaseLocked() {
        runCatching { cache?.release() }
        cache = null
        downloadManager = null
    }
}
