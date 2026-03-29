package com.streamflixreborn.streamflix.utils

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bumptech.glide.load.engine.GlideException
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.providers.AniWorldProvider
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.providers.SerienStreamProvider
import java.io.FileNotFoundException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ArtworkRepair {

    private const val TAG = "ArtworkRepair"
    const val KEY_PROVIDER_NAME = "provider_name"
    private val dbWriteMutex = Mutex()

    fun shouldRepair(url: String?, error: GlideException?): Boolean {
        if (url.isNullOrBlank()) return false
        return !isRemoteArtworkUrl(url) || containsFileNotFound(error)
    }

    fun isRemoteArtworkUrl(url: String?): Boolean {
        return !url.isNullOrBlank() && (url.startsWith("https://") || url.startsWith("http://"))
    }

    suspend fun resolveMovieForFavorite(context: Context, movie: Movie, favorite: Boolean): Movie {
        if (!favorite || hasUsableArtwork(movie.poster, movie.banner)) return movie
        val provider = UserPreferences.currentProvider ?: return movie
        val database = AppDatabase.getInstance(context)
        return repairMovie(context, provider, database, movie, persistResult = false) ?: movie
    }

    suspend fun resolveTvShowForFavorite(context: Context, tvShow: TvShow, favorite: Boolean): TvShow {
        if (!favorite || hasUsableArtwork(tvShow.poster, tvShow.banner)) return tvShow
        val provider = UserPreferences.currentProvider ?: return tvShow
        val database = AppDatabase.getInstance(context)
        return repairTvShow(context, provider, database, tvShow, persistResult = false) ?: tvShow
    }

    suspend fun repairMovie(
        context: Context,
        provider: Provider,
        database: AppDatabase,
        movie: Movie,
        persistResult: Boolean = true,
    ): Movie? {
        return runCatching {
            prepareProvider(context, provider)
            val refreshedMovie = provider.getMovie(movie.id).also { fetchedMovie ->
                applyTmdbFallbackToMovie(
                    currentMovie = fetchedMovie,
                    fallbackTitle = movie.title,
                    providerLanguage = provider.language,
                )
            }
            if (persistResult) {
                val repairDatabase = AppDatabase.getInstanceForProvider(provider.name, context)
                try {
                    dbWriteMutex.withLock {
                        repairDatabase.movieDao().getById(movie.id)?.let { refreshedMovie.merge(it) }
                        repairDatabase.movieDao().insert(refreshedMovie)
                    }
                } finally {
                    repairDatabase.close()
                }
            }
            refreshedMovie
        }.onFailure { error ->
            Log.w(TAG, "Unable to refresh movie artwork for ${movie.id} on ${provider.name}", error)
        }.getOrNull()
    }

    suspend fun repairTvShow(
        context: Context,
        provider: Provider,
        database: AppDatabase,
        tvShow: TvShow,
        persistResult: Boolean = true,
    ): TvShow? {
        return runCatching {
            prepareProvider(context, provider)
            val refreshedTvShow = provider.getTvShow(tvShow.id).also { fetchedTvShow ->
                applyTmdbFallbackToTvShow(
                    currentTvShow = fetchedTvShow,
                    fallbackTitle = tvShow.title,
                    providerLanguage = provider.language,
                )
            }
            if (persistResult) {
                val repairDatabase = AppDatabase.getInstanceForProvider(provider.name, context)
                try {
                    dbWriteMutex.withLock {
                        repairDatabase.tvShowDao().getById(tvShow.id)?.let { refreshedTvShow.merge(it) }
                        repairDatabase.tvShowDao().insert(refreshedTvShow)
                    }
                } finally {
                    repairDatabase.close()
                }
            }
            refreshedTvShow
        }.onFailure { error ->
            Log.w(TAG, "Unable to refresh tv show artwork for ${tvShow.id} on ${provider.name}", error)
        }.getOrNull()
    }

    suspend fun repairStoredArtwork(
        context: Context,
        provider: Provider,
        database: AppDatabase,
    ) {
        prepareProvider(context, provider)

        database.episodeDao()
            .getArtworkRepairTvShowIds()
            .distinct()
            .forEach { tvShowId ->
                val existingTvShow = database.tvShowDao().getById(tvShowId)
                val missingArtwork = existingTvShow == null ||
                    !isRemoteArtworkUrl(existingTvShow.poster) ||
                    existingTvShow.banner.isNullOrBlank() ||
                    !isRemoteArtworkUrl(existingTvShow.banner)

                if (missingArtwork) {
                    repairTvShow(
                        context = context,
                        provider = provider,
                        database = database,
                        tvShow = existingTvShow ?: TvShow(id = tvShowId, title = ""),
                    )
                }
            }

        database.movieDao()
            .getArtworkRepairCandidates()
            .distinctBy { it.id }
            .forEach { movie ->
                repairMovie(context, provider, database, movie)
            }

        database.tvShowDao()
            .getArtworkRepairCandidates()
            .distinctBy { it.id }
            .forEach { tvShow ->
                repairTvShow(context, provider, database, tvShow)
            }
    }

    private suspend fun prepareProvider(context: Context, provider: Provider) {
        when (provider) {
            SerienStreamProvider -> SerienStreamProvider.initialize(context)
            AniWorldProvider -> AniWorldProvider.initialize(context)
        }
    }

    private fun hasUsableArtwork(poster: String?, banner: String?): Boolean {
        return isRemoteArtworkUrl(poster) && (banner.isNullOrBlank() || isRemoteArtworkUrl(banner))
    }

    private suspend fun applyTmdbFallbackToMovie(
        currentMovie: Movie,
        fallbackTitle: String?,
        providerLanguage: String?,
    ) {
        if (hasUsableArtwork(currentMovie.poster, currentMovie.banner)) return

        val lookupTitle = currentMovie.title.ifBlank { fallbackTitle.orEmpty() }
        if (lookupTitle.isBlank()) return

        val tmdbMovie = TmdbUtils.getMovie(lookupTitle, language = providerLanguage) ?: return
        if (!isRemoteArtworkUrl(currentMovie.poster) && isRemoteArtworkUrl(tmdbMovie.poster)) {
            currentMovie.poster = tmdbMovie.poster
        }
        if (!isRemoteArtworkUrl(currentMovie.banner) && isRemoteArtworkUrl(tmdbMovie.banner)) {
            currentMovie.banner = tmdbMovie.banner
        }
        if (currentMovie.imdbId.isNullOrBlank()) {
            currentMovie.imdbId = tmdbMovie.imdbId
        }
    }

    private suspend fun applyTmdbFallbackToTvShow(
        currentTvShow: TvShow,
        fallbackTitle: String?,
        providerLanguage: String?,
    ) {
        if (hasUsableArtwork(currentTvShow.poster, currentTvShow.banner)) return

        val lookupTitle = currentTvShow.title.ifBlank { fallbackTitle.orEmpty() }
        if (lookupTitle.isBlank()) return

        val tmdbTvShow = TmdbUtils.getTvShow(lookupTitle, language = providerLanguage) ?: return
        if (!isRemoteArtworkUrl(currentTvShow.poster) && isRemoteArtworkUrl(tmdbTvShow.poster)) {
            currentTvShow.poster = tmdbTvShow.poster
        }
        if (!isRemoteArtworkUrl(currentTvShow.banner) && isRemoteArtworkUrl(tmdbTvShow.banner)) {
            currentTvShow.banner = tmdbTvShow.banner
        }
        if (currentTvShow.imdbId.isNullOrBlank()) {
            currentTvShow.imdbId = tmdbTvShow.imdbId
        }
    }

    private fun containsFileNotFound(error: GlideException?): Boolean {
        if (error == null) return false
        if (generateSequence(error.cause) { it.cause }.any { it is FileNotFoundException }) return true
        return error.rootCauses.any { root ->
            root is FileNotFoundException || generateSequence(root.cause) { it.cause }.any { it is FileNotFoundException }
        }
    }
}

object ArtworkRepairScheduler {

    fun schedule(context: Context, provider: Provider?) {
        provider ?: return
        schedule(context, provider.name)
    }

    fun schedule(context: Context, providerName: String?) {
        if (providerName.isNullOrBlank()) return

        val request = OneTimeWorkRequestBuilder<ArtworkRepairWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                androidx.work.workDataOf(ArtworkRepair.KEY_PROVIDER_NAME to providerName)
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "repair_artwork_${sanitize(providerName)}",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun sanitize(name: String): String {
        return name.lowercase()
            .replace("[^a-z0-9]".toRegex(), "_")
            .replace("__+".toRegex(), "_")
            .trim('_')
    }
}

class ArtworkRepairWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val providerName = inputData.getString(ArtworkRepair.KEY_PROVIDER_NAME)
            ?: return Result.success()
        val provider = Provider.findByName(providerName) ?: return Result.success()
        val database = AppDatabase.getInstanceForProvider(provider.name, applicationContext)

        return try {
            ArtworkRepair.repairStoredArtwork(applicationContext, provider, database)
            Result.success()
        } catch (error: Exception) {
            Log.w("ArtworkRepairWorker", "Unable to repair artwork for ${provider.name}", error)
            Result.retry()
        } finally {
            database.close()
        }
    }
}
