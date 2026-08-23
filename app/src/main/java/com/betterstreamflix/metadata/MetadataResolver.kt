package com.betterstreamflix.metadata

import com.betterstreamflix.utils.UserPreferences

/**
 * Metadata resolver — orchestrates TMDB lookups with caching.
 * Tries cache first, then API, then stores result.
 */
object MetadataResolver {

    /**
     * Resolve metadata for a movie title.
     */
    suspend fun resolveMovie(title: String, year: Int? = null): TmdbMetadata? {
        val language = UserPreferences.appLanguage

        // Check cache
        MetadataCache.get(title, "movie")?.let { return it }
        if (MetadataCache.isCached(title, "movie")) return null

        // Fetch from TMDB
        val result = TmdbClient.searchMovie(title, year, language)
        val metadata = result?.let { TmdbClient.getMovieDetails(it.id, language) }

        // Cache result (including null for negative caching)
        MetadataCache.put(title, "movie", metadata)
        return metadata
    }

    /**
     * Resolve metadata for a TV show title.
     */
    suspend fun resolveTv(title: String, year: Int? = null): TmdbMetadata? {
        val language = UserPreferences.appLanguage

        MetadataCache.get(title, "tv")?.let { return it }
        if (MetadataCache.isCached(title, "tv")) return null

        val result = TmdbClient.searchTv(title, year, language)
        val metadata = result?.let { TmdbClient.getTvDetails(it.id, language) }

        MetadataCache.put(title, "tv", metadata)
        return metadata
    }

    /**
     * Resolve metadata by type.
     */
    suspend fun resolve(title: String, type: String, year: Int? = null): TmdbMetadata? {
        return if (type == "movie") resolveMovie(title, year) else resolveTv(title, year)
    }

    /**
     * Get poster URL for a metadata entry.
     */
    fun getPosterUrl(metadata: TmdbMetadata?, size: String = "w500"): String? {
        return metadata?.posterPath?.let { TmdbClient.getPosterUrl(it, size) }
    }

    /**
     * Get backdrop URL for a metadata entry.
     */
    fun getBackdropUrl(metadata: TmdbMetadata?, size: String = "original"): String? {
        return metadata?.backdropPath?.let { TmdbClient.getBackdropUrl(it, size) }
    }
}
