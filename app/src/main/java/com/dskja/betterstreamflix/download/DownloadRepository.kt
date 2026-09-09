package com.dskja.betterstreamflix.download

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.dskja.betterstreamflix.fragments.downloads.OfflineVideoCache
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.UUID

class DownloadRepository private constructor(
    private val context: Context,
    private val dao: DownloadDao,
) {
    fun observeAll(): Flow<List<DownloadItemEntity>> = dao.observeAll()

    fun observeCompletedKeys(): Flow<Set<String>> =
        dao.observeCompletedKeys().map { it.toSet() }

    fun observeSeasonPacks(): Flow<List<DownloadSeasonPackEntity>> = dao.observeSeasonPacks()

    suspend fun getAllOnce(): List<DownloadItemEntity> = dao.observeAll().first()

    suspend fun getById(id: String) = dao.getById(id)

    suspend fun getByContentKey(contentKey: String) = dao.getByContentKey(contentKey)

    suspend fun getByMedia3Id(media3Id: String) = dao.getByMedia3Id(media3Id)

    suspend fun completedKeys(): Set<String> = dao.completedKeys().toSet()

    suspend fun upsert(item: DownloadItemEntity) = dao.upsert(item)

    suspend fun updateProgress(
        id: String,
        state: DownloadItemState,
        bytesDownloaded: Long,
        contentLength: Long,
        progressPct: Int,
        speedBytesPerSec: Long,
        etaSeconds: Long,
        localUri: String,
        errorCode: String,
        errorMessage: String,
    ) {
        val current = dao.getById(id) ?: return
        dao.upsert(
            current.copy(
                state = state.name,
                bytesDownloaded = bytesDownloaded,
                contentLength = contentLength,
                progressPct = progressPct,
                speedBytesPerSec = speedBytesPerSec,
                etaSeconds = etaSeconds,
                localUri = localUri,
                errorCode = errorCode,
                errorMessage = errorMessage,
                updatedAt = System.currentTimeMillis(),
                completedAt = if (state == DownloadItemState.COMPLETED) {
                    System.currentTimeMillis()
                } else {
                    current.completedAt
                },
            ),
        )
    }

    suspend fun markFailed(id: String, code: DownloadErrorCode, message: String) {
        val current = dao.getById(id) ?: return
        dao.upsert(
            current.copy(
                state = DownloadItemState.FAILED.name,
                errorCode = code.name,
                errorMessage = message,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun enqueueResolved(
        contentKey: String,
        providerName: String,
        kind: DownloadKind,
        title: String,
        subtitle: String,
        posterUrl: String,
        videoTypeJson: String,
        serverName: String,
        serverId: String,
        qualityLabel: String,
        mimeType: String?,
        streamUrl: String,
        headers: Map<String, String>,
        seasonPackId: String? = null,
        sortIndex: Int = 0,
        streamKeys: List<StreamKey> = emptyList(),
    ): DownloadItemEntity {
        val existing = dao.getByContentKey(contentKey)
        if (existing != null && existing.state == DownloadItemState.COMPLETED.name) {
            return existing
        }
        if (existing != null && DownloadItemState.fromKey(existing.state).isActive) {
            return existing
        }

        val id = existing?.id ?: UUID.randomUUID().toString()
        val media3Id = existing?.media3Id?.takeIf { it.isNotBlank() } ?: id
        val headersJson = JSONObject(headers as Map<*, *>).toString()

        DownloadHeaderStore.put(context, media3Id, headers)
        StreamflixDownloadManager.dataSourceFactory(context).apply {
            activeMedia3Id = media3Id
            activeHeaders = headers
        }

        val mime = mimeType?.takeIf { it.isNotBlank() }
            ?: when {
                streamUrl.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
                streamUrl.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
                streamUrl.contains(".mp4", ignoreCase = true) -> MimeTypes.VIDEO_MP4
                else -> null
            }

        val requestBuilder = DownloadRequest.Builder(media3Id, Uri.parse(streamUrl))
            .setMimeType(mime)
            .setData(title.toByteArray(Charsets.UTF_8))
        if (streamKeys.isNotEmpty()) {
            requestBuilder.setStreamKeys(streamKeys)
        }
        val request = requestBuilder.build()

        val entity = DownloadItemEntity(
            id = id,
            contentKey = contentKey,
            media3Id = media3Id,
            providerName = providerName,
            kind = kind.name,
            title = title,
            subtitle = subtitle,
            posterUrl = posterUrl,
            videoTypeJson = videoTypeJson,
            serverName = serverName,
            serverId = serverId,
            qualityLabel = qualityLabel,
            mimeType = mime.orEmpty(),
            state = DownloadItemState.QUEUED.name,
            streamUrl = streamUrl,
            headersJson = headersJson,
            seasonPackId = seasonPackId,
            sortIndex = sortIndex,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)

        val dm = StreamflixDownloadManager.get(context)
        dm.maxParallelDownloads = UserPreferences.downloadMaxConcurrent.coerceIn(1, 4)
        DownloadService.sendAddDownload(
            context,
            StreamflixDownloadService::class.java,
            request,
            /* foreground= */ true,
        )
        return entity
    }

    suspend fun pause(id: String) {
        val item = dao.getById(id) ?: return
        DownloadService.sendSetStopReason(
            context,
            StreamflixDownloadService::class.java,
            item.media3Id,
            /* stopReason= */ 1,
            false,
        )
        dao.upsert(
            item.copy(
                state = DownloadItemState.PAUSED.name,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun resume(id: String) {
        val item = dao.getById(id) ?: return
        if (UserPreferences.downloadWifiOnly && DownloadConnectivityMonitor.isMetered(context)) {
            // Soft-gate only: keep the item paused instead of marking a terminal failure.
            dao.upsert(
                item.copy(
                    state = DownloadItemState.PAUSED.name,
                    errorCode = DownloadErrorCode.WIFI_REQUIRED.name,
                    errorMessage = "Wi-Fi required",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return
        }
        DownloadService.sendSetStopReason(
            context,
            StreamflixDownloadService::class.java,
            item.media3Id,
            Download.STOP_REASON_NONE,
            false,
        )
        dao.upsert(
            item.copy(
                state = DownloadItemState.QUEUED.name,
                errorCode = "",
                errorMessage = "",
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun remove(id: String) {
        val item = dao.getById(id) ?: return
        DownloadService.sendRemoveDownload(
            context,
            StreamflixDownloadService::class.java,
            item.media3Id,
            false,
        )
        DownloadHeaderStore.remove(context, item.media3Id)
        DownloadStorage.deleteQuietly(DownloadStorage.subsDir(context, item.contentKey))
        dao.deleteById(id)
        OfflineVideoCache.remove(item.contentKey)
        item.seasonPackId?.let { refreshSeasonPack(it) }
    }

    suspend fun pauseAll() {
        getAllOnce().filter { DownloadItemState.fromKey(it.state).isActive }.forEach { pause(it.id) }
    }

    suspend fun resumeAll() {
        if (UserPreferences.downloadWifiOnly && DownloadConnectivityMonitor.isMetered(context)) return
        DownloadService.sendResumeDownloads(
            context,
            StreamflixDownloadService::class.java,
            false,
        )
        // Only resume paused rows. Hard failures (DRM/network/etc.) stay failed until manual retry.
        getAllOnce()
            .filter {
                it.state == DownloadItemState.PAUSED.name ||
                    (
                        it.state == DownloadItemState.FAILED.name &&
                            it.errorCode == DownloadErrorCode.WIFI_REQUIRED.name
                        )
            }
            .forEach { resume(it.id) }
    }

    suspend fun clearCompleted() {
        getAllOnce()
            .filter { it.state == DownloadItemState.COMPLETED.name }
            .forEach { remove(it.id) }
    }

    suspend fun clearFailed() {
        getAllOnce()
            .filter { it.state == DownloadItemState.FAILED.name }
            .forEach { remove(it.id) }
    }

    suspend fun clearAll() {
        getAllOnce().forEach { remove(it.id) }
        dao.deleteAll()
        DownloadHeaderStore.clear(context)
        OfflineVideoCache.clear()
    }

    suspend fun upsertSeasonPack(pack: DownloadSeasonPackEntity) = dao.upsertSeasonPack(pack)

    suspend fun getSeasonPack(id: String) = dao.getSeasonPack(id)

    suspend fun refreshSeasonPack(packId: String) {
        val pack = dao.getSeasonPack(packId) ?: return
        val items = dao.itemsForPack(packId)
        val completed = items.count { it.state == DownloadItemState.COMPLETED.name }
        val failed = items.count { it.state == DownloadItemState.FAILED.name }
        val active = items.any { DownloadItemState.fromKey(it.state).isActive }
        val state = when {
            items.isNotEmpty() && completed == items.size -> DownloadItemState.COMPLETED
            items.isNotEmpty() && failed == items.size -> DownloadItemState.FAILED
            active -> DownloadItemState.DOWNLOADING
            else -> DownloadItemState.QUEUED
        }
        dao.upsertSeasonPack(
            pack.copy(
                completedEpisodes = completed,
                failedEpisodes = failed,
                totalEpisodes = items.size.coerceAtLeast(pack.totalEpisodes),
                state = state.name,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    companion object {
        @Volatile
        private var instance: DownloadRepository? = null

        fun get(context: Context): DownloadRepository {
            return instance ?: synchronized(this) {
                instance ?: DownloadRepository(
                    context.applicationContext,
                    DownloadDatabase.get(context).downloadDao(),
                ).also { instance = it }
            }
        }
    }
}
