package com.streamflixreborn.streamflix.utils

import android.util.Log
import org.java_websocket.server.WebSocketServer
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import java.net.InetSocketAddress

class BypassWebSocketServer(
    port: Int,
    private val onDone: () -> Unit
) : WebSocketServer(InetSocketAddress(port)) {

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        Log.d("BypassWS", "Client connected: ${conn.remoteSocketAddress}")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        Log.d("BypassWS", "Message: $message")

        if (message == "done") {
            onDone()
        }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        Log.d("BypassWS", "Closed")
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e("BypassWS", "Error", ex)
    }

    override fun onStart() {
        Log.d("BypassWS", "Server started")
    }
}