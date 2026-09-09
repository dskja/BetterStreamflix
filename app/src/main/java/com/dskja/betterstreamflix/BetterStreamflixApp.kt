package com.dskja.betterstreamflix

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import java.security.Security
import org.conscrypt.Conscrypt
import com.dskja.betterstreamflix.database.AppDatabase
import com.dskja.betterstreamflix.providers.AniWorldProvider
import com.dskja.betterstreamflix.providers.SerienStreamProvider
import com.dskja.betterstreamflix.sync.CloudSyncManager
import com.dskja.betterstreamflix.sync.SupabaseProvider
import com.dskja.betterstreamflix.utils.AppLanguageManager
import com.dskja.betterstreamflix.utils.ArtworkRepairScheduler
import com.dskja.betterstreamflix.utils.CacheUtils
import com.dskja.betterstreamflix.utils.DeviceCapabilities
import com.dskja.betterstreamflix.utils.DnsResolver
import com.dskja.betterstreamflix.utils.IsrgRootTrustProvider
import com.dskja.betterstreamflix.utils.TMDb3
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BetterStreamflixApp : Application() {
    companion object {
        lateinit var instance: BetterStreamflixApp
            private set

        @Volatile
        var currentActivity: Activity? = null
            private set
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

        // 0. Initialize Conscrypt for modern SSL on old Android.
        // Fire OS / low-RAM sticks can fail loading native Conscrypt — never abort startup.
        runCatching {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }.onFailure {
            android.util.Log.e("BetterStreamflixApp", "Conscrypt init failed: ${it.message}")
        }

        // 1. Install ISRG Root X1 globally for Let's Encrypt. On Android < 7 (API 24)
        // network_security_config.xml is not supported so the certificate must be injected manually.
        runCatching { IsrgRootTrustProvider.install() }

        // 2. Inizializzazione preferenze (con applicationContext)
        UserPreferences.setup(this)
        DnsResolver.setDnsUrl(UserPreferences.dohProviderUrl)
        // Rebuild after DoH is applied so the first TMDB call never uses system DNS.
        runCatching { TMDb3.rebuildService() }

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
            // Skip automatic cache wipe on constrained Fire TV sticks: creating a WebView
            // during cold start can kill the process right after the splash screen.
            if (!DeviceCapabilities.shouldUseConstrainedPlayback(appContext)) {
                runCatching { CacheUtils.autoClearIfNeeded(appContext, thresholdMb = threshold) }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW &&
            !DeviceCapabilities.shouldUseConstrainedPlayback(this)
        ) {
            CacheUtils.clearAppCache(this)
        }
    }
}
