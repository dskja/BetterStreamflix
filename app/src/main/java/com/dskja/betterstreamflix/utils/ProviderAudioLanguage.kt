package com.dskja.betterstreamflix.utils

/**
 * Maps a provider language code to ExoPlayer preferred audio language tags.
 * Aggregator streams often use ISO-639-1, ISO-639-2/B, or ISO-639-2/T codes.
 */
object ProviderAudioLanguage {

    /** Normalize TMDb language tokens from prefs/display names to ISO codes. */
    fun normalizeTmdbLanguage(raw: String): String {
        val value = raw.trim().lowercase()
        return when {
            value == "en" || value.startsWith("en-") || value == "english" || value == "anglais" -> "en"
            value == "fr" || value.startsWith("fr-") || value == "french" ||
                value == "français" || value == "francais" -> "fr"
            value == "es" || value.startsWith("es-") || value == "spanish" ||
                value == "español" || value == "espanol" -> "es"
            value == "it" || value.startsWith("it-") || value == "italian" || value == "italiano" -> "it"
            value == "de" || value.startsWith("de-") || value == "german" || value == "deutsch" -> "de"
            else -> value.substringBefore("-")
        }
    }

    fun preferredAudioLanguages(providerLanguage: String?): Array<String>? {
        val lang = providerLanguage
            ?.substringBefore("-")
            ?.trim()
            ?.lowercase()
            ?: return null
        return when (lang) {
            "es" -> arrayOf("spa", "es")
            "en" -> arrayOf("en", "eng")
            "fr" -> arrayOf("fr", "fra", "fre")
            "it" -> arrayOf("it", "ita")
            "de" -> arrayOf("de", "deu", "ger")
            else -> null
        }
    }

    /** Higher score = play earlier for French TMDB (prefer dubbed VF over VO). */
    fun frenchServerPriority(serverName: String): Int {
        val n = serverName.uppercase()
        return when {
            n.contains("FRENCH") || Regex("""(^|[^A-Z])VF([^A-Z]|$)""").containsMatchIn(n) -> 100
            n.contains("VOSTFR") -> 40
            n.contains("(VO)") || Regex("""(^|[^A-Z])VO([^A-Z]|$)""").containsMatchIn(n) -> 10
            else -> 50
        }
    }
}
