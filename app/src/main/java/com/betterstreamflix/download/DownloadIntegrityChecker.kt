package com.betterstreamflix.download

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Download integrity checker — verifies downloaded file integrity
 * using checksums and file size validation.
 */
object DownloadIntegrityChecker {

    /**
     * Verify a file's SHA-256 checksum.
     */
    fun verifySha256(file: File, expectedHash: String): Boolean {
        if (!file.exists()) return false
        val actualHash = calculateSha256(file)
        return actualHash.equals(expectedHash, ignoreCase = true)
    }

    /**
     * Verify a file's MD5 checksum.
     */
    fun verifyMd5(file: File, expectedHash: String): Boolean {
        if (!file.exists()) return false
        val actualHash = calculateMd5(file)
        return actualHash.equals(expectedHash, ignoreCase = true)
    }

    /**
     * Verify file size.
     */
    fun verifyFileSize(file: File, expectedSize: Long): Boolean {
        return file.exists() && file.length() == expectedSize
    }

    /**
     * Calculate SHA-256 hash of a file.
     */
    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Calculate MD5 hash of a file.
     */
    fun calculateMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Check if a file is a valid video file by checking magic bytes.
     */
    fun isValidVideoFile(file: File): Boolean {
        if (!file.exists() || file.length() < 1024) return false

        return try {
            file.inputStream().use { input ->
                val header = ByteArray(12)
                val read = input.read(header)
                if (read < 4) return false

                // Check for common video file signatures
                // MP4: ftyp box at offset 4
                val ftypIndex = indexOf(header, "ftyp".toByteArray())
                if (ftypIndex >= 0 && ftypIndex < 8) return true

                // MKV/WebM: 0x1A 0x45 0xDF 0xA3
                if (header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                    header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()) return true

                // AVI: RIFF
                if (header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                    header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()) return true

                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Perform a full integrity check on a downloaded file.
     */
    fun fullCheck(file: File, expectedSize: Long?, expectedHash: String?): IntegrityResult {
        if (!file.exists()) return IntegrityResult(false, "File does not exist")
        if (file.length() == 0L) return IntegrityResult(false, "File is empty")

        if (expectedSize != null && !verifyFileSize(file, expectedSize)) {
            return IntegrityResult(false, "File size mismatch: expected $expectedSize, got ${file.length()}")
        }

        if (expectedHash != null && !verifySha256(file, expectedHash)) {
            return IntegrityResult(false, "Checksum mismatch")
        }

        if (!isValidVideoFile(file)) {
            return IntegrityResult(false, "File does not appear to be a valid video")
        }

        return IntegrityResult(true, "File integrity verified")
    }

    private fun indexOf(array: ByteArray, pattern: ByteArray): Int {
        for (i in 0..array.size - pattern.size) {
            if (pattern.indices.all { array[i + it] == pattern[it] }) return i
        }
        return -1
    }

    data class IntegrityResult(val isValid: Boolean, val message: String)
}
