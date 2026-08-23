package com.betterstreamflix.metadata

/**
 * Image URL helper — builds optimized image URLs for different display contexts.
 */
object ImageUrlHelper {

    /**
     * Get the optimal image size based on the display context.
     */
    fun getOptimalPosterSize(contextWidth: Int, isTv: Boolean): String {
        return when {
            isTv -> "w780"
            contextWidth > 1080 -> "w780"
            contextWidth > 720 -> "w500"
            else -> "w342"
        }
    }

    /**
     * Get the optimal backdrop size.
     */
    fun getOptimalBackdropSize(contextWidth: Int, isTv: Boolean): String {
        return when {
            isTv -> "original"
            contextWidth > 1080 -> "w1280"
            contextWidth > 720 -> "w780"
            else -> "w500"
        }
    }

    /**
     * Build a complete poster URL from a TMDB path.
     */
    fun buildPosterUrl(path: String?, contextWidth: Int = 720, isTv: Boolean = false): String? {
        if (path == null) return null
        val size = getOptimalPosterSize(contextWidth, isTv)
        return TmdbClient.getPosterUrl(path, size)
    }

    /**
     * Build a complete backdrop URL from a TMDB path.
     */
    fun buildBackdropUrl(path: String?, contextWidth: Int = 720, isTv: Boolean = false): String? {
        if (path == null) return null
        val size = getOptimalBackdropSize(contextWidth, isTv)
        return TmdbClient.getBackdropUrl(path, size)
    }

    /**
     * Check if a URL is a TMDB image URL.
     */
    fun isTmdbImageUrl(url: String): Boolean {
        return url.contains("image.tmdb.org")
    }
}
