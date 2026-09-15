package com.dskja.betterstreamflix.download

import android.content.Context
import android.util.Log
import com.dskja.betterstreamflix.utils.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Fetches the subtitle files listed in `DownloadItemEntity.subtitleUrlsJson`
 * into the download's sidecar dir and records the local paths in
 * `subtitlePathsJson` (read by [OfflinePlayback.buildLocalVideo]).
 *
 * Runs after the Media3 download completes — a subtitle failure never marks
 * the video download itself as failed.
 */
object SubtitleFetch {
    private const val TAG = "SubtitleFetch"
    private const val MAX_SUB_BYTES = 8 * 1024 * 1024L

    suspend fun fetchFor(context: Context, item: DownloadItemEntity) = withContext(Dispatchers.IO) {
        val urls = decodeUrls(item.subtitleUrlsJson)
        if (urls.isEmpty()) return@withContext

        val dir = DownloadStorage.subsDir(context, item.contentKey)
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "cannot create subs dir for ${item.contentKey}")
            return@withContext
        }

        val headers = decodeHeaders(item.headersJson)
        val written = mutableListOf<String>()
        urls.forEachIndexed { index, (lang, url) ->
            val file = File(dir, "${index}_${sanitize(lang)}${extOf(url)}")
            if (!file.exists() || file.length() == 0L) {
                val ok = download(url, file, headers)
                if (!ok) return@forEachIndexed
            }
            written += file.absolutePath
        }

        val arr = JSONArray()
        written.forEach { arr.put(it) }
        DownloadRepository.get(context).getById(item.id)?.let { current ->
            DownloadRepository.get(context).upsert(
                current.copy(
                    subtitlePathsJson = arr.toString(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun download(url: String, dest: File, headers: Map<String, String>): Boolean {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        return runCatching {
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.header(k, v) }
            NetworkClient.default.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching false
                val body = resp.body ?: return@runCatching false
                if (body.contentLength() > MAX_SUB_BYTES) return@runCatching false
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_SUB_BYTES) return@runCatching false
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            if (tmp.length() == 0L) return@runCatching false
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
            true
        }.getOrElse {
            Log.w(TAG, "subtitle fetch failed: $url", it)
            false
        }.also { ok ->
            if (!ok) tmp.delete()
        }
    }

    fun decodeUrls(json: String): List<Pair<String, String>> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val url = obj.optString("url")
                    if (url.isBlank()) continue
                    add(obj.optString("lang", "sub") to url)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun decodeHeaders(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        }.getOrDefault(emptyMap())
    }

    private fun sanitize(label: String): String =
        label.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(24).ifBlank { "sub" }

    private fun extOf(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "")
        return when (ext.lowercase()) {
            "vtt", "srt", "ass", "ssa", "ttml", "dfxp" -> ".$ext"
            else -> ".vtt"
        }
    }
}
