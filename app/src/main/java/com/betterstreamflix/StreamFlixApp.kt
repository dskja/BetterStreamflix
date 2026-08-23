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
import com.betterstreamflix.utils.GlobalErrorHandler
import com.betterstreamflix.utils.IsrgRootTrustProvider
import com.betterstreamflix.utils.UserPreferences
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

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + GlobalErrorHandler.handler)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
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
        runCatching { Security.insertProviderAt(Conscrypt.newProvider(), 1) }

        // 1. Install ISRG Root X1 globally for Let's Encrypt. On Android < 7 (API 24)
        // network_security_config.xml is not supported so the certificate must be injected manually.
        runCatching { IsrgRootTrustProvider.install() }

        // 2. Inizializzazione preferenze (con applicationContext)
        UserPreferences.setup(this)
        DnsResolver.setDnsUrl(UserPreferences.dohProviderUrl)

        val appContext = applicationContext
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val threshold = if (isTv) 10L else 50L

        applicationScope.launch(Dispatchers.IO) {
            runCatching { AppDatabase.setup(appContext) }
            runCatching { SupabaseProvider.initialize(appContext) }
            runCatching { CloudSyncManager.initialize(appContext) }
            runCatching { SerienStreamProvider.initialize(appContext) }
            runCatching { AniWorldProvider.initialize(appContext) }
            runCatching { ArtworkRepairScheduler.schedule(appContext, UserPreferences.currentProvider) }
            runCatching { CacheUtils.autoClearIfNeeded(appContext, thresholdMb = threshold) }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            CacheUtils.clearAppCache(this)
        }
    }
}
