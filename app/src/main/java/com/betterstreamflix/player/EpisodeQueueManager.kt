package com.betterstreamflix.player

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manages the "next episode" queue for binge-watching.
 * Tracks which episodes are queued and provides auto-play functionality.
 */
object EpisodeQueueManager {

    private const val PREFS_NAME = "episode_queue"
    private const val KEY_QUEUE = "queue_items"
    private const val KEY_AUTOPLAY = "autoplay_next"

    data class QueuedEpisode(
        val videoId: String,
        val title: String,
        val providerName: String,
        val seasonNumber: Int,
        val episodeNumber: Int,
        val thumbnailUrl: String? = null,
    )

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Set the episode queue (ordered list of upcoming episodes).
     */
    fun setQueue(context: Context, episodes: List<QueuedEpisode>) {
        val arr = org.json.JSONArray()
        episodes.forEach { ep ->
            arr.put(org.json.JSONObject().apply {
                put("videoId", ep.videoId)
                put("title", ep.title)
                put("providerName", ep.providerName)
                put("seasonNumber", ep.seasonNumber)
                put("episodeNumber", ep.episodeNumber)
                put("thumbnailUrl", ep.thumbnailUrl)
            })
        }
        getPrefs(context).edit { putString(KEY_QUEUE, arr.toString()) }
    }

    /**
     * Get the current episode queue.
     */
    fun getQueue(context: Context): List<QueuedEpisode> {
        val json = getPrefs(context).getString(KEY_QUEUE, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                QueuedEpisode(
                    videoId = obj.getString("videoId"),
                    title = obj.getString("title"),
                    providerName = obj.getString("providerName"),
                    seasonNumber = obj.getInt("seasonNumber"),
                    episodeNumber = obj.getInt("episodeNumber"),
                    thumbnailUrl = obj.optString("thumbnailUrl", null),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get the next episode in the queue.
     */
    fun getNextEpisode(context: Context): QueuedEpisode? {
        return getQueue(context).firstOrNull()
    }

    /**
     * Remove the first episode from the queue (after playing).
     */
    fun dequeue(context: Context) {
        val queue = getQueue(context).toMutableList()
        if (queue.isNotEmpty()) {
            queue.removeAt(0)
            setQueue(context, queue)
        }
    }

    /**
     * Check if auto-play next episode is enabled.
     */
    fun isAutoplayNextEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTOPLAY, true)
    }

    /**
     * Set auto-play next episode.
     */
    fun setAutoplayNext(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_AUTOPLAY, enabled) }
    }

    /**
     * Clear the queue.
     */
    fun clearQueue(context: Context) {
        getPrefs(context).edit { remove(KEY_QUEUE) }
    }
}
