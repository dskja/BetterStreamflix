package com.dskja.betterstreamflix.utils

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AllAnime / MKissa client-crypto (aa-crypto) helpers.
 *
 * Boot token = HMAC-SHA256(HMAC-SHA256(mask, bootPrefix+buildId), "group:lane:epoch:host:buildId").
 * Lane key = partB XOR mask. Episode payloads use AES-256-GCM with that key.
 */
object MkissaAaCrypto {

    const val ANIME_LANE = "k7"
    const val KEY_GROUP = "mkissa"
    const val DEFAULT_REFERER_HOST = "mkissa.to"

    const val EPOCH_MS = 7L * 24 * 60 * 60 * 1000
    const val GRACE_MS = 24L * 60 * 60 * 1000
    const val AA_REQ_WINDOW_MS = 5L * 60 * 1000

    // Live SPA Rf{} values (build 166) confirmed against api.mkissa.net bootstrap.
    private const val SALT_MUL = 165
    private const val SALT_ADD = 115
    private const val FRAG_MUL = 197
    private const val FRAG_ADD = 200
    private const val BOOT_PREFIX = "ld1faaOf3G:"

    private const val KEY_SIZE = 32
    private const val SEED_COUNT = 4
    private const val SEED_SIZE = KEY_SIZE / SEED_COUNT
    private const val IV_SIZE = 12
    private const val HEADER_SIZE = 1 + IV_SIZE

    /** Current SPA build + mask seeds decoded from the crypto chunk (Xc rotation 123). */
    val DEFAULT_BUILD = BuildInfo(
        buildId = "166",
        seeds = listOf(
            "0VmOiOTlfQ0=",
            "F/SlaG5999I=",
            "VTm6fMS7BdQ=",
            "LIQNr2OipeQ=",
        ),
    )

    data class BuildInfo(
        val buildId: String,
        val seeds: List<String>,
    )

    data class Material(
        val buildId: String,
        val epoch: Long,
        val key: ByteArray,
        val mask: ByteArray,
        val switchAt: Long,
    )

    fun epochCandidates(now: Long = System.currentTimeMillis()): List<Long> {
        val current = now / EPOCH_MS
        val inGrace = now - current * EPOCH_MS < GRACE_MS && current > 0
        return if (inGrace) listOf(current - 1, current) else listOf(current)
    }

    fun deriveMask(buildId: String, seeds: List<String>): ByteArray? {
        if (buildId.isBlank() || seeds.size != SEED_COUNT) return null
        val stream = ByteArray(KEY_SIZE) { i ->
            (buildId[i % buildId.length].code xor ((i * SALT_MUL + SALT_ADD) and 0xFF)).toByte()
        }
        val mask = ByteArray(KEY_SIZE)
        for (index in seeds.indices) {
            val bytes = runCatching { Base64.decode(seeds[index], Base64.DEFAULT) }.getOrNull() ?: return null
            if (bytes.size < SEED_SIZE) return null
            val base = index * SEED_SIZE
            for (offset in 0 until SEED_SIZE) {
                mask[base + offset] = (
                    (bytes[offset].toInt() and 0xFF) xor
                        (stream[base + offset].toInt() and 0xFF) xor
                        ((index * FRAG_MUL + offset * FRAG_ADD) and 0xFF)
                    ).toByte()
            }
        }
        if (mask.all { it == 0.toByte() }) return null
        return mask
    }

    fun deriveKey(mask: ByteArray, partB: ByteArray): ByteArray {
        return ByteArray(KEY_SIZE) { i ->
            ((partB[i].toInt() and 0xFF) xor (mask[i % mask.size].toInt() and 0xFF)).toByte()
        }
    }

    fun bootToken(
        mask: ByteArray,
        buildId: String,
        epoch: Long,
        refererHost: String = DEFAULT_REFERER_HOST,
        keyGroup: String = KEY_GROUP,
        lane: String = ANIME_LANE,
    ): String {
        val inner = hmacSha256(mask, "$BOOT_PREFIX$buildId")
        val message = listOf(keyGroup, lane, epoch.toString(), refererHost, buildId).joinToString(":")
        return hmacSha256(inner, message).toHex()
    }

    fun buildAaReq(
        key: ByteArray,
        epoch: Long,
        buildId: String,
        queryHash: String,
        lane: String = ANIME_LANE,
    ): String {
        val ts = System.currentTimeMillis() / AA_REQ_WINDOW_MS * AA_REQ_WINDOW_MS
        val iv = MessageDigest.getInstance("SHA-256")
            .digest("$epoch:$buildId:$queryHash:$ts:$lane".toByteArray(Charsets.UTF_8))
            .copyOfRange(0, IV_SIZE)
        val payload = JSONObject()
            .put("v", 1)
            .put("ts", ts)
            .put("epoch", epoch)
            .put("buildId", buildId)
            .put("qh", queryHash)
            .put("k", lane)
            .toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(byteArrayOf(1) + iv + encrypted, Base64.NO_WRAP)
    }

    fun decryptTobeParsed(value: String, rotatingKey: ByteArray?): JSONObject {
        val bytes = Base64.decode(value, Base64.DEFAULT)
        if (bytes.isEmpty()) throw Exception("Empty MKissa encrypted payload")
        val version = bytes[0].toInt()
        if (version != 1) throw Exception("Unsupported MKissa encryption version: $version")
        val iv = bytes.copyOfRange(1, HEADER_SIZE)
        val cipherText = bytes.copyOfRange(HEADER_SIZE, bytes.size)
        val legacyKey = MessageDigest.getInstance("SHA-256")
            .digest("Xot36i3lK3:v$version".toByteArray(Charsets.UTF_8))
        val candidates = listOfNotNull(rotatingKey, legacyKey)
        var lastError: Exception? = null
        for (key in candidates) {
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                return JSONObject(String(cipher.doFinal(cipherText), Charsets.UTF_8))
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: Exception("MKissa decrypt failed")
    }

    /**
     * Discover buildId + 4 mask seeds from an obfuscated crypto chunk that contains an `Xc`
     * string table (same approach as the live SPA).
     */
    fun discoverBuildFromJs(js: String): BuildInfo? {
        val tableMatch = Regex(
            """function\s+Xc\(\)\s*\{\s*const\s+\w+\s*=\s*(\[[\s\S]*?\]);\s*return\s+Xc\s*="""
        ).find(js) ?: return null
        val table = parseJsStringArray(tableMatch.groupValues[1]) ?: return null
        if (table.isEmpty()) return null

        for (rotation in table.indices) {
            val rotated = ArrayList(table).also { list ->
                repeat(rotation) { list.add(list.removeAt(0)) }
            }
            fun vr(index: Int): String? = rotated.getOrNull(index - 468)
            fun fr(index: Int): String? = vr(index + 271)
            fun at(_a: Int, b: Int): String? = vr(b - 700)

            val buildId = fr(296) ?: continue
            if (!buildId.matches(Regex("""\d{2,8}"""))) continue

            val seeds = listOf(
                listOf(fr(301), fr(363), at(1143, 1197), at(1411, 1375)),
                listOf(at(1354, 1463), fr(437), fr(200), at(1397, 1249)),
                listOf(at(1053, 1193), fr(227), at(1385, 1384), at(1535, 1381)),
                listOf(at(1106, 1237), at(1357, 1286), fr(245), fr(399)),
            ).map { parts ->
                parts.filterNotNull().joinToString("")
            }

            if (seeds.size != SEED_COUNT) continue
            if (seeds.any { runCatching { Base64.decode(it, Base64.DEFAULT).size }.getOrDefault(0) < SEED_SIZE }) {
                continue
            }
            if (deriveMask(buildId, seeds) != null) {
                return BuildInfo(buildId, seeds)
            }
        }
        return null
    }

    private fun parseJsStringArray(literal: String): List<String>? {
        val out = ArrayList<String>()
        var i = 0
        while (i < literal.length) {
            val c = literal[i]
            if (c == '"' || c == '\'') {
                val quote = c
                i++
                val sb = StringBuilder()
                while (i < literal.length) {
                    val ch = literal[i]
                    when {
                        ch == '\\' && i + 1 < literal.length -> {
                            sb.append(literal[i + 1])
                            i += 2
                        }
                        ch == quote -> {
                            i++
                            break
                        }
                        else -> {
                            sb.append(ch)
                            i++
                        }
                    }
                }
                out.add(sb.toString())
            } else {
                i++
            }
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun hmacSha256(key: ByteArray, message: String): ByteArray {
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(message.toByteArray(Charsets.UTF_8))
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
