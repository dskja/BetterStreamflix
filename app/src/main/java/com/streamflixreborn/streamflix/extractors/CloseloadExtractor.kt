package com.streamflixreborn.streamflix.extractors

import android.util.Base64
import androidx.media3.common.MimeTypes
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.providers.RidomoviesProvider
import com.streamflixreborn.streamflix.utils.JsUnpacker
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.nio.charset.Charset

class CloseloadExtractor : Extractor() {

    override val name = "Closeload"
    override val mainUrl = "https://closeload.top/"

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)
        val document = service.get(link, RidomoviesProvider.URL)
        val html = document.toString()
        
        val unpacker = JsUnpacker(html)
        val unpacked = if (unpacker.detect()) unpacker.unpack() ?: html else html
        
        // --- 1. DYNAMIC PARAMETER DETECTION ---
        var magicNum = 399756995L
        var offset = 5
        val matchConst = Regex("""(\d+)\s*%\s*\(\s*i\s*\+\s*(\d+)\s*\)""").find(unpacked)
        if (matchConst != null) {
            magicNum = matchConst.groupValues[1].toLong()
            offset = matchConst.groupValues[2].toInt()
        }

        // --- 2. CANDIDATE COLLECTION ---
        val inputs = mutableListOf<String>()

        // A. DC Hello Pattern
        val varNameMatch = Regex("""myPlayer\.src\(\{\s*src:\s*(\w+)\s*,""").find(unpacked)
        if (varNameMatch != null) {
            val varName = varNameMatch.groupValues[1]
            val dcHelloMatch = Regex("""var\s+$varName\s*=\s*dc_hello\("([^"]+)"\)""").find(unpacked)
            if (dcHelloMatch != null) {
                inputs.add(dcHelloMatch.groupValues[1])
            }
        }

        // B. Arrays of strings
        Regex("""\[\s*((?:"[^"]+",?\s*)+)\]""").findAll(unpacked).forEach { match ->
            val parts = Regex("\"([^\"]+)\"").findAll(match.groupValues[1]).map { it.groupValues[1] }.toList()
            if (parts.size > 5) {
                inputs.add(parts.joinToString(""))
            }
        }

        // C. Long strings in function calls
        Regex("""\(\s*"([a-zA-Z0-9+/=]{30,})"\s*\)""").findAll(unpacked).forEach { match ->
            inputs.add(match.groupValues[1])
        }

        // --- 3. EXECUTE BRUTE FORCE ---
        // Try to find the URL in gathered inputs using smart brute force
        var source = inputs.firstNotNullOfOrNull { smartBruteForce(it, magicNum, offset) }

        // D. Fallback: Search for Pure Base64 strings if nothing else worked
        if (source == null) {
             source = Regex("[\"'](aHR0[a-zA-Z0-9+/=]{20,})[\"']").findAll(unpacked)
                .mapNotNull { safeBase64Decode(it.groupValues[1]) }
                .map { String(it, Charsets.UTF_8) }
                .firstOrNull { it.startsWith("http") }
        }

        if (source == null) throw Exception("No video found")

        return Video(source, headers = mapOf("Referer" to mainUrl), type = MimeTypes.APPLICATION_M3U8)
    }

    /**
     * Smart Brute Force: Tries all permutations of:
     * 1. String Transforms (Reverse, ROT13)
     * 2. Base64 Decode
     * 3. (Optional) Intermediate Transforms + Second Base64 Decode
     * 4. Byte Transforms (Reverse, ROT13)
     * 5. (Optional) Decryption Loop
     *
     * Returns the extracted URL string if found, otherwise null.
     */
    private fun smartBruteForce(inputData: String, magicNum: Long, offset: Int): String? {
        val stringTransforms = listOf<(String) -> String>(
            { it },                         // No change
            { it.reversed() },              // Reverse
            { rot13(it) },                  // ROT13
            { rot13(it.reversed()) },       // Reverse -> ROT13
            { rot13(it).reversed() }        // ROT13 -> Reverse
        )

        val byteTransforms = listOf<(ByteArray) -> ByteArray>(
            { it },                         // No change
            { it.reversedArray() },         // Reverse bytes
            { rot13Bytes(it) },             // ROT13 bytes
            { rot13Bytes(it.reversedArray()) }, // Reverse -> ROT13 bytes
            { rot13Bytes(it).reversedArray() }  // ROT13 -> Reverse bytes
        )

        for (sTrans in stringTransforms) {
            for (bTrans in byteTransforms) {
                try {
                    // Phase 1: String Transform -> Base64
                    val sRes = sTrans(inputData)
                    val b64Res = safeBase64Decode(sRes) ?: continue

                    // Collect candidates for Phase 2 (Byte Transform & Loop)
                    // We store: (bytes, description)
                    val candidates = mutableListOf<ByteArray>()
                    candidates.add(b64Res) // Standard Logic

                    // Phase 1.5: Double Base64 Logic
                    try {
                        val firstDecodeStr = String(b64Res, Charsets.ISO_8859_1) // Keep byte values
                        
                        // Variation A: Direct Double Decode
                        val b64Res2 = safeBase64Decode(firstDecodeStr)
                        if (b64Res2 != null) candidates.add(b64Res2)

                        // Variation B: Reverse before Double Decode (B64 -> Reverse -> B64)
                        val b64Res2Reversed = safeBase64Decode(firstDecodeStr.reversed())
                        if (b64Res2Reversed != null) candidates.add(b64Res2Reversed)

                    } catch (e: Exception) { }

                    // Phase 2: Byte Transform -> Decryption Loop -> Validate
                    for (candidateBytes in candidates) {
                         val finalBytes = bTrans(candidateBytes)
                         
                         // Try WITH decryption loop
                         try {
                             val adjusted = unmixLoop(finalBytes, magicNum, offset)
                             val url = String(adjusted, Charsets.UTF_8).trim()
                             if (url.startsWith("http") && url.contains(".mp4")) {
                                 return url
                             }
                         } catch (e: Exception) {}

                         // Try WITHOUT decryption loop
                         try {
                             val urlPlain = String(finalBytes, Charsets.UTF_8).trim()
                             if (urlPlain.startsWith("http") && urlPlain.contains(".mp4")) {
                                 return urlPlain
                             }
                         } catch (e: Exception) {}
                    }

                } catch (e: Exception) {
                    continue
                }
            }
        }
        return null
    }

    private fun safeBase64Decode(str: String): ByteArray? = try {
        Base64.decode(str, Base64.DEFAULT)
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun rot13(input: String): String = input.map {
        when (it) {
            in 'A'..'Z' -> 'A' + (it - 'A' + 13) % 26
            in 'a'..'z' -> 'a' + (it - 'a' + 13) % 26
            else -> it
        }
    }.joinToString("")

    private fun rot13Bytes(data: ByteArray): ByteArray {
        val res = ByteArray(data.size)
        for (i in data.indices) {
            val b = data[i].toInt()
            res[i] = when (b) {
                in 65..90 -> (65 + (b - 65 + 13) % 26).toByte()
                in 97..122 -> (97 + (b - 97 + 13) % 26).toByte()
                else -> b.toByte()
            }
        }
        return res
    }

    private fun unmixLoop(decodedBytes: ByteArray, magicNum: Long, offset: Int): ByteArray {
        val finalBytes = ByteArray(decodedBytes.size)
        for (i in decodedBytes.indices) {
            val b = decodedBytes[i].toInt() and 0xFF
            val adjustment = (magicNum % (i + offset)).toInt()
            finalBytes[i] = ((b - adjustment + 256) % 256).toByte()
        }
        return finalBytes
    }
    
    private interface Service {
        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder().build()
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()
                return retrofit.create(Service::class.java)
            }
        }
        @GET
        suspend fun get(@Url url: String, @Header("referer") referer: String): Document
    }
}
