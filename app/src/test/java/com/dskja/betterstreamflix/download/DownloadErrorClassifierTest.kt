package com.dskja.betterstreamflix.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadErrorClassifierTest {

    @Test
    fun classifiesCloudflareAndEmptyBody() {
        assertEquals(
            DownloadErrorCode.CLOUDFLARE,
            DownloadErrorClassifier.classify(Exception("Just a moment… Cloudflare")),
        )
        assertEquals(
            DownloadErrorCode.EMPTY_RESPONSE,
            DownloadErrorClassifier.classify(Exception("End of input at character 0 of ")),
        )
    }

    @Test
    fun classifiesExpiredVsNetwork() {
        assertEquals(
            DownloadErrorCode.EXPIRED,
            DownloadErrorClassifier.classify(Exception("HTTP 403 Forbidden")),
        )
        assertEquals(
            DownloadErrorCode.NETWORK,
            DownloadErrorClassifier.classify(Exception("Unable to resolve host api.example")),
        )
        assertEquals(
            DownloadErrorCode.NOSPACE,
            DownloadErrorClassifier.classify(Exception("ENOSPC no space left on device")),
        )
    }
}
