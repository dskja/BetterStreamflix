package com.betterstreamflix.notifications

import android.app.Notification
import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Media session manager — manages the media session for background
 * playback controls.
 */
object MediaSessionManager {

    private var mediaSession: MediaSession? = null

    /**
     * Create and start a media session.
     */
    fun createSession(
        context: Context,
        player: ExoPlayer,
        sessionId: String = "betterstreamflix_media_session",
    ): MediaSession {
        val sessionActivityIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = sessionActivityIntent?.let {
            android.app.PendingIntent.getActivity(
                context,
                0,
                it,
                android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val builder = MediaSession.Builder(context, player)
            .setId(sessionId)

        pendingIntent?.let { builder.setSessionActivity(it) }

        mediaSession = builder.build()
        return mediaSession ?: throw IllegalStateException("Failed to create media session")
    }

    /**
     * Get the current media session.
     */
    fun getSession(): MediaSession? = mediaSession

    /**
     * Release the media session.
     */
    fun releaseSession() {
        mediaSession?.release()
        mediaSession = null
    }

    /**
     * Update the player in the media session.
     */
    fun updatePlayer(player: ExoPlayer) {
        mediaSession?.player = player
    }

    /**
     * Check if a media session is active.
     */
    fun isSessionActive(): Boolean = mediaSession != null
}
