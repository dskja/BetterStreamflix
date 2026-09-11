package com.dskja.betterstreamflix.cast

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-scoped Cast session + stream proxy + queue.
 * Survives player fragment teardown so casting continues while browsing the app.
 */
@OptIn(UnstableApi::class)
object CastPlaybackHub {

    private const val TAG = "CastPlaybackHub"

    @Volatile
    private var castPlayer: CastPlayer? = null

    @Volatile
    private var proxy: CastStreamProxyServer? = null

    @Volatile
    var isCasting: Boolean = false
        private set

    @Volatile
    var lastTitle: String? = null
        private set

    @Volatile
    var lastSubtitle: String? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val queue = CopyOnWriteArrayList<MediaItem>()
    private val sessionStateListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
    private var sessionManagerListener: SessionManagerListener<CastSession>? = null
    private var playerListener: Player.Listener? = null

    fun ensureCastContext(context: Context) {
        runCatching {
            val castContext = CastContext.getSharedInstance(context.applicationContext)
            attachSessionManager(castContext)
        }.onFailure {
            Log.w(TAG, "CastContext unavailable: ${it.message}")
        }
    }

    fun obtainPlayer(context: Context): CastPlayer? {
        castPlayer?.let { return it }
        return runCatching {
            val castContext = CastContext.getSharedInstance(context.applicationContext)
            attachSessionManager(castContext)
            CastPlayer(castContext).also { player ->
                castPlayer = player
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            playNextInQueue()
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        lastTitle = mediaItem?.mediaMetadata?.title?.toString()
                        lastSubtitle = mediaItem?.mediaMetadata?.subtitle?.toString()
                    }
                }
                playerListener = listener
                player.addListener(listener)
            }
        }.onFailure {
            Log.w(TAG, "CastPlayer create failed: ${it.message}")
        }.getOrNull()
    }

    fun playerOrNull(): CastPlayer? = castPlayer

    fun setSessionAvailabilityListener(listener: SessionAvailabilityListener?) {
        castPlayer?.setSessionAvailabilityListener(listener)
    }

    fun addSessionStateListener(listener: (Boolean) -> Unit) {
        sessionStateListeners.add(listener)
    }

    fun removeSessionStateListener(listener: (Boolean) -> Unit) {
        sessionStateListeners.remove(listener)
    }

    fun markCasting(active: Boolean) {
        if (isCasting == active) return
        isCasting = active
        sessionStateListeners.forEach { runCatching { it(active) } }
        if (!active) {
            stopProxyIfIdle()
        }
    }

    fun ensureProxy(headers: Map<String, String>): CastStreamProxyServer? {
        val existing = proxy
        if (existing != null && existing.wasStarted()) {
            existing.updateDefaultHeaders(headers)
            return existing
        }
        return runCatching {
            CastStreamProxyServer().also { server ->
                server.updateDefaultHeaders(headers)
                server.start()
                proxy = server
                Log.i(TAG, "Cast proxy listening on ${server.publicBaseUrl()}")
            }
        }.onFailure {
            Log.e(TAG, "Failed to start cast proxy", it)
            proxy = null
        }.getOrNull()
    }

    fun wrapForCast(originalUrl: String, headers: Map<String, String>): String {
        if (originalUrl.startsWith("data:", ignoreCase = true)) return originalUrl
        val server = ensureProxy(headers) ?: return originalUrl
        return server.wrap(originalUrl)
    }

    fun clearQueue() = queue.clear()

    fun enqueue(item: MediaItem) {
        queue.add(item)
    }

    fun enqueueAll(items: List<MediaItem>) {
        queue.addAll(items)
    }

    fun queuedCount(): Int = queue.size

    fun playNow(
        context: Context,
        item: MediaItem,
        startPositionMs: Long = 0L,
        playWhenReady: Boolean = true,
    ) {
        val player = obtainPlayer(context) ?: return
        lastTitle = item.mediaMetadata.title?.toString()
        lastSubtitle = item.mediaMetadata.subtitle?.toString()
        player.setMediaItem(item, startPositionMs)
        player.prepare()
        player.playWhenReady = playWhenReady
        markCasting(true)
    }

    fun playNextInQueue() {
        val player = castPlayer ?: return
        if (queue.isEmpty()) return
        val next = queue.removeAt(0)
        mainHandler.post {
            runCatching {
                player.setMediaItem(next)
                player.prepare()
                player.playWhenReady = true
                lastTitle = next.mediaMetadata.title?.toString()
                lastSubtitle = next.mediaMetadata.subtitle?.toString()
            }.onFailure {
                Log.w(TAG, "Failed to play next Cast queue item: ${it.message}")
            }
        }
    }

    fun setReceiverVolume(volume: Float) {
        castPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    fun seekBy(deltaMs: Long) {
        val player = castPlayer ?: return
        player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))
    }

    fun currentPosition(): Long = castPlayer?.currentPosition ?: 0L

    fun duration(): Long = castPlayer?.duration?.takeIf { it > 0 } ?: 0L

    /**
     * Detach UI without ending an active Cast session.
     * Call from player [androidx.fragment.app.Fragment.onDestroyView].
     */
    fun detachUi(context: Context) {
        castPlayer?.setSessionAvailabilityListener(null)
        val sessionConnected = runCatching {
            CastContext.getSharedInstance(context.applicationContext)
                .sessionManager
                .currentCastSession
                ?.isConnected == true
        }.getOrDefault(false)
        if (!sessionConnected && !isCasting) {
            releaseFully()
        }
    }

    fun releaseFully() {
        runCatching { castPlayer?.setSessionAvailabilityListener(null) }
        playerListener?.let { listener ->
            runCatching { castPlayer?.removeListener(listener) }
        }
        playerListener = null
        runCatching { castPlayer?.release() }
        castPlayer = null
        isCasting = false
        queue.clear()
        stopProxy()
    }

    private fun attachSessionManager(castContext: CastContext) {
        if (sessionManagerListener != null) return
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                markCasting(true)
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                markCasting(true)
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                markCasting(false)
            }

            override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
            override fun onSessionStarting(session: CastSession) = Unit
            override fun onSessionStartFailed(session: CastSession, error: Int) {
                Log.w(TAG, "Cast session start failed: $error")
                markCasting(false)
            }

            override fun onSessionEnding(session: CastSession) = Unit
            override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                Log.w(TAG, "Cast session resume failed: $error")
            }
        }
        sessionManagerListener = listener
        castContext.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
    }

    private fun stopProxyIfIdle() {
        if (!isCasting) stopProxy()
    }

    private fun stopProxy() {
        runCatching { proxy?.stop() }
        proxy = null
    }
}
