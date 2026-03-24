package com.streamflixreborn.streamflix.utils

import android.util.Log
import android.net.Uri
import java.util.UUID

object WebSocketBypassTestHelper {

    private const val TAG = "BypassWSTest"
    private const val DEFAULT_PORT = 8081

    private var server: BypassWebSocketServer? = null
    private var serverPort: Int = DEFAULT_PORT

    data class Session(
        val token: String,
        val wsUrl: String,
        val deepLink: String,
    )

    @Synchronized
    fun createSession(
        url: String,
        onDone: (String) -> Unit = {},
    ): Session? {
        val activeServer = ensureServer(onDone) ?: return null
        val wsUrl = BypassWebSocketEndpointHelper.getAdvertisedWsUrl(serverPort) ?: return null
        val token = UUID.randomUUID().toString()

        activeServer.registerSession(token, url)
        val deepLink = "streamflix://resolve?ws=${Uri.encode(wsUrl)}&token=$token"

        return Session(
            token = token,
            wsUrl = wsUrl,
            deepLink = deepLink,
        )
    }

    @Synchronized
    private fun ensureServer(onDone: (String) -> Unit): BypassWebSocketServer? {
        server?.let { return it }

        return runCatching {
            BypassWebSocketServer(serverPort) { token, _ ->
                onDone(token)
            }.also {
                it.start()
                check(it.awaitStart()) { "Timed out while starting websocket server" }
                server = it
            }
        }.onFailure {
            Log.e(TAG, "Unable to start websocket server", it)
        }.getOrNull()
    }
}
