package com.dskja.betterstreamflix.download

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.media3.exoplayer.offline.Download
import com.dskja.betterstreamflix.models.Video
import org.json.JSONArray
import java.io.File

object OfflinePlayback {
    fun contentKeyFor(videoType: Video.Type, providerName: String): String {
        return when (videoType) {
            is Video.Type.Movie -> DownloadContentKey.movie(providerName, videoType.id)
            is Video.Type.Episode -> DownloadContentKey.episode(
                providerName = providerName,
                tvShowId = videoType.tvShow.id,
                seasonNumber = videoType.season.number,
                episodeNumber = videoType.number,
                episodeId = videoType.id,
            )
        }
    }

    suspend fun findCompleted(context: Context, videoType: Video.Type): DownloadItemEntity? {
        val providerName = videoType.let {
            // Prefer tagged provider from existing download by id match later
            com.dskja.betterstreamflix.utils.UserPreferences.currentProvider?.name
        } ?: return null
        val key = contentKeyFor(videoType, providerName)
        val item = DownloadRepository.get(context).getByContentKey(key)
            ?: return null
        return item.takeIf { it.state == DownloadItemState.COMPLETED.name }
    }

    suspend fun findCompletedAnyProvider(context: Context, videoType: Video.Type): DownloadItemEntity? {
        val repo = DownloadRepository.get(context)
        val all = repo.getAllOnce().filter { it.state == DownloadItemState.COMPLETED.name }
        return when (videoType) {
            is Video.Type.Movie -> all.firstOrNull {
                it.kind == DownloadKind.MOVIE.name &&
                    (it.contentKey == DownloadContentKey.movie(
                        it.providerName,
                        videoType.id,
                    ) || it.videoTypeJson.contains("\"id\":\"${videoType.id}\""))
            }
            is Video.Type.Episode -> all.firstOrNull {
                it.kind == DownloadKind.EPISODE.name &&
                    (it.contentKey == DownloadContentKey.episode(
                        it.providerName,
                        videoType.tvShow.id,
                        videoType.season.number,
                        videoType.number,
                        videoType.id,
                    ) || it.videoTypeJson.contains("\"id\":\"${videoType.id}\""))
            }
        }
    }

    fun buildLocalVideo(context: Context, item: DownloadItemEntity): Video? {
        val media3Id = item.media3Id
        val download = runCatching {
            StreamflixDownloadManager.get(context).downloadIndex.getDownload(media3Id)
        }.getOrNull()
        val source = when {
            download != null && download.state == Download.STATE_COMPLETED ->
                download.request.uri.toString()
            item.localUri.isNotBlank() -> item.localUri
            item.streamUrl.isNotBlank() -> item.streamUrl
            else -> return null
        }
        val headers = runCatching {
            val o = org.json.JSONObject(item.headersJson.ifBlank { "{}" })
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())

        val subs = runCatching {
            val arr = JSONArray(item.subtitlePathsJson.ifBlank { "[]" })
            buildList {
                for (i in 0 until arr.length()) {
                    val path = arr.optString(i)
                    if (path.isBlank()) continue
                    add(
                        Video.Subtitle(
                            label = File(path).nameWithoutExtension,
                            file = path,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())

        return Video(
            source = source,
            headers = headers.ifEmpty { null },
            type = item.mimeType.ifBlank { null },
            subtitles = subs,
        )
    }

    fun exportShareUri(context: Context, item: DownloadItemEntity): Uri? {
        val path = item.localUri.takeIf { it.isNotBlank() } ?: return null
        val file = when {
            path.startsWith("file:") -> File(Uri.parse(path).path ?: return null)
            path.startsWith("/") -> File(path)
            else -> return null
        }
        if (!file.exists()) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file,
        )
    }
}
