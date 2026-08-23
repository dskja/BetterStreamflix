package com.betterstreamflix.i18n

import java.util.Locale

/**
 * Number formatter — formats numbers and dates according to the current locale.
 */
object LocaleFormatter {

    /**
     * Format a number according to locale.
     */
    fun formatNumber(number: Int, locale: Locale = Locale.getDefault()): String {
        return String.format(locale, "%,d", number)
    }

    /**
     * Format a rating (e.g., "8.5").
     */
    fun formatRating(rating: Double, locale: Locale = Locale.getDefault()): String {
        return String.format(locale, "%.1f", rating)
    }

    /**
     * Format a file size in bytes to a human-readable string.
     */
    fun formatFileSize(bytes: Long, locale: Locale = Locale.getDefault()): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format(locale, "%.1f GB", gb)
            mb >= 1 -> String.format(locale, "%.1f MB", mb)
            kb >= 1 -> String.format(locale, "%.1f KB", kb)
            else -> String.format(locale, "%d B", bytes)
        }
    }

    /**
     * Format a duration in milliseconds to a readable string.
     */
    fun formatDuration(ms: Long, locale: Locale = Locale.getDefault()): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format(locale, "%d:%02d", minutes, seconds)
        }
    }

    /**
     * Format a percentage.
     */
    fun formatPercent(value: Float, locale: Locale = Locale.getDefault()): String {
        return String.format(locale, "%d%%", (value * 100).toInt())
    }
}
