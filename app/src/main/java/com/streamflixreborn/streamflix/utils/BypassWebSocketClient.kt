package com.streamflixreborn.streamflix.utils

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class BypassWebSocketClient(
    uri: String
) : WebSocketClient(URI(uri)) {

    override fun onOpen(handshakedata: ServerHandshake?) {
        Log.d("BypassWS", "Connected to TV")
    }

    override fun onMessage(message: String?) {}

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        Log.d("BypassWS", "Closed")
    }

    override fun onError(ex: Exception?) {
        Log.e("BypassWS", "Error", ex)
    }
}