package com.dskja.betterstreamflix.utils

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.google.gson.Gson
import com.dskja.betterstreamflix.models.Video
import java.io.File

/**
 * Durable on-device cache for OpenSubtitles / SubDL files so playback can keep
 * using a previously selected subtitle without re-hitting flaky APIs.
 */
object SubtitleFileCache {

    const val PROVIDER_OPENSUBTITLES = "opensubtitles"
    const val PROVIDER_SUBDL = "subdl"

    private const val ROOT_DIR = "subtitles"
    private const val LAST_SELECTION_FILE = "last_selection.json"
    private val gson = Gson()

    data class CachedSubtitle(
        val provider: String,
        val fileId: String,
        val label: String,
        val language: String? = null,
        val fileName: String,
    )

    fun contentKey(videoType: Video.Type): String = when (videoType) {
        is Video.Type.Movie -> "movie_${sanitize(videoType.id)}"
        is Video.Type.Episode ->
            "episode_${sanitize(videoType.tvShow.id)}_s${videoType.season.number}_e${videoType.number}"
    }

    fun getUri(
        context: Context,
        contentKey: String,
        provider: String,
        fileId: String,
    ): Uri? {
        val file = resolveFile(context, contentKey, provider, fileId) ?: return null
        return if (file.exists() && file.length() > 0L) file.toUri() else null
    }

    fun put(
        context: Context,
        contentKey: String,
        provider: String,
        fileId: String,
        label: String,
        language: String?,
        sourceFile: File,
    ): Uri {
        val dir = contentDir(context, contentKey).apply { mkdirs() }
        val extension = sourceFile.extension.ifBlank { "srt" }
        val target = File(dir, cacheFileName(provider, fileId, extension))
        if (target.exists()) target.delete()
        sourceFile.copyTo(target, overwrite = true)
        val cached = CachedSubtitle(
            provider = provider,
            fileId = fileId,
            label = label,
            language = language,
            fileName = target.name,
        )
        remember(context, contentKey, cached)
        return target.toUri()
    }

    fun remember(context: Context, contentKey: String, cached: CachedSubtitle) {
        val dir = contentDir(context, contentKey).apply { mkdirs() }
        File(dir, LAST_SELECTION_FILE).writeText(gson.toJson(cached))
    }

    fun lastSelection(context: Context, contentKey: String): CachedSubtitle? {
        val file = File(contentDir(context, contentKey), LAST_SELECTION_FILE)
        if (!file.exists()) return null
        return runCatching {
            gson.fromJson(file.readText(), CachedSubtitle::class.java)
        }.getOrNull()?.takeIf { cached ->
            resolveFile(context, contentKey, cached)?.exists() == true
        }
    }

    fun resolveFile(context: Context, contentKey: String, cached: CachedSubtitle): File? =
        resolveFile(context, contentKey, cached.provider, cached.fileId)
            ?: File(contentDir(context, contentKey), cached.fileName).takeIf { it.exists() }

    private fun resolveFile(
        context: Context,
        contentKey: String,
        provider: String,
        fileId: String,
    ): File? {
        val dir = contentDir(context, contentKey)
        if (!dir.exists()) return null
        val prefix = "${sanitize(provider)}_${sanitize(fileId)}."
        return dir.listFiles()
            ?.firstOrNull { it.isFile && it.name.startsWith(prefix) && it.length() > 0L }
    }

    private fun contentDir(context: Context, contentKey: String): File =
        File(context.applicationContext.filesDir, "$ROOT_DIR/${sanitize(contentKey)}")

    private fun cacheFileName(provider: String, fileId: String, extension: String): String =
        "${sanitize(provider)}_${sanitize(fileId)}.${sanitize(extension).ifBlank { "srt" }}"

    private fun sanitize(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "unknown" }
}
