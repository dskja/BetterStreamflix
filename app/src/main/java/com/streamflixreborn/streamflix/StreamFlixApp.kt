package com.streamflixreborn.streamflix

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.providers.AniWorldProvider
import com.streamflixreborn.streamflix.providers.SerienStreamProvider
import com.streamflixreborn.streamflix.utils.CacheUtils
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.EpisodeManager
import com.streamflixreborn.streamflix.utils.UserPreferences

class StreamFlixApp : Application() {
    companion object {
        lateinit var instance: StreamFlixApp
            private set
        lateinit var database: AppDatabase
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        UserPreferences.setup(this)
        DnsResolver.setDnsUrl(UserPreferences.dohProviderUrl)

        SerienStreamProvider.initialize(this)
        AniWorldProvider.initialize(this)

        // Pulizia automatica della cache all'avvio
        // Differenziamo la soglia tra TV (più restrittiva) e Mobile
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val threshold = if (isTv) 10L else 50L
        
        CacheUtils.autoClearIfNeeded(this, thresholdMb = threshold)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Se il sistema è a corto di memoria, puliamo la cache completa
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            CacheUtils.clearAppCache(this)
        }
    }
}
