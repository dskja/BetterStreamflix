package com.betterstreamflix.download

enum class StreamType {
    HLS,
    DASH,
    HTTP,
}

object StreamTypeDetector {
    fun detect(url: String): StreamType = when {
        url.contains(".m3u8", ignoreCase = true) ||
            url.contains("application/x-mpegURL", ignoreCase = true) ||
            url.contains("application/vnd.apple.mpegurl", ignoreCase = true) -> StreamType.HLS
        url.contains(".mpd", ignoreCase = true) ||
            url.contains("application/dash+xml", ignoreCase = true) -> StreamType.DASH
        else -> StreamType.HTTP
    }

    fun isDrmProtected(url: String): Boolean =
        url.contains("widevine", ignoreCase = true) ||
            url.contains("playready", ignoreCase = true) ||
            url.contains("fairplay", ignoreCase = true)
}
