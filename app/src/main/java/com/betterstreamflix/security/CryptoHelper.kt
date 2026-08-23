package com.betterstreamflix.security

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Crypto helper — provides AES encryption/decryption for sensitive data
 * that needs to be stored or transmitted.
 */
object CryptoHelper {

    private const val ALGORITHM = "AES/CBC/PKCS7Padding"
    private const val KEY_ALGORITHM = "AES"
    private const val IV_LENGTH = 16

    /**
     * Encrypt a string with a given key.
     */
    fun encrypt(plaintext: String, key: String): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val secretKey = deriveKey(key)
        val iv = ByteArray(IV_LENGTH).also { java.security.SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt a string with a given key.
     */
    fun decrypt(ciphertext: String, key: String): String? {
        return try {
            val combined = Base64.decode(ciphertext, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(ALGORITHM)
            val secretKey = deriveKey(key)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Hash a string using SHA-256.
     */
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generate a random token.
     */
    fun generateToken(length: Int = 32): String {
        val bytes = ByteArray(length)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun deriveKey(key: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(key.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(hash, KEY_ALGORITHM)
    }
}
