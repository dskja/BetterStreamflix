package com.betterstreamflix.database

import android.content.Context
import com.betterstreamflix.database.dao.WatchHistoryEntity
import com.betterstreamflix.database.dao.FavoriteEntity
import com.betterstreamflix.database.dao.DownloadEntity
import com.betterstreamflix.database.dao.CachedMetadataEntity
import com.betterstreamflix.database.dao.ProviderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository that wraps all DAOs for unified data access.
 */
class AppDataRepository(context: Context) {

    private val db = AppLevelDatabase.getInstance(context)

    // === Watch History ===

    suspend fun addWatchHistory(item: WatchHistoryEntity) = db.watchHistoryDao().insert(item)
    fun getWatchHistory(): Flow<List<WatchHistoryEntity>> = db.watchHistoryDao().getAll()
    fun getContinueWatching(): Flow<List<WatchHistoryEntity>> = db.watchHistoryDao().getContinueWatching()
    suspend fun getWatchHistoryItem(videoId: String, providerName: String) = db.watchHistoryDao().get(videoId, providerName)
    suspend fun deleteWatchHistoryItem(videoId: String, providerName: String) = db.watchHistoryDao().delete(videoId, providerName)
    suspend fun clearWatchHistory() = db.watchHistoryDao().clearAll()
    suspend fun clearWatchHistoryOlderThan(before: Long) = db.watchHistoryDao().clearOlderThan(before)

    // === Favorites ===

    suspend fun addFavorite(item: FavoriteEntity) = db.favoriteDao().insert(item)
    fun getFavorites(): Flow<List<FavoriteEntity>> = db.favoriteDao().getAll()
    fun getFavoritesByType(type: String): Flow<List<FavoriteEntity>> = db.favoriteDao().getByType(type)
    suspend fun isFavorite(videoId: String, providerName: String) = db.favoriteDao().isFavorite(videoId, providerName)
    suspend fun deleteFavorite(videoId: String, providerName: String) = db.favoriteDao().delete(videoId, providerName)
    suspend fun clearFavorites() = db.favoriteDao().clearAll()

    // === Downloads ===

    suspend fun addDownload(item: DownloadEntity) = db.downloadDao().insert(item)
    fun getDownloads() = db.downloadDao().getAll()
    fun getActiveDownloads() = db.downloadDao().getActive()
    fun getCompletedDownloads() = db.downloadDao().getCompleted()
    suspend fun updateDownloadProgress(id: String, bytes: Long) = db.downloadDao().updateProgress(id, bytes)
    suspend fun updateDownloadStatus(id: String, status: String) = db.downloadDao().updateStatus(id, status)
    suspend fun deleteDownload(id: String) = db.downloadDao().delete(id)
    suspend fun clearDownloads() = db.downloadDao().clearAll()

    // === Cached Metadata ===

    suspend fun cacheMetadata(item: CachedMetadataEntity) = db.metadataDao().insert(item)
    suspend fun getCachedMetadata(title: String, type: String) = db.metadataDao().get(title, type)
    suspend fun getCachedMetadataByTmdbId(tmdbId: Int) = db.metadataDao().getByTmdbId(tmdbId)
    suspend fun clearOldMetadata(before: Long) = db.metadataDao().clearOlderThan(before)
    suspend fun clearAllMetadata() = db.metadataDao().clearAll()

    // === Providers ===

    suspend fun upsertProvider(item: ProviderEntity) = db.providerDao().insert(item)
    fun getEnabledProviders() = db.providerDao().getEnabledProviders()
    fun getAllProviders() = db.providerDao().getAllProviders()
    suspend fun setProviderEnabled(name: String, enabled: Boolean) = db.providerDao().setEnabled(name, enabled)
    suspend fun updateProviderLastUsed(name: String, timestamp: Long) = db.providerDao().updateLastUsed(name, timestamp)
    suspend fun updateProviderDomain(name: String, domain: String) = db.providerDao().updateDomain(name, domain)
}
