package com.streamflixreborn.streamflix.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.WatchItem
import com.streamflixreborn.streamflix.providers.Provider
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object HomeCacheStore {
    private val gson = Gson()
    private val memoryCache = ConcurrentHashMap<String, List<CachedCategory>>()

    fun read(context: Context, provider: Provider): List<Category>? {
        val cacheKey = cacheKey(provider)
        memoryCache[cacheKey]?.let { payload ->
            return payload.toCategories()
        }

        val file = cacheFile(context, cacheKey)
        if (!file.exists()) return null

        return runCatching {
            val type = object : TypeToken<List<CachedCategory>>() {}.type
            val payload: List<CachedCategory> = gson.fromJson(file.readText(), type)
            memoryCache[cacheKey] = payload
            payload.toCategories()
        }.recoverCatching {
            if (it is JsonSyntaxException) {
                memoryCache.remove(cacheKey)
                file.delete()
            }
            null
        }.getOrNull()
    }

    fun write(context: Context, provider: Provider, categories: List<Category>) {
        runCatching {
            val payload = categories.map { CachedCategory.from(it) }
            val cacheKey = cacheKey(provider)
            memoryCache[cacheKey] = payload
            cacheFile(context, cacheKey).apply {
                parentFile?.mkdirs()
                writeText(gson.toJson(payload))
            }
        }
    }

    fun clear(context: Context, provider: Provider) {
        val cacheKey = cacheKey(provider)
        memoryCache.remove(cacheKey)
        cacheFile(context, cacheKey).delete()
    }

    private fun cacheFile(context: Context, cacheKey: String): File {
        val safeName = cacheKey.replace(Regex("[^a-zA-Z0-9._-]+"), "_")
        return File(context.cacheDir, "home-cache/$safeName.json")
    }

    private fun cacheKey(provider: Provider): String {
        val baseUrlKey = provider.baseUrl.trim().trimEnd('/')
        return buildList {
            add(provider.name)
            if (baseUrlKey.isNotEmpty()) {
                add(baseUrlKey)
            }
        }.joinToString("__")
    }

    private fun List<CachedCategory>.toCategories(): List<Category> {
        return mapNotNull { it.toCategoryOrNull() }
    }

    private data class CachedCategory(
        val name: String,
        val list: List<CachedItem>,
    ) {
        fun toCategoryOrNull(): Category? {
            val items = list.mapNotNull { it.toItemOrNull() }
            return Category(name = name, list = items)
        }

        companion object {
            fun from(category: Category): CachedCategory {
                return CachedCategory(
                    name = category.name,
                    list = category.list.mapNotNull(CachedItem::from)
                )
            }
        }
    }

    private data class CachedItem(
        val type: String,
        val id: String,
        val title: String? = null,
        val overview: String? = null,
        val released: String? = null,
        val runtime: Int? = null,
        val trailer: String? = null,
        val quality: String? = null,
        val rating: Double? = null,
        val poster: String? = null,
        val banner: String? = null,
        val episodeNumber: Int? = null,
        val tvShowId: String? = null,
        val tvShowTitle: String? = null,
        val tvShowPoster: String? = null,
        val tvShowBanner: String? = null,
        val seasonId: String? = null,
        val seasonNumber: Int? = null,
        val seasonTitle: String? = null,
        val seasonPoster: String? = null,
        val lastPlaybackPositionMillis: Long? = null,
        val durationMillis: Long? = null,
        val lastEngagementTimeUtcMillis: Long? = null,
    ) {
        fun toItemOrNull(): AppAdapter.Item? {
            val watchHistory =
                if (
                    lastPlaybackPositionMillis != null &&
                    durationMillis != null &&
                    durationMillis > 0 &&
                    lastEngagementTimeUtcMillis != null
                ) {
                    WatchItem.WatchHistory(
                        lastEngagementTimeUtcMillis = lastEngagementTimeUtcMillis,
                        lastPlaybackPositionMillis = lastPlaybackPositionMillis,
                        durationMillis = durationMillis,
                    )
                } else null
            return when (type) {
                "movie" -> Movie(
                    id = id,
                    title = title.orEmpty(),
                    overview = overview,
                    released = released,
                    runtime = runtime,
                    trailer = trailer,
                    quality = quality,
                    rating = rating,
                    poster = poster,
                    banner = banner,
                ).apply {
                    this.watchHistory = watchHistory
                }

                "tv" -> TvShow(
                    id = id,
                    title = title.orEmpty(),
                    overview = overview,
                    released = released,
                    runtime = runtime,
                    trailer = trailer,
                    quality = quality,
                    rating = rating,
                    poster = poster,
                    banner = banner,
                )

                "episode" -> Episode(
                    id = id,
                    number = episodeNumber ?: 0,
                    title = title,
                    released = released,
                    poster = poster,
                    overview = overview,
                    tvShow = tvShowId?.let {
                        TvShow(
                            id = it,
                            title = tvShowTitle.orEmpty(),
                            poster = tvShowPoster,
                            banner = tvShowBanner,
                        )
                    },
                    season = seasonId?.let {
                        Season(
                            id = it,
                            number = seasonNumber ?: 0,
                            title = seasonTitle.orEmpty(),
                            poster = seasonPoster,
                        )
                    }
                ).apply {
                    this.watchHistory = watchHistory
                }

                else -> null
            }
        }

        companion object {
            fun from(item: AppAdapter.Item): CachedItem? {
                return when (item) {
                    is Movie -> CachedItem(
                        type = "movie",
                        id = item.id,
                        title = item.title,
                        overview = item.overview,
                        released = item.released?.format("yyyy-MM-dd"),
                        runtime = item.runtime,
                        trailer = item.trailer,
                        quality = item.quality,
                        rating = item.rating,
                        poster = item.poster,
                        banner = item.banner,
                        lastPlaybackPositionMillis = item.watchHistory?.lastPlaybackPositionMillis,
                        durationMillis = item.watchHistory?.durationMillis,
                        lastEngagementTimeUtcMillis = item.watchHistory?.lastEngagementTimeUtcMillis,
                    )

                    is TvShow -> CachedItem(
                        type = "tv",
                        id = item.id,
                        title = item.title,
                        overview = item.overview,
                        released = item.released?.format("yyyy-MM-dd"),
                        runtime = item.runtime,
                        trailer = item.trailer,
                        quality = item.quality,
                        rating = item.rating,
                        poster = item.poster,
                        banner = item.banner,
                    )

                    is Episode -> CachedItem(
                        type = "episode",
                        id = item.id,
                        title = item.title,
                        overview = item.overview,
                        released = item.released?.format("yyyy-MM-dd"),
                        poster = item.poster,
                        episodeNumber = item.number,
                        tvShowId = item.tvShow?.id,
                        tvShowTitle = item.tvShow?.title,
                        tvShowPoster = item.tvShow?.poster,
                        tvShowBanner = item.tvShow?.banner,
                        seasonId = item.season?.id,
                        seasonNumber = item.season?.number,
                        seasonTitle = item.season?.title,
                        seasonPoster = item.season?.poster,
                        lastPlaybackPositionMillis = item.watchHistory?.lastPlaybackPositionMillis,
                        durationMillis = item.watchHistory?.durationMillis,
                        lastEngagementTimeUtcMillis = item.watchHistory?.lastEngagementTimeUtcMillis,
                    )

                    else -> null
                }
            }
        }
    }
}
