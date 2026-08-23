package com.betterstreamflix.metadata

/**
 * Genre manager — maps provider categories to standardized genres
 * and provides genre-based browsing.
 */
object GenreManager {

    /**
     * Standardized genre list.
     */
    val STANDARD_GENRES = listOf(
        "Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary",
        "Drama", "Family", "Fantasy", "History", "Horror", "Music",
        "Mystery", "Romance", "Science Fiction", "Thriller", "War", "Western",
    )

    /**
     * Map provider-specific category names to standard genres.
     */
    private val genreMapping = mapOf(
        "Action" to "Action",
        "Aktion" to "Action",
        "Abenteuer" to "Adventure",
        "Adventure" to "Adventure",
        "Animation" to "Animation",
        "Anime" to "Animation",
        "Komödie" to "Comedy",
        "Comedy" to "Comedy",
        "Krimi" to "Crime",
        "Crime" to "Crime",
        "Dokumentation" to "Documentary",
        "Documentary" to "Documentary",
        "Drama" to "Drama",
        "Familie" to "Family",
        "Family" to "Family",
        "Fantasy" to "Fantasy",
        "Fantasie" to "Fantasy",
        "Historie" to "History",
        "History" to "History",
        "Horror" to "Horror",
        "Grusel" to "Horror",
        "Musik" to "Music",
        "Music" to "Music",
        "Mystery" to "Mystery",
        "Romantik" to "Romance",
        "Romance" to "Romance",
        "Sci-Fi" to "Science Fiction",
        "Science Fiction" to "Science Fiction",
        "SF" to "Science Fiction",
        "Thriller" to "Thriller",
        "Krieg" to "War",
        "War" to "War",
        "Western" to "Western",
    )

    /**
     * Normalize a provider category to a standard genre.
     */
    fun normalizeGenre(categoryName: String): String? {
        return genreMapping[categoryName.trim()]
            ?: genreMapping.entries.firstOrNull { it.key.equals(categoryName, ignoreCase = true) }?.value
    }

    /**
     * Get all unique genres from a list of category names.
     */
    fun extractGenres(categories: List<String>): List<String> {
        return categories.mapNotNull { normalizeGenre(it) }.distinct().sorted()
    }
}
