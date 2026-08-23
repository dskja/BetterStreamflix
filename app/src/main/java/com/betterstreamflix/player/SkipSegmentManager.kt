package com.betterstreamflix.player

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manages skip segments (intro, outro, recap) for episodes and movies.
 * Stores skip timestamps per video.
 */
object SkipSegmentManager {

    private const val PREFS_NAME = "skip_segments"
    private const val KEY_INTRO_START = "intro_start_"
    private const val KEY_INTRO_END = "intro_end_"
    private const val KEY_OUTRO_START = "outro_start_"
    private const val KEY_OUTRO_END = "outro_end_"

    data class SkipSegment(
        val type: SegmentType,
        val startMs: Long,
        val endMs: Long,
    )

    enum class SegmentType { INTRO, OUTRO, RECAP }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save an intro segment for a video.
     */
    fun saveIntroSegment(context: Context, videoId: String, startMs: Long, endMs: Long) {
        getPrefs(context).edit {
            putLong(KEY_INTRO_START + videoId, startMs)
            putLong(KEY_INTRO_END + videoId, endMs)
        }
    }

    /**
     * Save an outro segment for a video.
     */
    fun saveOutroSegment(context: Context, videoId: String, startMs: Long, endMs: Long) {
        getPrefs(context).edit {
            putLong(KEY_OUTRO_START + videoId, startMs)
            putLong(KEY_OUTRO_END + videoId, endMs)
        }
    }

    /**
     * Get the intro segment for a video, if any.
     */
    fun getIntroSegment(context: Context, videoId: String): SkipSegment? {
        val prefs = getPrefs(context)
        val start = prefs.getLong(KEY_INTRO_START + videoId, -1)
        val end = prefs.getLong(KEY_INTRO_END + videoId, -1)
        if (start < 0 || end < 0) return null
        return SkipSegment(SegmentType.INTRO, start, end)
    }

    /**
     * Get the outro segment for a video, if any.
     */
    fun getOutroSegment(context: Context, videoId: String): SkipSegment? {
        val prefs = getPrefs(context)
        val start = prefs.getLong(KEY_OUTRO_START + videoId, -1)
        val end = prefs.getLong(KEY_OUTRO_END + videoId, -1)
        if (start < 0 || end < 0) return null
        return SkipSegment(SegmentType.OUTRO, start, end)
    }

    /**
     * Check if current position is within a skip segment.
     */
    fun getActiveSkipSegment(context: Context, videoId: String, positionMs: Long): SkipSegment? {
        getIntroSegment(context, videoId)?.let { seg ->
            if (positionMs in seg.startMs..seg.endMs) return seg
        }
        getOutroSegment(context, videoId)?.let { seg ->
            if (positionMs in seg.startMs..seg.endMs) return seg
        }
        return null
    }

    /**
     * Clear all segments for a video.
     */
    fun clearSegments(context: Context, videoId: String) {
        getPrefs(context).edit {
            remove(KEY_INTRO_START + videoId)
            remove(KEY_INTRO_END + videoId)
            remove(KEY_OUTRO_START + videoId)
            remove(KEY_OUTRO_END + videoId)
        }
    }
}
