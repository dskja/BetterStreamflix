package com.streamflixreborn.streamflix.backup

import android.content.Context
import android.util.Log
import androidx.room.Transaction
import com.streamflixreborn.streamflix.database.dao.EpisodeDao
import com.streamflixreborn.streamflix.database.dao.MovieDao
import com.streamflixreborn.streamflix.database.dao.TvShowDao
import com.streamflixreborn.streamflix.database.dao.SeasonDao
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.WatchItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class ProviderBackupContext(
    val name: String,
    val movieDao: MovieDao,
    val tvShowDao: TvShowDao,
    val episodeDao: EpisodeDao,
    val seasonDao: SeasonDao // Aggiunto SeasonDao
)

class BackupRestoreManager(
    private val context: Context,
    private val providers: List<ProviderBackupContext>
) {
    private val TAG = "BackupVerify"

    fun exportUserData(): String? {
        return try {
            val root = JSONObject()
            root.put("version", 4) // Versione aumentata per includere le stagioni
            root.put("exportedAt", System.currentTimeMillis())

            val providersArray = JSONArray()
            for (p in providers) {
                // Controlla se il database per questo provider ha dati rilevanti
                val moviesToExport = p.movieDao.getAll()
                    .filter { it.isWatched || it.watchedDate != null || it.watchHistory != null || it.isFavorite }
                val tvShowsToExport = p.tvShowDao.getAllForBackup()
                    .filter { it.isWatching || it.isFavorite }
                val episodesToExport = p.episodeDao.getAllForBackup()
                    .filter { it.isWatched || it.watchedDate != null || it.watchHistory != null }
                
                // Se non ci sono dati, saltiamo il provider per tenere il file pulito.
                if (moviesToExport.isEmpty() && tvShowsToExport.isEmpty() && episodesToExport.isEmpty()) {
                    continue
                }

                val providerObj = JSONObject()
                providerObj.put("name", p.name)

                // Movies
                val moviesArray = JSONArray()
                moviesToExport.forEach { movie ->
                    val obj = JSONObject().apply {
                        put("id", movie.id)
                        put("title", movie.title)
                        put("poster", movie.poster)
                        put("banner", movie.banner)
                        put("isFavorite", movie.isFavorite)
                        put("isWatched", movie.isWatched)
                        put("watchedDate", movie.watchedDate?.timeInMillis ?: JSONObject.NULL)
                        put("watchHistory", movie.watchHistory?.toJson() ?: JSONObject.NULL)
                    }
                    moviesArray.put(obj)
                    Log.d(TAG, "EXPORT: [${p.name}] Movie: ${movie.title} (Fav: ${movie.isFavorite})")
                }
                providerObj.put("movies", moviesArray)

                // TV Shows
                val tvShowsArray = JSONArray()
                tvShowsToExport.forEach { show ->
                    val obj = JSONObject().apply {
                        put("id", show.id)
                        put("title", show.title)
                        put("poster", show.poster)
                        put("banner", show.banner)
                        put("isFavorite", show.isFavorite)
                        put("isWatching", show.isWatching)
                    }
                    tvShowsArray.put(obj)
                    Log.d(TAG, "EXPORT: [${p.name}] TV Show: ${show.title} (Fav: ${show.isFavorite})")
                }
                providerObj.put("tvShows", tvShowsArray)

                // Seasons
                val seasonsArray = JSONArray()
                p.seasonDao.getAllForBackup()
                    .forEach { season ->
                        val obj = JSONObject().apply {
                            put("id", season.id)
                            put("number", season.number)
                            put("title", season.title)
                            put("poster", season.poster)
                            put("tvShowId", season.tvShow?.id)
                        }
                        seasonsArray.put(obj)
                    }
                providerObj.put("seasons", seasonsArray)

                // Episodes
                val episodesArray = JSONArray()
                episodesToExport.forEach { ep ->
                    val obj = JSONObject().apply {
                        put("id", ep.id)
                        put("number", ep.number)
                        put("title", ep.title)
                        put("poster", ep.poster)
                        put("tvShowId", ep.tvShow?.id)
                        put("seasonId", ep.season?.id)
                        put("isWatched", ep.isWatched)
                        put("watchedDate", ep.watchedDate?.timeInMillis ?: JSONObject.NULL)
                        put("watchHistory", ep.watchHistory?.toJson() ?: JSONObject.NULL)
                    }
                    episodesArray.put(obj)
                    Log.d(TAG, "EXPORT: [${p.name}] Episode: ${ep.title} (Watched: ${ep.isWatched})")
                }
                providerObj.put("episodes", episodesArray)

                providersArray.put(providerObj)
            }

            root.put("providers", providersArray)
            Log.d(TAG, "Export successful for version 4. Total providers exported: ${providersArray.length()}")
            root.toString()
        } catch (t: Throwable) {
            Log.e(TAG, "Error during exportUserData", t)
            null
        }
    }


    // Eseguo l'intera operazione di importazione come transazione per l'atomicità
    @Transaction
    fun importUserData(json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            val providersArray = obj.optJSONArray("providers") ?: return false
            val backupVersion = obj.optInt("version", 1)

            Log.d(TAG, "Starting import from version $backupVersion for ${providersArray.length()} providers")

            for (i in 0 until providersArray.length()) {
                val providerObj = providersArray.optJSONObject(i) ?: continue
                val providerName = providerObj.optString("name") ?: continue
                val providerCtx = providers.find { it.name == providerName } ?: run {
                    Log.w(TAG, "Provider '$providerName' not found in current providers. Skipping...")
                    continue
                }

                // 1. Import Seasons (NUOVO)
                providerObj.optJSONArray("seasons")?.let { arr ->
                    val seasonsToSave = mutableListOf<Season>()
                    for (j in 0 until arr.length()) {
                        val s = arr.optJSONObject(j) ?: continue
                        val season = Season(
                            id = s.optString("id", ""),
                            number = s.optInt("number", 0)
                        ).apply {
                            title = s.optStringOrNull("title")
                            poster = s.optStringOrNull("poster")
                            s.optStringOrNull("tvShowId")?.let { tvId -> tvShow = TvShow(tvId, "") }
                        }
                        seasonsToSave.add(season)
                    }
                    if (seasonsToSave.isNotEmpty()) {
                        providerCtx.seasonDao.saveAll(seasonsToSave)
                        Log.d(TAG, "IMPORT: Imported ${seasonsToSave.size} seasons for provider $providerName")
                    }
                }

                // 2. Import TV Shows
                providerObj.optJSONArray("tvShows")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        val s = arr.optJSONObject(j) ?: continue
                        val isFavorite = s.optBoolean("isFavorite", false)
                        val isWatching = s.optBoolean("isWatching", false) // Corretto il default a false

                        val tvShow = TvShow(
                            id = s.optString("id", ""),
                            title = s.optString("title", "")
                        ).apply {
                            poster = s.optStringOrNull("poster")
                            banner = s.optStringOrNull("banner")
                            this.isFavorite = isFavorite
                            this.isWatching = isWatching
                        }
                        providerCtx.tvShowDao.save(tvShow)
                        Log.d(TAG, "IMPORT: [${providerName}] TV Show: ${tvShow.title}. Favorites: $isFavorite, Watching: $isWatching")
                    }
                }

                // 3. Import Movies
                providerObj.optJSONArray("movies")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        val m = arr.optJSONObject(j) ?: continue
                        val isFavorite = m.optBoolean("isFavorite", false)
                        val isWatched = m.optBoolean("isWatched", false)
                        val watchedDate = m.optLongOrNull("watchedDate")?.toCalendar()
                        val watchHistory = m.optJSONObject("watchHistory")?.toWatchHistory()
                        
                        val movie = Movie(
                            id = m.optString("id", ""),
                            title = m.optString("title", "")
                        ).apply {
                            poster = m.optStringOrNull("poster")
                            banner = m.optStringOrNull("banner")
                            this.isFavorite = isFavorite
                            this.isWatched = isWatched
                            this.watchedDate = watchedDate
                            this.watchHistory = watchHistory
                        }
                        providerCtx.movieDao.save(movie)
                        Log.d(TAG, "IMPORT: [${providerName}] Movie: ${movie.title}. Favorites: $isFavorite, Watched: $isWatched, History: ${watchHistory != null}")
                    }
                }

                // 4. Import Episodes
                providerObj.optJSONArray("episodes")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        val e = arr.optJSONObject(j) ?: continue
                        val isWatched = e.optBoolean("isWatched", false)
                        val watchedDate = e.optLongOrNull("watchedDate")?.toCalendar()
                        val watchHistory = e.optJSONObject("watchHistory")?.toWatchHistory()

                        val ep = Episode(id = e.optString("id", "")).apply {
                            number = e.optInt("number", 0)
                            title = e.optStringOrNull("title")
                            poster = e.optStringOrNull("poster")
                            e.optStringOrNull("tvShowId")?.let { tvId -> tvShow = TvShow(tvId, "") }
                            e.optStringOrNull("seasonId")?.let { sId -> season = Season(sId, 0) }
                            this.isWatched = isWatched
                            this.watchedDate = watchedDate
                            this.watchHistory = watchHistory
                        }
                        providerCtx.episodeDao.save(ep)
                        Log.d(TAG, "IMPORT: [${providerName}] Episode: ${ep.title}. Watched: $isWatched, History: ${watchHistory != null}")
                    }
                }
            }

            Log.d(TAG, "Import completed successfully")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Error during importUserData", t)
            false
        }
    }


}
private fun Long.toCalendar(): Calendar = Calendar.getInstance().apply { timeInMillis = this@toCalendar }

private fun WatchItem.WatchHistory.toJson(): JSONObject =
    JSONObject().apply {
        put("lastEngagementTimeUtcMillis", lastEngagementTimeUtcMillis)
        put("lastPlaybackPositionMillis", lastPlaybackPositionMillis)
        put("durationMillis", durationMillis)
    }

private fun JSONObject.toWatchHistory(): WatchItem.WatchHistory =
    WatchItem.WatchHistory(
        optLong("lastEngagementTimeUtcMillis", 0L),
        optLong("lastPlaybackPositionMillis", 0L),
        optLong("durationMillis", 0L)
    )


private fun JSONObject.optLongOrNull(name: String): Long? {
    return if (has(name) && !isNull(name)) optLong(name) else null
}

private fun JSONObject.optStringOrNull(name: String): String? {
    return if (has(name) && !isNull(name)) optString(name) else null
}
