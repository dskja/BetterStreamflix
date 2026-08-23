package com.betterstreamflix.polish

import android.content.Context
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Time formatter — provides consistent time and date formatting
 * across the app.
 */
object TimeFormatter {

    /**
     * Format a duration in milliseconds as a time string.
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
     * Format a relative time (e.g., "2 hours ago").
     */
    fun formatRelativeTime(timestamp: Long, context: Context): String {
        return DateUtils.getRelativeTimeSpanString(
            timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    }

    /**
     * Format a date for display.
     */
    fun formatDate(timestamp: Long, pattern: String = "MMM d, yyyy"): String {
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * Format a time for display.
     */
    fun formatTime(timestamp: Long, context: Context): String {
        return if (android.text.format.DateFormat.is24HourFormat(context)) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        } else {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        }
    }

    /**
     * Format a date and time together.
     */
    fun formatDateTime(timestamp: Long, context: Context): String {
        return "${formatDate(timestamp)} at ${formatTime(timestamp, context)}"
    }

    /**
     * Format bytes as a human-readable size.
     */
    fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.1f GB", gb)
            mb >= 1 -> String.format("%.1f MB", mb)
            kb >= 1 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * Format a count with abbreviation (e.g., 1.2K, 3.5M).
     */
    fun formatCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    /**
     * Get day of week from timestamp.
     */
    fun getDayOfWeek(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        return SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
    }

    /**
     * Check if a timestamp is today.
     */
    fun isToday(timestamp: Long): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal2.timeInMillis = timestamp
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Check if a timestamp is within the last N days.
     */
    fun isWithinLastDays(timestamp: Long, days: Int): Boolean {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        return timestamp >= cutoff
    }
}
