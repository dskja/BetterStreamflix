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

    fun get(context: Context, media3Id: String): Map<String, String> {
        val raw = prefs(context).getString(media3Id, null) ?: return emptyMap()
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

    fun remove(context: Context, media3Id: String) {
        prefs(context).edit().remove(media3Id).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
