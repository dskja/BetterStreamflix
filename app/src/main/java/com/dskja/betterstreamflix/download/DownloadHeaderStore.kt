package com.dskja.betterstreamflix.download

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Persists per-download HTTP headers so Media3's OkHttp data source can attach
 * Referer/UA/Origin while segments download in the background.
 *
 * Host-level lookup must not share one cookie jar across parallel downloads on
 * the same CDN — that caused 403s when maxParallelDownloads > 1. Exact URL keys
 * win; host fallback only applies when a single active download owns that host.
 */
object DownloadHeaderStore {
    private const val PREFS = "download_headers"

    private val activeByMedia3Id = ConcurrentHashMap<String, Map<String, String>>()
    private val urlToMedia3Id = ConcurrentHashMap<String, String>()
    private val hostToMedia3Ids = ConcurrentHashMap<String, CopyOnWriteArraySet<String>>()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(context: Context, media3Id: String, headers: Map<String, String>) {
        val json = JSONObject()
        headers.forEach { (k, v) -> json.put(k, v) }
        prefs(context).edit().putString(media3Id, json.toString()).apply()
        if (headers.isNotEmpty()) {
            activeByMedia3Id[media3Id] = headers
        }
    }

    /**
     * Index headers by stream URL and register the download as the active owner
     * of that origin so segment requests can resolve headers safely.
     */
    fun putForUrl(
        context: Context,
        url: String,
        headers: Map<String, String>,
        media3Id: String? = null,
    ) {
        if (url.isBlank() || headers.isEmpty()) return
        val json = JSONObject()
        headers.forEach { (k, v) -> json.put(k, v) }
        prefs(context).edit().putString(urlKey(url), json.toString()).apply()

        val id = media3Id?.takeIf { it.isNotBlank() } ?: "url:" + sha256(url)
        activeByMedia3Id[id] = headers
        urlToMedia3Id[url] = id
        hostOf(url)?.let { host ->
            hostToMedia3Ids.getOrPut(host) { CopyOnWriteArraySet() }.add(id)
        }
    }

    fun getForUrl(context: Context, url: String): Map<String, String> {
        if (url.isBlank()) return emptyMap()

        urlToMedia3Id[url]?.let { id ->
            activeByMedia3Id[id]?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        prefs(context).getString(urlKey(url), null)?.let { return decode(it) }

        val host = hostOf(url) ?: return emptyMap()
        val owners = hostToMedia3Ids[host].orEmpty()
        if (owners.size == 1) {
            val id = owners.first()
            activeByMedia3Id[id]?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        // Multiple parallel downloads on the same host: do not guess — prefer
        // path-prefix match against known stream URLs of active owners.
        if (owners.isNotEmpty()) {
            val match = urlToMedia3Id.entries.firstOrNull { (knownUrl, id) ->
                id in owners && (url.startsWith(knownUrl.substringBefore('?')) ||
                    knownUrl.startsWith(url.substringBefore('?')))
            }
            match?.let { activeByMedia3Id[it.value]?.takeIf { h -> h.isNotEmpty() }?.let { return it } }
        }
        return emptyMap()
    }

    fun removeForUrl(context: Context, url: String) {
        if (url.isBlank()) return
        prefs(context).edit().remove(urlKey(url)).apply()
        val id = urlToMedia3Id.remove(url)
        hostOf(url)?.let { host ->
            val set = hostToMedia3Ids[host]
            if (id != null) set?.remove(id)
            if (set != null && set.isEmpty()) hostToMedia3Ids.remove(host)
        }
        if (id != null && urlToMedia3Id.values.none { it == id }) {
            activeByMedia3Id.remove(id)
        }
    }

    private fun hostOf(url: String): String? = runCatching {
        val uri = android.net.Uri.parse(url)
        uri.host?.let { "${uri.scheme}://$it" }
    }.getOrNull()

    fun get(context: Context, media3Id: String): Map<String, String> {
        activeByMedia3Id[media3Id]?.takeIf { it.isNotEmpty() }?.let { return it }
        val raw = prefs(context).getString(media3Id, null) ?: return emptyMap()
        return decode(raw)
    }

    private fun decode(raw: String): Map<String, String> {
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, json.optString(key))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun urlKey(url: String): String = "url:" + sha256(url)

    private fun sha256(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun remove(context: Context, media3Id: String) {
        prefs(context).edit().remove(media3Id).apply()
        activeByMedia3Id.remove(media3Id)
        urlToMedia3Id.entries.removeIf { it.value == media3Id }
        hostToMedia3Ids.values.forEach { it.remove(media3Id) }
        hostToMedia3Ids.entries.removeIf { it.value.isEmpty() }
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        activeByMedia3Id.clear()
        urlToMedia3Id.clear()
        hostToMedia3Ids.clear()
    }
}
