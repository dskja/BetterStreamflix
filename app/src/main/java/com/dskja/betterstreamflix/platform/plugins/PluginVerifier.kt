package com.dskja.betterstreamflix.platform.plugins

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Safety gate for local plugin APKs before [DexClassLoader] is used.
 * Requires a catalog SHA-256 pin and a supported API version.
 */
object PluginVerifier {
    const val CURRENT_API_VERSION = 1

    sealed class Result {
        data class Ok(val sha256: String) : Result()
        data class Rejected(val reason: String) : Result()
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(
        apkFile: File,
        expectedSha256: String,
        apiVersion: Int,
        entryClass: String,
        maxBytes: Long = 32L * 1024L * 1024L,
    ): Result {
        if (!apkFile.isFile) {
            return Result.Rejected("APK missing: ${apkFile.absolutePath}")
        }
        if (apkFile.length() <= 0L) {
            return Result.Rejected("APK empty")
        }
        if (apkFile.length() > maxBytes) {
            return Result.Rejected("APK too large (${apkFile.length()} bytes)")
        }
        if (expectedSha256.isBlank()) {
            return Result.Rejected("Catalog entry requires sha256 pin")
        }
        if (entryClass.isBlank()) {
            return Result.Rejected("Catalog entry requires entryClass")
        }
        if (apiVersion != CURRENT_API_VERSION) {
            return Result.Rejected(
                "Unsupported apiVersion=$apiVersion (need $CURRENT_API_VERSION)",
            )
        }
        val actual = runCatching { sha256Hex(apkFile) }.getOrElse {
            return Result.Rejected("SHA-256 failed: ${it.message}")
        }
        if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
            return Result.Rejected("SHA-256 mismatch")
        }
        return Result.Ok(actual.lowercase())
    }
}
