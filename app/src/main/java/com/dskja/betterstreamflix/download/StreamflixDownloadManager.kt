package com.dskja.betterstreamflix.download

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object StreamflixDownloadManager {
    @Volatile
    private var downloadManager: DownloadManager? = null

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var dataSourceFactory: DownloadDataSourceFactory? = null

    @Volatile
    private var notificationHelper: DownloadNotificationHelper? = null

    @Volatile
    private var databaseProvider: StandaloneDatabaseProvider? = null

    @Volatile
    private var connectivityWatcherStarted = false

    private val executor: Executor = Executors.newFixedThreadPool(2)
    private val connectivityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityActionMutex = Mutex()

    fun get(context: Context): DownloadManager {
        downloadManager?.let { return it }
        synchronized(this) {
            downloadManager?.let { return it }
            val app = context.applicationContext
            DownloadConnectivityMonitor.start(app)
            DownloadNotifier.ensureChannel(app)

            val dbProvider = StandaloneDatabaseProvider(app).also { databaseProvider = it }
            val cache = SimpleCache(
                DownloadStorage.cacheDir(app),
                NoOpCacheEvictor(),
                dbProvider,
            ).also { simpleCache = it }

            val upstreamFactory = DownloadDataSourceFactory(app).also { dataSourceFactory = it }
            val cacheFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

            val manager = DownloadManager(
                app,
                dbProvider,
                cache,
                cacheFactory,
                executor,
            ).apply {
                maxParallelDownloads = UserPreferences.downloadMaxConcurrent.coerceIn(1, 4)
                addListener(DownloadEventBridge)
            }
            downloadManager = manager
            notificationHelper = DownloadNotificationHelper(app, DownloadNotifier.CHANNEL_ID)
            DownloadEventBridge.attach(app)
            startConnectivityWatcher(app)
            return manager
        }
    }

    private fun startConnectivityWatcher(app: Context) {
        if (connectivityWatcherStarted) return
        connectivityWatcherStarted = true
        connectivityScope.launch {
            DownloadConnectivityMonitor.status
                .map { it.type }
                .distinctUntilChanged()
                .collectLatest { type ->
                    // Debounce flaky network flips so we don't thrash pause/resume.
                    delay(750)
                    connectivityActionMutex.withLock {
                        val repo = DownloadRepository.get(app)
                        if (UserPreferences.downloadWifiOnly && type != DownloadNetworkType.WIFI) {
                            repo.pauseAll()
                        } else if (type != DownloadNetworkType.NONE) {
                            repo.resumeAll()
                        }
                    }
                }
        }
    }

    fun dataSourceFactory(context: Context): DownloadDataSourceFactory {
        get(context)
        return dataSourceFactory!!
    }

    fun cacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val manager = get(context)
        val cache = simpleCache ?: error("cache not ready")
        val upstream = dataSourceFactory ?: error("factory not ready")
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .also { manager /* keep ref */ }
    }

    fun notificationHelper(context: Context): DownloadNotificationHelper {
        get(context)
        return notificationHelper!!
    }

    fun setMaxParallel(context: Context, max: Int) {
        get(context).maxParallelDownloads = max.coerceIn(1, 4)
    }

    fun release() {
        synchronized(this) {
            downloadManager?.release()
            downloadManager = null
            simpleCache?.release()
            simpleCache = null
            dataSourceFactory = null
            notificationHelper = null
            databaseProvider = null
        }
    }
}
