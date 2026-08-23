package com.betterstreamflix.metadata

/**
 * Content rating helper — maps provider-specific ratings to standard ratings.
 */
object ContentRatingHelper {

    /**
     * Standard content ratings.
     */
    enum class ContentRating(val display: String, val minAge: Int) {
        G("G", 0),
        PG("PG", 7),
        PG_13("PG-13", 13),
        R("R", 17),
        NC_17("NC-17", 18),
        TV_Y("TV-Y", 0),
        TV_Y7("TV-Y7", 7),
        TV_PG("TV-PG", 10),
        TV_14("TV-14", 14),
        TV_MA("TV-MA", 17),
        FSK_0("FSK 0", 0),
        FSK_6("FSK 6", 6),
        FSK_12("FSK 12", 12),
        FSK_16("FSK 16", 16),
        FSK_18("FSK 18", 18),
        UNRATED("Unrated", 0),
    }

    /**
     * Parse a rating string to ContentRating.
     */
    fun parse(rating: String?): ContentRating {
        if (rating == null) return ContentRating.UNRATED
        val upper = rating.uppercase().trim()
        return when {
            upper.startsWith("FSK") -> {
                when (upper.filter { it.isDigit() }.toIntOrNull()) {
                    0 -> ContentRating.FSK_0
                    6 -> ContentRating.FSK_6
                    12 -> ContentRating.FSK_12
                    16 -> ContentRating.FSK_16
                    18 -> ContentRating.FSK_18
                    else -> ContentRating.UNRATED
                }
            }
            upper == "G" -> ContentRating.G
            upper == "PG" -> ContentRating.PG
            upper == "PG-13" || upper == "PG13" -> ContentRating.PG_13
            upper == "R" -> ContentRating.R
            upper == "NC-17" || upper == "NC17" -> ContentRating.NC_17
            upper == "TV-Y" -> ContentRating.TV_Y
            upper == "TV-Y7" -> ContentRating.TV_Y7
            upper == "TV-PG" -> ContentRating.TV_PG
            upper == "TV-14" -> ContentRating.TV_14
            upper == "TV-MA" -> ContentRating.TV_MA
            else -> ContentRating.UNRATED
        }
    }

    /**
     * Check if content is appropriate for a given age.
     */
    fun isAppropriate(rating: ContentRating, age: Int): Boolean {
        return age >= rating.minAge
    }
}
