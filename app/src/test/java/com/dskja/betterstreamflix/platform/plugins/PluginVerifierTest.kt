package com.dskja.betterstreamflix.platform.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PluginVerifierTest {
    @Test
    fun rejectsMissingShaAndEntryClass() {
        val tmp = File.createTempFile("plugin", ".apk").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }
        val missingSha = PluginVerifier.verify(
            apkFile = tmp,
            expectedSha256 = "",
            apiVersion = 1,
            entryClass = "com.example.Plugin",
        )
        assertTrue(missingSha is PluginVerifier.Result.Rejected)

        val missingEntry = PluginVerifier.verify(
            apkFile = tmp,
            expectedSha256 = "abcd",
            apiVersion = 1,
            entryClass = "",
        )
        assertTrue(missingEntry is PluginVerifier.Result.Rejected)
    }

    @Test
    fun acceptsMatchingSha256() {
        val bytes = byteArrayOf(9, 8, 7, 6, 5)
        val tmp = File.createTempFile("plugin-ok", ".apk").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
        val sha = PluginVerifier.sha256Hex(tmp)
        val ok = PluginVerifier.verify(
            apkFile = tmp,
            expectedSha256 = sha,
            apiVersion = PluginVerifier.CURRENT_API_VERSION,
            entryClass = "com.example.LocalPlugin",
        )
        assertTrue(ok is PluginVerifier.Result.Ok)
        assertEquals(sha.lowercase(), (ok as PluginVerifier.Result.Ok).sha256)
    }

    @Test
    fun rejectsApiMismatch() {
        val tmp = File.createTempFile("plugin-api", ".apk").apply {
            writeBytes(byteArrayOf(1))
            deleteOnExit()
        }
        val sha = PluginVerifier.sha256Hex(tmp)
        val rejected = PluginVerifier.verify(
            apkFile = tmp,
            expectedSha256 = sha,
            apiVersion = 99,
            entryClass = "com.example.LocalPlugin",
        )
        assertTrue(rejected is PluginVerifier.Result.Rejected)
    }
}
