package com.streamflixreborn.streamflix

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.providers.AniWorldProvider
import com.streamflixreborn.streamflix.providers.SerienStreamProvider
import com.streamflixreborn.streamflix.utils.CacheUtils
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.UserPreferences

class StreamFlixApp : Application() {
    companion object {
        lateinit var instance: StreamFlixApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Inizializzazione preferenze (con applicationContext)
        UserPreferences.setup(this)
        
        // 2. Configurazione DNS
        DnsResolver.setDnsUrl(UserPreferences.dohProviderUrl)

        // 3. Inizializzazione Database per il provider corrente
        AppDatabase.setup(this)

        // 4. Inizializzazione provider specifici (se necessario)
        SerienStreamProvider.initialize(this)
        AniWorldProvider.initialize(this)

        // 5. Pulizia cache intelligente
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val threshold = if (isTv) 10L else 50L
        CacheUtils.autoClearIfNeeded(this, thresholdMb = threshold)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            CacheUtils.clearAppCache(this)
        }
    }
}
