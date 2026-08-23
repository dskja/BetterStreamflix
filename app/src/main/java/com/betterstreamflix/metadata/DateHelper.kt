package com.betterstreamflix.metadata

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Date formatting helper for content metadata.
 */
object DateHelper {

    private val yearFormat = SimpleDateFormat("yyyy", Locale.US)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    /**
     * Extract year from a date string.
     */
    fun extractYear(dateString: String?): String {
        if (dateString.isNullOrBlank()) return ""
        return try {
            val date = dateFormat.parse(dateString)
            yearFormat.format(date)
        } catch (e: Exception) {
            dateString.substringBefore("-").take(4)
        }
    }

    /**
     * Format a date string for display.
     */
    fun formatForDisplay(dateString: String?): String {
        if (dateString.isNullOrBlank()) return ""
        return try {
            val date = dateFormat.parse(dateString)
            displayFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    /**
     * Format duration in minutes to "Xh Ym" format.
     */
    fun formatRuntime(minutes: Int): String {
        if (minutes <= 0) return ""
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    /**
     * Format milliseconds to "H:MM:SS" or "M:SS" format.
     */
    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    /**
     * Format a timestamp to relative time (e.g., "2 hours ago").
     */
    fun formatRelativeTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 30 -> formatForDisplay(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp)))
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "just now"
        }
    }
}
