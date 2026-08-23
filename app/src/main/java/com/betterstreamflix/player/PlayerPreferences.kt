package com.betterstreamflix.player

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer

/**
 * Manages player preferences: quality, audio track, subtitle track, captions.
 */
object PlayerPreferences {

    private const val PREFS_NAME = "player_prefs"
    private const val KEY_QUALITY_HEIGHT = "quality_height"
    private const val KEY_AUDIO_LANG = "audio_lang"
    private const val KEY_SUBTITLE_LANG = "subtitle_lang"
    private const val KEY_CAPTIONS_ENABLED = "captions_enabled"
    private const val KEY_SPEED = "playback_speed"
    private const val KEY_SEEK_INCREMENT = "seek_increment"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get preferred video quality height (e.g., 720, 1080). Null = auto.
     */
    fun getQualityHeight(context: Context): Int? {
        return getPrefs(context).getInt(KEY_QUALITY_HEIGHT, -1).takeIf { it > 0 }
    }

    /**
     * Set preferred video quality height.
     */
    fun setQualityHeight(context: Context, height: Int?) {
        getPrefs(context).edit {
            if (height != null) putInt(KEY_QUALITY_HEIGHT, height)
            else remove(KEY_QUALITY_HEIGHT)
        }
    }

    /**
     * Get preferred audio language.
     */
    fun getAudioLanguage(context: Context): String? {
        return getPrefs(context).getString(KEY_AUDIO_LANG, null)
    }

    /**
     * Set preferred audio language.
     */
    fun setAudioLanguage(context: Context, lang: String?) {
        getPrefs(context).edit {
            if (lang != null) putString(KEY_AUDIO_LANG, lang)
            else remove(KEY_AUDIO_LANG)
        }
    }

    /**
     * Get preferred subtitle language.
     */
    fun getSubtitleLanguage(context: Context): String? {
        return getPrefs(context).getString(KEY_SUBTITLE_LANG, null)
    }

    /**
     * Set preferred subtitle language.
     */
    fun setSubtitleLanguage(context: Context, lang: String?) {
        getPrefs(context).edit {
            if (lang != null) putString(KEY_SUBTITLE_LANG, lang)
            else remove(KEY_SUBTITLE_LANG)
        }
    }

    /**
     * Check if captions are enabled.
     */
    fun isCaptionsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CAPTIONS_ENABLED, false)
    }

    /**
     * Toggle captions.
     */
    fun setCaptionsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_CAPTIONS_ENABLED, enabled) }
    }

    /**
     * Get saved playback speed.
     */
    fun getPlaybackSpeed(context: Context): Float {
        return getPrefs(context).getFloat(KEY_SPEED, 1.0f)
    }

    /**
     * Save playback speed.
     */
    fun setPlaybackSpeed(context: Context, speed: Float) {
        getPrefs(context).edit { putFloat(KEY_SPEED, speed) }
    }

    /**
     * Get seek increment in seconds (default 10).
     */
    fun getSeekIncrement(context: Context): Int {
        return getPrefs(context).getInt(KEY_SEEK_INCREMENT, 10)
    }

    /**
     * Set seek increment.
     */
    fun setSeekIncrement(context: Context, seconds: Int) {
        getPrefs(context).edit { putInt(KEY_SEEK_INCREMENT, seconds) }
    }

    /**
     * Apply saved preferences to an ExoPlayer.
     */
    fun applyToPlayer(context: Context, player: ExoPlayer) {
        val params = TrackSelectionParameters.Builder(context)

        getQualityHeight(context)?.let { height ->
            params.setMaxVideoSize(Int.MAX_VALUE, height)
        }

        getAudioLanguage(context)?.let { lang ->
            params.setPreferredAudioLanguages(lang)
        }

        getSubtitleLanguage(context)?.let { lang ->
            params.setPreferredTextLanguages(lang)
        }

        player.trackSelectionParameters = params.build()
        player.setPlaybackSpeed(getPlaybackSpeed(context))
    }
}
