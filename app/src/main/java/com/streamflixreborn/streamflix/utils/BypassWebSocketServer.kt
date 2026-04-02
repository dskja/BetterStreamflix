package com.streamflixreborn.streamflix.utils

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BypassWebSocketServer(
    port: Int,
    private val onDone: (token: String, cookies: String?) -> Unit
) : WebSocketServer(InetSocketAddress(port)) {

    private val sessions = ConcurrentHashMap<String, String>()
    private val startedLatch = CountDownLatch(1)

    fun registerSession(token: String, payload: String) {
        sessions[token] = payload
    }

    fun clearSession(token: String) {
        sessions.remove(token)
    }

    private var startError: Exception? = null

    fun getStartError(): Exception? = startError

    fun awaitStart(timeoutMs: Long = 5_000): Boolean {
        return startedLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        Log.d("BypassWS", "Client connected: ${conn.remoteSocketAddress}")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        Log.d("BypassWS", "Message: $message")

        when {
            message.startsWith("resolve:") -> {
                val token = message.substringAfter("resolve:")
                val payload = sessions[token]
                if (payload != null) {
                    conn.send("payload:$payload")
                } else {
                    conn.send("error:unknown_session")
                }
            }

            message.startsWith("done:") -> {
                val payload = message.substringAfter("done:")
                val token = payload.substringBefore(":")
                val cookies = payload.substringAfter(":", "")
                    .takeIf { it.isNotBlank() }
                    ?.let { encoded ->
                        runCatching {
                            String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
                        }.getOrNull()
                    }
                if (sessions.remove(token) != null) {
                    conn.send("ack:$token")
                    onDone(token, cookies)
                } else {
                    conn.send("error:unknown_session")
                }
            }
        }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        Log.d("BypassWS", "Closed")
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e("BypassWS", "Error", ex)
        if (conn == null) {
            startError = ex
            startedLatch.countDown()
        }
    }

    override fun onStart() {
        Log.d("BypassWS", "Server started")
        startedLatch.countDown()
    }
}
