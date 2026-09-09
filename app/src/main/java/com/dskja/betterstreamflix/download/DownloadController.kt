package com.dskja.betterstreamflix.download

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.exoplayer.offline.DownloadHelper
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.providers.IptvProvider
import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.utils.UserPreferences
import com.dskja.betterstreamflix.utils.format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DownloadPrepareResult(
    val contentKey: String,
    val providerName: String,
    val kind: DownloadKind,
    val title: String,
    val subtitle: String,
    val posterUrl: String,
    val videoType: Video.Type,
    val videoTypeJson: String,
    val servers: List<ResolvedServerCandidate>,
    val selectedServerIndex: Int,
    val trackOptions: List<DownloadTrackOption>,
)

data class ResolvedServerCandidate(
    val server: Video.Server,
    val video: Video,
)

data class DownloadTrackOption(
    val label: String,
    val streamKeys: List<StreamKey>,
    val estimatedBytes: Long = -1L,
)

sealed class DownloadEnqueueOutcome {
    data class AlreadyCompleted(val item: DownloadItemEntity) : DownloadEnqueueOutcome()
    data class AlreadyActive(val item: DownloadItemEntity) : DownloadEnqueueOutcome()
    data class NeedsOptions(val prepared: DownloadPrepareResult) : DownloadEnqueueOutcome()
    data class Started(val item: DownloadItemEntity) : DownloadEnqueueOutcome()
    data class Failed(val code: DownloadErrorCode, val message: String) : DownloadEnqueueOutcome()
}

object DownloadController {
    private const val TAG = "DownloadController"
    private const val MAX_OPTION_SERVERS = 8
    private val helperExecutor = Executors.newSingleThreadExecutor()

    suspend fun prepareMovie(context: Context, movie: Movie): DownloadEnqueueOutcome =
        withContext(Dispatchers.IO) {
            val provider = UserPreferences.currentProvider
                ?: return@withContext DownloadEnqueueOutcome.Failed(
                    DownloadErrorCode.UNKNOWN,
                    "No provider",
                )
            if (provider is IptvProvider) {
                return@withContext DownloadEnqueueOutcome.Failed(
                    DownloadErrorCode.IPTV,
                    "Live TV cannot be downloaded",
                )
            }
            val contentKey = DownloadContentKey.movie(provider.name, movie.id)
            checkExisting(context, contentKey)?.let { return@withContext it }
            guardNetworkAndStorage(context)?.let { return@withContext it }

            val videoType = Video.Type.Movie(
                id = movie.id,
                title = movie.title,
                releaseDate = movie.released?.format("yyyy-MM-dd") ?: "",
                poster = movie.poster ?: "",
                imdbId = movie.imdbId,
            )
            resolveAndPrepare(
                context = context,
                provider = provider,
                contentKey = contentKey,
                kind = DownloadKind.MOVIE,
                title = movie.title,
                subtitle = provider.name,
                posterUrl = movie.poster.orEmpty(),
                videoType = videoType,
                contentId = movie.id,
            )
        }

    suspend fun prepareEpisode(context: Context, episode: Episode): DownloadEnqueueOutcome =
        withContext(Dispatchers.IO) {
            val provider = UserPreferences.currentProvider
                ?: return@withContext DownloadEnqueueOutcome.Failed(
                    DownloadErrorCode.UNKNOWN,
                    "No provider",
                )
            if (provider is IptvProvider) {
                return@withContext DownloadEnqueueOutcome.Failed(
                    DownloadErrorCode.IPTV,
                    "Live TV cannot be downloaded",
                )
            }
            val tvShow = episode.tvShow
                ?: return@withContext DownloadEnqueueOutcome.Failed(
                    DownloadErrorCode.UNKNOWN,
                    "Missing show",
                )
            val seasonNumber = episode.season?.number ?: 1
            val contentKey = DownloadContentKey.episode(
                providerName = provider.name,
                tvShowId = tvShow.id,
                seasonNumber = seasonNumber,
                episodeNumber = episode.number,
                episodeId = episode.id,
            )
            checkExisting(context, contentKey)?.let { return@withContext it }
            guardNetworkAndStorage(context)?.let { return@withContext it }

            val videoType = Video.Type.Episode(
                id = episode.id,
                number = episode.number,
                title = episode.title,
                poster = episode.poster,
                overview = episode.overview,
                tvShow = Video.Type.Episode.TvShow(
                    id = tvShow.id,
                    title = tvShow.title,
                    poster = tvShow.poster,
                    banner = tvShow.banner,
                    releaseDate = tvShow.released?.format("yyyy-MM-dd"),
                    imdbId = null,
                ),
                season = Video.Type.Episode.Season(
                    number = seasonNumber,
                    title = episode.season?.title,
                ),
            )
            resolveAndPrepare(
                context = context,
                provider = provider,
                contentKey = contentKey,
                kind = DownloadKind.EPISODE,
                title = tvShow.title,
                subtitle = "S${seasonNumber.toString().padStart(2, '0')}E${episode.number.toString().padStart(2, '0')}" +
                    (episode.title?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                posterUrl = episode.poster ?: tvShow.poster.orEmpty(),
                videoType = videoType,
                contentId = episode.id,
            )
        }

    suspend fun prepareFromResolved(
        context: Context,
        videoType: Video.Type,
        server: Video.Server,
        video: Video,
    ): DownloadEnqueueOutcome = withContext(Dispatchers.IO) {
        val provider = UserPreferences.currentProvider
            ?: return@withContext DownloadEnqueueOutcome.Failed(DownloadErrorCode.UNKNOWN, "No provider")
        if (provider is IptvProvider) {
            return@withContext DownloadEnqueueOutcome.Failed(DownloadErrorCode.IPTV, "Live TV cannot be downloaded")
        }
        guardNetworkAndStorage(context)?.let { return@withContext it }

        val contentKey: String
        val kind: DownloadKind
        val title: String
        val subtitle: String
        val poster: String
        when (videoType) {
            is Video.Type.Movie -> {
                contentKey = DownloadContentKey.movie(provider.name, videoType.id)
                kind = DownloadKind.MOVIE
                title = videoType.title
                subtitle = provider.name
                poster = videoType.poster
            }
            is Video.Type.Episode -> {
                contentKey = DownloadContentKey.episode(
                    provider.name,
                    videoType.tvShow.id,
                    videoType.season.number,
                    videoType.number,
                    videoType.id,
                )
                kind = DownloadKind.EPISODE
                title = videoType.tvShow.title
                subtitle = "S${videoType.season.number}E${videoType.number}"
                poster = videoType.poster ?: videoType.tvShow.poster.orEmpty()
            }
        }
        checkExisting(context, contentKey)?.let { return@withContext it }

        if (isUnsupportedSource(video.source)) {
            return@withContext DownloadEnqueueOutcome.Failed(
                DownloadErrorCode.UNSUPPORTED,
                "Unsupported stream format",
            )
        }
        if (looksLikeDrm(video.source)) {
            return@withContext DownloadEnqueueOutcome.Failed(DownloadErrorCode.DRM, "Protected stream")
        }

        val preparedTracks = prepareTrackOptions(context, video)
        DownloadEnqueueOutcome.NeedsOptions(
            DownloadPrepareResult(
                contentKey = contentKey,
                providerName = provider.name,
                kind = kind,
                title = title,
                subtitle = subtitle,
                posterUrl = poster,
                videoType = videoType,
                videoTypeJson = serializeVideoType(videoType),
                servers = listOf(ResolvedServerCandidate(server, video)),
                selectedServerIndex = 0,
                trackOptions = preparedTracks,
            ),
        )
    }

    suspend fun confirmEnqueue(
        context: Context,
        prepared: DownloadPrepareResult,
        serverIndex: Int,
        trackIndex: Int,
        qualityLabel: String,
    ): DownloadEnqueueOutcome = withContext(Dispatchers.IO) {
        guardNetworkAndStorage(context)?.let { return@withContext it }
        val candidate = prepared.servers.getOrNull(serverIndex)
            ?: prepared.servers.firstOrNull()
            ?: return@withContext DownloadEnqueueOutcome.Failed(
                DownloadErrorCode.NO_SERVERS,
                "No server",
            )
        val track = prepared.trackOptions.getOrNull(trackIndex)
        val item = DownloadRepository.get(context).enqueueResolved(
            contentKey = prepared.contentKey,
            providerName = prepared.providerName,
            kind = prepared.kind,
            title = prepared.title,
            subtitle = prepared.subtitle,
            posterUrl = prepared.posterUrl,
            videoTypeJson = prepared.videoTypeJson,
            serverName = candidate.server.name,
            serverId = candidate.server.id,
            qualityLabel = qualityLabel.ifBlank { track?.label ?: "Auto" },
            mimeType = candidate.video.type,
            streamUrl = candidate.video.source,
            headers = candidate.video.headers.orEmpty(),
            streamKeys = track?.streamKeys.orEmpty(),
        )
        DownloadEnqueueOutcome.Started(item)
    }

    suspend fun enqueueSeason(
        context: Context,
        tvShow: TvShow,
        seasonNumber: Int,
        episodes: List<Episode>,
        onEachPrepared: suspend (DownloadPrepareResult) -> Pair<Int, Int>,
    ): DownloadEnqueueOutcome = withContext(Dispatchers.IO) {
        val provider = UserPreferences.currentProvider
            ?: return@withContext DownloadEnqueueOutcome.Failed(DownloadErrorCode.UNKNOWN, "No provider")
        if (provider is IptvProvider) {
            return@withContext DownloadEnqueueOutcome.Failed(DownloadErrorCode.IPTV, "Live TV cannot be downloaded")
        }
        guardNetworkAndStorage(context)?.let { return@withContext it }
        if (episodes.isEmpty()) {
            return@withContext DownloadEnqueueOutcome.Failed(DownloadErrorCode.UNKNOWN, "No episodes")
        }

        val packId = DownloadContentKey.seasonPack(provider.name, tvShow.id, seasonNumber)
        val repo = DownloadRepository.get(context)
        repo.upsertSeasonPack(
            DownloadSeasonPackEntity(
                id = packId,
                providerName = provider.name,
                tvShowId = tvShow.id,
                tvShowTitle = tvShow.title,
                seasonNumber = seasonNumber,
                posterUrl = tvShow.poster.orEmpty(),
                totalEpisodes = episodes.size,
            ),
        )

        var started = 0
        episodes.forEachIndexed { index, episode ->
            episode.tvShow = tvShow
            val outcome = prepareEpisode(context, episode)
            when (outcome) {
                is DownloadEnqueueOutcome.NeedsOptions -> {
                    val (serverIdx, trackIdx) = onEachPrepared(outcome.prepared)
                    val label = outcome.prepared.trackOptions.getOrNull(trackIdx)?.label ?: "Auto"
                    val confirmed = confirmEnqueue(context, outcome.prepared, serverIdx, trackIdx, label)
                    if (confirmed is DownloadEnqueueOutcome.Started) {
                        val item = confirmed.item.copy(seasonPackId = packId, sortIndex = index)
                        repo.upsert(item)
                        started++
                    }
                }
                is DownloadEnqueueOutcome.Started -> {
                    repo.upsert(outcome.item.copy(seasonPackId = packId, sortIndex = index))
                    started++
                }
                is DownloadEnqueueOutcome.AlreadyActive,
                is DownloadEnqueueOutcome.AlreadyCompleted -> started++
                is DownloadEnqueueOutcome.Failed -> Log.w(TAG, "Season item failed: ${outcome.message}")
            }
        }
        repo.refreshSeasonPack(packId)
        if (started == 0) {
            DownloadEnqueueOutcome.Failed(DownloadErrorCode.NO_SERVERS, "No episodes queued")
        } else {
            val pack = repo.getSeasonPack(packId)
            DownloadEnqueueOutcome.Started(
                DownloadItemEntity(
                    id = packId,
                    contentKey = packId,
                    media3Id = packId,
                    providerName = provider.name,
                    kind = DownloadKind.EPISODE.name,
                    title = tvShow.title,
                    subtitle = "Season $seasonNumber · $started/${episodes.size}",
                    posterUrl = tvShow.poster.orEmpty(),
                    seasonPackId = packId,
                ),
            ).also { pack }
        }
    }

    private suspend fun resolveAndPrepare(
        context: Context,
        provider: Provider,
        contentKey: String,
        kind: DownloadKind,
        title: String,
        subtitle: String,
        posterUrl: String,
        videoType: Video.Type,
        contentId: String,
    ): DownloadEnqueueOutcome {
        val servers = try {
            provider.getServers(contentId, videoType)
        } catch (e: Exception) {
            Log.e(TAG, "getServers failed", e)
            return classifyFailure(e)
        }
        if (servers.isEmpty()) {
            return DownloadEnqueueOutcome.Failed(DownloadErrorCode.NO_SERVERS, "No servers found")
        }

        val resolved = mutableListOf<ResolvedServerCandidate>()
        var lastError: Exception? = null
        // Resolve several servers so the options dialog can switch hosters.
        for (server in servers.take(MAX_OPTION_SERVERS)) {
            try {
                val video = provider.getVideo(server)
                if (video.source.isBlank()) continue
                if (isUnsupportedSource(video.source)) continue
                if (looksLikeDrm(video.source)) continue
                resolved += ResolvedServerCandidate(server, video)
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "getVideo failed for ${server.name}: ${e.message}")
            }
        }

        if (resolved.isEmpty()) {
            return lastError?.let { classifyFailure(it) }
                ?: DownloadEnqueueOutcome.Failed(DownloadErrorCode.NO_SERVERS, "All servers failed")
        }

        val tracks = prepareTrackOptions(context, resolved.first().video)
        val prepared = DownloadPrepareResult(
            contentKey = contentKey,
            providerName = provider.name,
            kind = kind,
            title = title,
            subtitle = subtitle,
            posterUrl = posterUrl,
            videoType = videoType,
            videoTypeJson = serializeVideoType(videoType),
            servers = resolved,
            selectedServerIndex = 0,
            trackOptions = tracks,
        )

        // Always show options so users can pick server + quality before starting.
        return DownloadEnqueueOutcome.NeedsOptions(prepared)
    }

    suspend fun prepareTrackOptions(context: Context, video: Video): List<DownloadTrackOption> {
        return try {
            val helper = createHelper(context, video)
            prepareHelper(helper)
            val options = mutableListOf<DownloadTrackOption>()
            // Default: download all tracks Media3 would pick (empty stream keys = default)
            options += DownloadTrackOption(label = "Best available", streamKeys = emptyList())
            // Expose video track heights when present
            val mapped = runCatching {
                val periodCount = helper.periodCount
                val keys = mutableListOf<Pair<String, List<StreamKey>>>()
                for (periodIndex in 0 until periodCount) {
                    val tracks = helper.getTracks(periodIndex)
                    tracks.groups.forEachIndexed { groupIndex, group ->
                        if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val height = format.height
                                if (height > 0) {
                                    val label = "${height}p"
                                    val streamKey = StreamKey(periodIndex, groupIndex, i)
                                    keys += label to listOf(streamKey)
                                }
                            }
                        }
                    }
                }
                keys.distinctBy { it.first }
                    .sortedByDescending { it.first.removeSuffix("p").toIntOrNull() ?: 0 }
                    .map { DownloadTrackOption(it.first, it.second) }
            }.getOrDefault(emptyList())
            if (mapped.isNotEmpty()) {
                options.clear()
                options += mapped
                if (mapped.size > 1) {
                    options += DownloadTrackOption(
                        label = "Data saver",
                        streamKeys = mapped.last().streamKeys,
                    )
                }
            }
            helper.release()
            options
        } catch (e: Exception) {
            Log.w(TAG, "prepareTracks failed: ${e.message}")
            listOf(DownloadTrackOption("Auto", emptyList()))
        }
    }

    private fun createHelper(context: Context, video: Video): DownloadHelper {
        val factory = StreamflixDownloadManager.dataSourceFactory(context)
        factory.activeHeaders = video.headers.orEmpty()
        val mime = video.type ?: when {
            video.source.contains(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
            video.source.contains(".mpd", true) -> MimeTypes.APPLICATION_MPD
            else -> null
        }
        val mediaItem = MediaItem.Builder()
            .setUri(video.source)
            .setMimeType(mime)
            .build()
        return DownloadHelper.forMediaItem(
            context,
            mediaItem,
            null,
            factory,
        )
    }

    private suspend fun prepareHelper(helper: DownloadHelper) =
        suspendCancellableCoroutine { cont ->
            helper.prepare(object : DownloadHelper.Callback {
                override fun onPrepared(helper: DownloadHelper, tracksInfoAvailable: Boolean) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            })
            cont.invokeOnCancellation {
                runCatching { helper.release() }
            }
        }

    private suspend fun checkExisting(
        context: Context,
        contentKey: String,
    ): DownloadEnqueueOutcome? {
        val existing = DownloadRepository.get(context).getByContentKey(contentKey) ?: return null
        return when (DownloadItemState.fromKey(existing.state)) {
            DownloadItemState.COMPLETED -> DownloadEnqueueOutcome.AlreadyCompleted(existing)
            DownloadItemState.QUEUED,
            DownloadItemState.PREPARING,
            DownloadItemState.DOWNLOADING,
            DownloadItemState.PAUSED,
            -> DownloadEnqueueOutcome.AlreadyActive(existing)
            else -> null
        }
    }

    private fun guardNetworkAndStorage(context: Context): DownloadEnqueueOutcome.Failed? {
        if (!DownloadStorage.hasEnoughSpace(context)) {
            return DownloadEnqueueOutcome.Failed(DownloadErrorCode.NOSPACE, "Not enough storage")
        }
        if (UserPreferences.downloadWifiOnly && DownloadConnectivityMonitor.isMetered(context)) {
            return DownloadEnqueueOutcome.Failed(DownloadErrorCode.WIFI_REQUIRED, "Wi-Fi required")
        }
        val net = DownloadConnectivityMonitor.current(context)
        if (net.type == DownloadNetworkType.NONE) {
            return DownloadEnqueueOutcome.Failed(DownloadErrorCode.NETWORK, "No network")
        }
        return null
    }

    private fun isUnsupportedSource(source: String): Boolean {
        if (source.isBlank()) return true
        if (source.startsWith("data:", ignoreCase = true)) return true
        if (!(source.startsWith("http://") || source.startsWith("https://"))) return true
        return false
    }

    private fun looksLikeDrm(source: String): Boolean =
        source.contains("drm", ignoreCase = true) && source.contains("license", ignoreCase = true)

    private fun classifyFailure(e: Exception): DownloadEnqueueOutcome.Failed {
        val msg = e.message.orEmpty()
        val code = when {
            msg.contains("cloudflare", true) || msg.contains("captcha", true) ||
                msg.contains("Just a moment", true) -> DownloadErrorCode.CLOUDFLARE
            msg.contains("403") || msg.contains("401") -> DownloadErrorCode.CLOUDFLARE
            msg.contains("DRM", true) || msg.contains("Widevine", true) -> DownloadErrorCode.DRM
            msg.contains("Unable to resolve host", true) || msg.contains("timeout", true) ->
                DownloadErrorCode.NETWORK
            else -> DownloadErrorCode.UNKNOWN
        }
        return DownloadEnqueueOutcome.Failed(code, msg.ifBlank { code.name })
    }

    fun serializeVideoType(videoType: Video.Type): String {
        return when (videoType) {
            is Video.Type.Movie -> JSONObject()
                .put("kind", "movie")
                .put("id", videoType.id)
                .put("title", videoType.title)
                .put("releaseDate", videoType.releaseDate)
                .put("poster", videoType.poster)
                .put("imdbId", videoType.imdbId)
                .toString()
            is Video.Type.Episode -> JSONObject()
                .put("kind", "episode")
                .put("id", videoType.id)
                .put("number", videoType.number)
                .put("title", videoType.title)
                .put("poster", videoType.poster)
                .put("overview", videoType.overview)
                .put("showId", videoType.tvShow.id)
                .put("showTitle", videoType.tvShow.title)
                .put("showPoster", videoType.tvShow.poster)
                .put("showBanner", videoType.tvShow.banner)
                .put("seasonNumber", videoType.season.number)
                .put("seasonTitle", videoType.season.title)
                .toString()
        }
    }

    fun deserializeVideoType(json: String): Video.Type? {
        return runCatching {
            val o = JSONObject(json)
            when (o.optString("kind")) {
                "movie" -> Video.Type.Movie(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    releaseDate = o.optString("releaseDate"),
                    poster = o.optString("poster"),
                    imdbId = o.optString("imdbId").takeIf { it.isNotBlank() },
                )
                "episode" -> Video.Type.Episode(
                    id = o.getString("id"),
                    number = o.optInt("number"),
                    title = o.optString("title").takeIf { it.isNotBlank() },
                    poster = o.optString("poster").takeIf { it.isNotBlank() },
                    overview = o.optString("overview").takeIf { it.isNotBlank() },
                    tvShow = Video.Type.Episode.TvShow(
                        id = o.optString("showId"),
                        title = o.optString("showTitle"),
                        poster = o.optString("showPoster").takeIf { it.isNotBlank() },
                        banner = o.optString("showBanner").takeIf { it.isNotBlank() },
                        releaseDate = null,
                        imdbId = null,
                    ),
                    season = Video.Type.Episode.Season(
                        number = o.optInt("seasonNumber", 1),
                        title = o.optString("seasonTitle").takeIf { it.isNotBlank() },
                    ),
                )
                else -> null
            }
        }.getOrNull()
    }

    private data class Tuple5<A, B, C, D, E>(
        val a: A,
        val b: B,
        val c: C,
        val d: D,
        val e: E,
    )
}
