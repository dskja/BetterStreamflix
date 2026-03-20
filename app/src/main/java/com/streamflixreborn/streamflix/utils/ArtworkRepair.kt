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

object ArtworkRepair {

    private const val TAG = "ArtworkRepair"
    const val KEY_PROVIDER_NAME = "provider_name"

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
        return repairMovie(context, provider, database, movie) ?: movie
    }

    suspend fun resolveTvShowForFavorite(context: Context, tvShow: TvShow, favorite: Boolean): TvShow {
        if (!favorite || hasUsableArtwork(tvShow.poster, tvShow.banner)) return tvShow
        val provider = UserPreferences.currentProvider ?: return tvShow
        val database = AppDatabase.getInstance(context)
        return repairTvShow(context, provider, database, tvShow) ?: tvShow
    }

    suspend fun repairMovie(
        context: Context,
        provider: Provider,
        database: AppDatabase,
        movie: Movie,
    ): Movie? {
        return runCatching {
            prepareProvider(context, provider)
            val refreshedMovie = provider.getMovie(movie.id)
            database.movieDao().getById(movie.id)?.let { refreshedMovie.merge(it) }
            database.movieDao().insert(refreshedMovie)
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
    ): TvShow? {
        return runCatching {
            prepareProvider(context, provider)
            val refreshedTvShow = provider.getTvShow(tvShow.id)
            database.tvShowDao().getById(tvShow.id)?.let { refreshedTvShow.merge(it) }
            database.tvShowDao().insert(refreshedTvShow)
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
