package com.dskja.betterstreamflix.download

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Persists per-download HTTP headers so Media3's OkHttp data source can attach
 * Referer/UA/Origin while segments download in the background.
 */
object DownloadHeaderStore {
    private const val PREFS = "download_headers"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(context: Context, media3Id: String, headers: Map<String, String>) {
        val json = JSONObject()
        headers.forEach { (k, v) -> json.put(k, v) }
        prefs(context).edit().putString(media3Id, json.toString()).apply()
    }

    /**
     * Also index headers by stream URL and by origin so [HeaderInjectingDataSource]
     * can scope them per request — segment URLs differ from the manifest URL but
     * share the host, and headers like Referer/User-Agent are origin-scoped anyway.
     */
    fun putForUrl(context: Context, url: String, headers: Map<String, String>) {
        if (url.isBlank() || headers.isEmpty()) return
        val json = JSONObject()
        headers.forEach { (k, v) -> json.put(k, v) }
        val editor = prefs(context).edit().putString(urlKey(url), json.toString())
        hostOf(url)?.let { editor.putString(hostKey(it), json.toString()) }
        editor.apply()
    }

    fun getForUrl(context: Context, url: String): Map<String, String> {
        if (url.isBlank()) return emptyMap()
        val prefs = prefs(context)
        prefs.getString(urlKey(url), null)?.let { return decode(it) }
        val host = hostOf(url) ?: return emptyMap()
        return prefs.getString(hostKey(host), null)?.let { decode(it) } ?: emptyMap()
    }

    fun removeForUrl(context: Context, url: String) {
        if (url.isBlank()) return
        prefs(context).edit()
            .remove(urlKey(url))
            .also { editor -> hostOf(url)?.let { editor.remove(hostKey(it)) } }
            .apply()
    }

    private fun hostOf(url: String): String? = runCatching {
        val uri = android.net.Uri.parse(url)
        uri.host?.let { "${uri.scheme}://$it" }
    }.getOrNull()

    fun get(context: Context, media3Id: String): Map<String, String> {
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

    private fun hostKey(host: String): String = "host:" + sha256(host)

    private fun sha256(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun remove(context: Context, media3Id: String) {
        prefs(context).edit().remove(media3Id).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
