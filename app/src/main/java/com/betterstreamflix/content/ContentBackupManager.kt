package com.betterstreamflix.content

/**
 * Content backup manager — exports and imports user data
 * (favorites, watch history, settings) for backup/restore.
 */
object ContentBackupManager {

    data class BackupData(
        val version: Int,
        val timestamp: Long,
        val favorites: List<FavoriteBackup>,
        val watchHistory: List<HistoryBackup>,
        val settings: Map<String, Any?>,
    )

    data class FavoriteBackup(
        val videoId: String,
        val title: String,
        val providerName: String,
        val type: String,
    )

    data class HistoryBackup(
        val videoId: String,
        val title: String,
        val providerName: String,
        val watchedAt: Long,
        val progressPercent: Float,
    )

    /**
     * Create a backup from repository data.
     */
    suspend fun createBackup(
        favorites: List<com.betterstreamflix.database.dao.FavoriteEntity>,
        history: List<com.betterstreamflix.database.dao.WatchHistoryEntity>,
        settings: Map<String, Any?>,
    ): BackupData {
        return BackupData(
            version = 1,
            timestamp = System.currentTimeMillis(),
            favorites = favorites.map {
                FavoriteBackup(it.videoId, it.title, it.providerName, it.type)
            },
            watchHistory = history.map {
                HistoryBackup(it.videoId, it.title, it.providerName, it.watchedAt, it.progressPercent)
            },
            settings = settings,
        )
    }

    /**
     * Serialize backup to JSON string.
     */
    fun serializeBackup(backup: BackupData): String {
        val json = org.json.JSONObject()
        json.put("version", backup.version)
        json.put("timestamp", backup.timestamp)

        val favoritesArray = org.json.JSONArray()
        backup.favorites.forEach { fav ->
            favoritesArray.put(org.json.JSONObject().apply {
                put("videoId", fav.videoId)
                put("title", fav.title)
                put("providerName", fav.providerName)
                put("type", fav.type)
            })
        }
        json.put("favorites", favoritesArray)

        val historyArray = org.json.JSONArray()
        backup.watchHistory.forEach { hist ->
            historyArray.put(org.json.JSONObject().apply {
                put("videoId", hist.videoId)
                put("title", hist.title)
                put("providerName", hist.providerName)
                put("watchedAt", hist.watchedAt)
                put("progressPercent", hist.progressPercent)
            })
        }
        json.put("watchHistory", historyArray)

        val settingsObj = org.json.JSONObject(backup.settings)
        json.put("settings", settingsObj)

        return json.toString(2)
    }

    /**
     * Deserialize backup from JSON string.
     */
    fun deserializeBackup(json: String): BackupData? {
        return try {
            val obj = org.json.JSONObject(json)
            val favorites = (0 until obj.getJSONArray("favorites").length()).map { i ->
                val f = obj.getJSONArray("favorites").getJSONObject(i)
                FavoriteBackup(f.getString("videoId"), f.getString("title"), f.getString("providerName"), f.getString("type"))
            }
            val history = (0 until obj.getJSONArray("watchHistory").length()).map { i ->
                val h = obj.getJSONArray("watchHistory").getJSONObject(i)
                HistoryBackup(h.getString("videoId"), h.getString("title"), h.getString("providerName"), h.getLong("watchedAt"), h.getDouble("progressPercent").toFloat())
            }
            val settings = obj.getJSONObject("settings").toMap()
            BackupData(obj.getInt("version"), obj.getLong("timestamp"), favorites, history, settings)
        } catch (e: Exception) {
            null
        }
    }
}
