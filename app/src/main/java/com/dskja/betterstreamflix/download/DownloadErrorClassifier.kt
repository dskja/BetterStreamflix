package com.dskja.betterstreamflix.download

/**
 * Shared download failure classifier used by enqueue UI and Media3 event bridge.
 */
object DownloadErrorClassifier {

    fun classify(e: Throwable?): DownloadErrorCode {
        if (e == null) return DownloadErrorCode.UNKNOWN
        val chain = generateSequence(e) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
        val hay = chain.lowercase()
        return when {
            "end of input" in hay ||
                "character 0" in hay ||
                "empty response" in hay ||
                "empty body" in hay ||
                "unexpected end" in hay -> DownloadErrorCode.EMPTY_RESPONSE
            "no space" in hay || "enospc" in hay || "space left" in hay ->
                DownloadErrorCode.NOSPACE
            "wifi" in hay && ("required" in hay || "only" in hay) ->
                DownloadErrorCode.WIFI_REQUIRED
            "cloudflare" in hay || "captcha" in hay || "just a moment" in hay ||
                "cf-ray" in hay -> DownloadErrorCode.CLOUDFLARE
            "drm" in hay || "widevine" in hay || "license" in hay ->
                DownloadErrorCode.DRM
            "no servers" in hay || "servers found" in hay ->
                DownloadErrorCode.NO_SERVERS
            "cleartext" in hay || "cleartexttraffic" in hay ->
                DownloadErrorCode.UNSUPPORTED
            "expired" in hay || "410" in hay ||
                (("403" in hay || "401" in hay || "forbidden" in hay) &&
                    "cloudflare" !in hay) -> DownloadErrorCode.EXPIRED
            "ssl" in hay || "certificate" in hay || "handshake" in hay ->
                DownloadErrorCode.NETWORK
            "unable to resolve host" in hay || "timeout" in hay ||
                "unknownhost" in hay || "sockettimeout" in hay ||
                "failed to connect" in hay -> DownloadErrorCode.NETWORK
            else -> DownloadErrorCode.UNKNOWN
        }
    }
}
