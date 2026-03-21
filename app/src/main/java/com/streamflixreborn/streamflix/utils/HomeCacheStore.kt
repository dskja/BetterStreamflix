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
import java.io.File

object HomeCacheStore {
    private val gson = Gson()

    fun read(context: Context, providerName: String): List<Category>? {
        val file = cacheFile(context, providerName)
        if (!file.exists()) return null

        return runCatching {
            val type = object : TypeToken<List<CachedCategory>>() {}.type
            val payload: List<CachedCategory> = gson.fromJson(file.readText(), type)
            payload.mapNotNull { it.toCategoryOrNull() }
        }.recoverCatching {
            if (it is JsonSyntaxException) {
                file.delete()
            }
            null
        }.getOrNull()
    }

    fun write(context: Context, providerName: String, categories: List<Category>) {
        runCatching {
            val payload = categories.map { CachedCategory.from(it) }
            cacheFile(context, providerName).apply {
                parentFile?.mkdirs()
                writeText(gson.toJson(payload))
            }
        }
    }

    fun clear(context: Context, providerName: String) {
        cacheFile(context, providerName).delete()
    }

    private fun cacheFile(context: Context, providerName: String): File {
        val safeName = providerName.replace(Regex("[^a-zA-Z0-9._-]+"), "_")
        return File(context.cacheDir, "home-cache/$safeName.json")
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
    ) {
        fun toItemOrNull(): AppAdapter.Item? {
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
                )

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
                )

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
                    )

                    else -> null
                }
            }
        }
    }
}
