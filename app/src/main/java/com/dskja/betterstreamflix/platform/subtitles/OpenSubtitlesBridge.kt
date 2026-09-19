package com.dskja.betterstreamflix.platform.subtitles

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.dskja.betterstreamflix.utils.OpenSubtitles
import com.dskja.betterstreamflix.utils.SubtitleFileCache
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Bridges OpenSubtitles.com v1 → legacy [OpenSubtitles.Subtitle] UI models,
 * with automatic fallback to the old REST API when v1 is unavailable.
 */
object OpenSubtitlesBridge {
    private const val TAG = "OpenSubtitlesBridge"

    suspend fun search(
        imdbId: String? = null,
        tmdbId: Int? = null,
        query: String? = null,
        season: Int? = null,
        episode: Int? = null,
        languages: String = preferredLanguages(),
    ): List<OpenSubtitles.Subtitle> = withContext(Dispatchers.IO) {
        if (OpenSubtitlesV1Client.configured()) {
            when (
                val result = OpenSubtitlesV1Client.search(
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                    query = query,
                    languages = languages,
                    season = season,
                    episode = episode,
                )
            ) {
                is OpenSubtitlesV1Client.Result.Ok -> {
                    if (result.value.isNotEmpty()) {
                        return@withContext result.value.map { it.toLegacySubtitle() }
                    }
                    Log.i(TAG, "V1 returned 0 hits — falling back to legacy")
                }
                is OpenSubtitlesV1Client.Result.Err -> {
                    Log.w(TAG, "V1 search failed: ${result.reason} — legacy fallback")
                }
            }
        }
        OpenSubtitles.search(
            imdbId = imdbId,
            query = query,
            season = season,
            episode = episode,
        )
    }

    suspend fun download(
        context: Context,
        subtitle: OpenSubtitles.Subtitle,
        contentKey: String,
    ): Uri = withContext(Dispatchers.IO) {
        val fileId = subtitle.idSubtitleFile?.toIntOrNull()
        val isV1 = subtitle.matchedBy == "opensubtitles.com" ||
            subtitle.subDownloadLink.isBlank() && fileId != null
        if (isV1 && OpenSubtitlesV1Client.configured() && fileId != null) {
            when (val dl = OpenSubtitlesV1Client.requestDownload(fileId)) {
                is OpenSubtitlesV1Client.Result.Ok -> {
                    return@withContext downloadUrlToCache(
                        context = context,
                        url = dl.value,
                        contentKey = contentKey,
                        fileId = fileId.toString(),
                        fileName = subtitle.subFileName ?: "subtitle.$fileId.srt",
                        label = subtitle.subFileName ?: subtitle.languageName ?: fileId.toString(),
                        language = subtitle.languageName,
                    )
                }
                is OpenSubtitlesV1Client.Result.Err -> {
                    Log.w(TAG, "V1 download failed: ${dl.reason}")
                    if (subtitle.subDownloadLink.isNotBlank()) {
                        return@withContext OpenSubtitles.download(context, subtitle, contentKey)
                    }
                    error(dl.reason)
                }
            }
        }
        OpenSubtitles.download(context, subtitle, contentKey)
    }

    fun OpenSubtitlesV1Client.SubtitleHit.toLegacySubtitle(): OpenSubtitles.Subtitle =
        OpenSubtitles.Subtitle(
            matchedBy = "opensubtitles.com",
            idSubtitleFile = fileId.toString(),
            subFileName = release.ifBlank { "subtitle.$fileId.srt" },
            languageName = language,
            iso639 = language,
            subDownloadsCnt = downloadCount.toString(),
            subHearingImpaired = if (hearingImpaired) "1" else "0",
            movieReleaseName = release,
            idSubtitle = id,
            // V1 downloads via /download — link filled at download time.
            subDownloadLink = "",
        )

    private fun preferredLanguages(): String {
        val pref = runCatching { UserPreferences.openSubtitlesLanguages }.getOrDefault("")
        return pref.ifBlank { "en,de" }
    }

    private fun downloadUrlToCache(
        context: Context,
        url: String,
        contentKey: String,
        fileId: String,
        fileName: String,
        label: String,
        language: String?,
    ): Uri {
        SubtitleFileCache.getUri(
            context = context,
            contentKey = contentKey,
            provider = SubtitleFileCache.PROVIDER_OPENSUBTITLES,
            fileId = fileId,
        )?.let { return it }

        val tmp = File.createTempFile("osv1-", ".srt", context.cacheDir)
        URL(url).openStream().use { input ->
            FileOutputStream(tmp).use { output -> input.copyTo(output) }
        }
        val destDir = File(context.filesDir, "subtitles").also { it.mkdirs() }
        val dest = File(destDir, "osv1-$fileId-${File(fileName).name}")
        if (dest.exists()) dest.delete()
        tmp.copyTo(dest, overwrite = true)
        tmp.delete()
        SubtitleFileCache.remember(
            context = context,
            contentKey = contentKey,
            cached = SubtitleFileCache.CachedSubtitle(
                provider = SubtitleFileCache.PROVIDER_OPENSUBTITLES,
                fileId = fileId,
                label = label,
                language = language,
                fileName = dest.name,
            ),
        )
        return dest.toUri()
    }
}
