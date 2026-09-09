package com.dskja.betterstreamflix.cast

import android.content.Context
import android.util.Log
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext

/**
 * Process-scoped Cast session + stream proxy.
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

    fun ensureCastContext(context: Context) {
        runCatching {
            CastContext.getSharedInstance(context.applicationContext)
        }.onFailure {
            Log.w(TAG, "CastContext unavailable: ${it.message}")
        }
    }

    fun obtainPlayer(context: Context): CastPlayer? {
        castPlayer?.let { return it }
        return runCatching {
            val castContext = CastContext.getSharedInstance(context.applicationContext)
            CastPlayer(castContext).also { castPlayer = it }
        }.onFailure {
            Log.w(TAG, "CastPlayer create failed: ${it.message}")
        }.getOrNull()
    }

    fun playerOrNull(): CastPlayer? = castPlayer

    fun setSessionAvailabilityListener(listener: SessionAvailabilityListener?) {
        castPlayer?.setSessionAvailabilityListener(listener)
    }

    fun markCasting(active: Boolean) {
        isCasting = active
        if (!active) {
            // Keep proxy warm briefly while switching; stop when idle.
            stopProxyIfIdle()
        }
    }

    fun ensureProxy(headers: Map<String, String>): CastStreamProxyServer? {
        val existing = proxy
        if (existing != null && existing.isAlive) {
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
        runCatching { castPlayer?.release() }
        castPlayer = null
        isCasting = false
        stopProxy()
    }

    private fun stopProxyIfIdle() {
        if (!isCasting) stopProxy()
    }

    private fun stopProxy() {
        runCatching { proxy?.stop() }
        proxy = null
    }
}
