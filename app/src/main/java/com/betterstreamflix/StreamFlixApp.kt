package com.betterstreamflix

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import java.security.Security
import org.conscrypt.Conscrypt
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.providers.AniWorldProvider
import com.betterstreamflix.providers.SerienStreamProvider
import com.betterstreamflix.sync.CloudSyncManager
import com.betterstreamflix.sync.SupabaseProvider
import com.betterstreamflix.utils.AppLanguageManager
import com.betterstreamflix.utils.ArtworkRepairScheduler
import com.betterstreamflix.utils.CacheUtils
import com.betterstreamflix.utils.DnsResolver
import com.betterstreamflix.utils.FileLogger
import com.betterstreamflix.utils.GlobalErrorHandler
import com.betterstreamflix.utils.IsrgRootTrustProvider
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.download.DownloadRepository
import com.betterstreamflix.download.Media3OfflineDownloads
import com.betterstreamflix.notifications.NotificationChannelManager
import com.betterstreamflix.work.NewEpisodeCheckWorker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StreamFlixApp : Application() {
    companion object {
        lateinit var instance: StreamFlixApp
            private set

        @Volatile
        var currentActivity: Activity? = null
            private set
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + GlobalErrorHandler.handler)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 0. Initialize file logger FIRST so everything is captured
        FileLogger.init(this)
        FileLogger.installCrashHandler()
        FileLogger.logLifecycle("StreamFlixApp.onCreate", "PID=${android.os.Process.myPid()}")
        FileLogger.i("Init", "Application class created. BuildConfig.APP_LAYOUT=${BuildConfig.APP_LAYOUT}")

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivity === activity) {
                    currentActivity = null
                }
            }

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity === activity) {
                    currentActivity = null
                }
            }
        })

        // 0. Initialize Conscrypt for modern SSL on old Android
        FileLogger.i("Init", "Step 0: Conscrypt SSL provider")
        runCatching { Security.insertProviderAt(Conscrypt.newProvider(), 1) }
            .onSuccess { FileLogger.i("Init", "✓ Conscrypt installed") }
            .onFailure { FileLogger.e("Init", "✗ Conscrypt failed", it) }

        // 1. Install ISRG Root X1 globally for Let's Encrypt.
        FileLogger.i("Init", "Step 1: ISRG Root X1")
        runCatching { IsrgRootTrustProvider.install() }
            .onSuccess { FileLogger.i("Init", "✓ ISRG Root X1 installed") }
            .onFailure { FileLogger.e("Init", "✗ ISRG Root X1 failed", it) }

        // 2. Initialize preferences
        FileLogger.i("Init", "Step 2: UserPreferences + DnsResolver")
        UserPreferences.setup(this)
        FileLogger.i("Init", "✓ UserPreferences setup. currentProvider=${UserPreferences.currentProvider}")
        DnsResolver.setDnsUrl(UserPreferences.dohProviderUrl)
        FileLogger.i("Init", "✓ DnsResolver set to ${UserPreferences.dohProviderUrl}")

        NotificationChannelManager.createChannels(this)
        NewEpisodeCheckWorker.schedule(this)
        Media3OfflineDownloads.init(this)

        val appContext = applicationContext
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val threshold = if (isTv) 10L else 50L
        FileLogger.i("Init", "isTv=$isTv, cacheThreshold=${threshold}MB")

        FileLogger.i("Init", "Step 3: Async initialization (IO dispatcher)")
        applicationScope.launch(Dispatchers.IO) {
            FileLogger.i("Init", "→ DownloadRepository.migrateFromSharedPrefsIfNeeded()")
            runCatching { DownloadRepository(appContext).migrateFromSharedPrefsIfNeeded() }
                .onSuccess { FileLogger.i("Init", "✓ DownloadRepository migration done") }
                .onFailure { FileLogger.e("Init", "✗ DownloadRepository migration FAILED", it) }

            FileLogger.i("Init", "→ AppDatabase.setup()")
            runCatching { AppDatabase.setup(appContext) }
                .onSuccess { FileLogger.i("Init", "✓ AppDatabase setup") }
                .onFailure { FileLogger.e("Init", "✗ AppDatabase FAILED", it) }

            FileLogger.i("Init", "→ SupabaseProvider.initialize()")
            runCatching { SupabaseProvider.initialize(appContext) }
                .onSuccess { FileLogger.i("Init", "✓ SupabaseProvider initialized. isConfigured=${SupabaseProvider.isConfigured}") }
                .onFailure { FileLogger.e("Init", "✗ SupabaseProvider FAILED", it) }

            FileLogger.i("Init", "→ CloudSyncManager.initialize()")
            runCatching { CloudSyncManager.initialize(appContext) }
                .onSuccess { FileLogger.i("Init", "✓ CloudSyncManager initialized") }
                .onFailure { FileLogger.e("Init", "✗ CloudSyncManager FAILED", it) }

            FileLogger.i("Init", "→ SerienStreamProvider.initialize()")
            runCatching { SerienStreamProvider.initialize(appContext) }
                .onSuccess { FileLogger.i("Init", "✓ SerienStreamProvider initialized") }
                .onFailure { FileLogger.e("Init", "✗ SerienStreamProvider FAILED", it) }

            FileLogger.i("Init", "→ AniWorldProvider.initialize()")
            runCatching { AniWorldProvider.initialize(appContext) }
                .onSuccess { FileLogger.i("Init", "✓ AniWorldProvider initialized") }
                .onFailure { FileLogger.e("Init", "✗ AniWorldProvider FAILED", it) }

            FileLogger.i("Init", "→ ArtworkRepairScheduler.schedule()")
            runCatching { ArtworkRepairScheduler.schedule(appContext, UserPreferences.currentProvider) }
                .onSuccess { FileLogger.i("Init", "✓ ArtworkRepairScheduler scheduled") }
                .onFailure { FileLogger.e("Init", "✗ ArtworkRepairScheduler FAILED", it) }

            FileLogger.i("Init", "→ CacheUtils.autoClearIfNeeded()")
            runCatching { CacheUtils.autoClearIfNeeded(appContext, thresholdMb = threshold) }
                .onSuccess { FileLogger.i("Init", "✓ CacheUtils auto-clear done") }
                .onFailure { FileLogger.e("Init", "✗ CacheUtils FAILED", it) }

            FileLogger.i("Init", "=== All async initialization complete ===")
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            CacheUtils.clearAppCache(this)
        }
    }
}
