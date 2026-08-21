package com.betterstreamflix.sync

import android.content.Context
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.providers.Provider

object CloudSyncHooks {
    fun movie(context: Context, provider: Provider, movie: Movie) {
        enqueue(context) { userId, now ->
            RemoteMediaState.fromMovie(userId, provider.name, movie, now)
        }
    }

    fun movie(context: Context, provider: Provider, id: String) {
        if (CloudSyncManager.isApplyingRemote) return
        val movie = runCatching { AppDatabase.getInstance(context).movieDao().getById(id) }.getOrNull()
            ?: Movie(id = id)
        movie(context, provider, movie)
    }

    fun tvShow(context: Context, provider: Provider, show: TvShow) {
        enqueue(context) { userId, now ->
            RemoteMediaState.fromTvShow(userId, provider.name, show, now)
        }
    }

    fun tvShow(context: Context, provider: Provider, id: String) {
        if (CloudSyncManager.isApplyingRemote) return
        val show = runCatching { AppDatabase.getInstance(context).tvShowDao().getById(id) }.getOrNull()
            ?: TvShow(id = id)
        tvShow(context, provider, show)
    }

    fun episode(context: Context, provider: Provider, episode: Episode) {
        enqueue(context) { userId, now ->
            RemoteMediaState.fromEpisode(userId, provider.name, episode, now)
        }
    }

    fun episode(context: Context, provider: Provider, id: String) {
        if (CloudSyncManager.isApplyingRemote) return
        val episode = runCatching { AppDatabase.getInstance(context).episodeDao().getById(id) }.getOrNull()
            ?: Episode(id = id)
        episode(context, provider, episode)
    }

    private inline fun enqueue(
        context: Context,
        state: (userId: String, now: Long) -> RemoteMediaState,
    ) {
        if (CloudSyncManager.isApplyingRemote) return
        val userId = CloudSyncManager.currentUserId() ?: return
        CloudMutationStore.enqueue(context.applicationContext, state(userId, System.currentTimeMillis()))
        CloudSyncScheduler.enqueue(context)
    }
}
