package com.betterstreamflix.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/** Pure JVM tests for NetworkError classification (no Android/Robolectric). */
class NetworkErrorClassificationTest {

    @Test
    fun `SocketTimeoutException classifies as Timeout`() {
        val error = NetworkError.from(SocketTimeoutException("connect timed out"))
        assertTrue(error is NetworkError.Timeout)
    }

    @Test
    fun `UnknownHostException classifies as DnsFailure`() {
        val error = NetworkError.from(UnknownHostException("unable to resolve host example.com"))
        assertTrue(error is NetworkError.DnsFailure)
    }

    @Test
    fun `SSLHandshakeException classifies as SslError`() {
        val error = NetworkError.from(SSLHandshakeException("Certificate mismatch"))
        assertTrue(error is NetworkError.SslError)
    }

    @Test
    fun `message containing 403 classifies as HttpError 403`() {
        val error = NetworkError.from(RuntimeException("HTTP 403 Forbidden"))
        assertTrue(error is NetworkError.HttpError)
        assertEquals(403, (error as NetworkError.HttpError).code)
    }

    @Test
    fun `message containing 502 classifies as HttpError 502`() {
        val error = NetworkError.from(RuntimeException("HTTP 502 Bad Gateway"))
        assertTrue(error is NetworkError.HttpError)
        assertEquals(502, (error as NetworkError.HttpError).code)
    }

    @Test
    fun `message containing Connection refused classifies as ProviderBlocked`() {
        val error = NetworkError.from(RuntimeException("Connection refused"))
        assertTrue(error is NetworkError.ProviderBlocked)
    }

    @Test
    fun `unknown exception classifies as Unknown`() {
        val error = NetworkError.from(IllegalStateException("Something went wrong"))
        assertTrue(error is NetworkError.Unknown)
        assertEquals("Something went wrong", (error as NetworkError.Unknown).message)
    }
}
