package com.betterstreamflix.download

/**
 * Identifies Media3 offline downloads in Room [DownloadManager.DownloadTask.filePath].
 * HTTP downloads use a plain filesystem path instead.
 */
object OfflineMediaPaths {

    const val SCHEME = "media3offline"

    fun forDownload(downloadId: String): String = "$SCHEME://$downloadId"

    fun parseDownloadId(filePath: String): String? {
        if (!filePath.startsWith("$SCHEME://")) return null
        return filePath.removePrefix("$SCHEME://").takeIf { it.isNotBlank() }
    }
}
