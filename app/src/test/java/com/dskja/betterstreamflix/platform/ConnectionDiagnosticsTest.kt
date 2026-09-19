package com.dskja.betterstreamflix.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectionDiagnosticsTest {

    @Before
    fun clear() {
        ConnectionDiagnostics.clearLastReport()
    }

    @Test
    fun probeReportSummaryTruncatesLines() {
        val report = ConnectionDiagnostics.ProbeReport(
            ok = true,
            title = "OK",
            lines = listOf("a", "b", "c", "d", "e"),
        )
        assertEquals("a · b · c", report.asSummary(3))
        assertEquals("a · b · c · d · e", report.asSummary(10))
    }

    @Test
    fun probeReportClipboardIncludesTitleAndLines() {
        val report = ConnectionDiagnostics.ProbeReport(
            ok = false,
            title = "Issues",
            lines = listOf("DNS failed", "DoH OK"),
        )
        assertEquals("Issues\nDNS failed\nDoH OK", report.asClipboardText())
    }

    @Test
    fun rememberStoresLastReport() {
        assertTrue(ConnectionDiagnostics.lastReport() == null)
        val report = ConnectionDiagnostics.ProbeReport(
            ok = true,
            title = "Pass",
            lines = listOf("ok"),
            latencyMs = 42,
        )
        ConnectionDiagnostics.remember(report)
        assertEquals(report, ConnectionDiagnostics.lastReport())
        assertEquals(42L, ConnectionDiagnostics.lastReport()?.latencyMs)
    }

    @Test
    fun resolveSystemBogusHostIsEmpty() {
        assertTrue(ConnectionDiagnostics.resolveSystem("definitely-not-a-real-host.invalid").isEmpty())
    }

    @Test
    fun probeReportOkFlagPreserved() {
        assertFalse(
            ConnectionDiagnostics.ProbeReport(false, "fail", emptyList()).ok,
        )
        assertTrue(
            ConnectionDiagnostics.ProbeReport(true, "ok", emptyList()).ok,
        )
    }
}
