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

class CloseloadExtractor : Extractor() {

    override val name = "Closeload"
    override val mainUrl = "https://closeload.top/"

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)
        val document = service.get(link, RidomoviesProvider.URL)
        val html = document.toString()

        val unpacked = JsUnpacker(html).unpack() ?: html

        // 1. DYNAMIC PARAMETER DETECTION
        // Try to find the match constants used in the unmix loop: (charCode - 399756995 % (i + 5))
        var magicNum = 399_756_995L
        var offset = 5
        val matchConst = Regex("""(\d+)\s*%\s*\(\s*i\s*\+\s*(\d+)\s*\)""").find(unpacked)
        if (matchConst != null) {
            magicNum = matchConst.groupValues[1].toLong()
            offset = matchConst.groupValues[2].toInt()
        }

        // 2. DATA CANDIDATE COLLECTION
        val candidates = mutableListOf<String>()

        // A. Arrays of strings (usually split URLs)
        val arrayRegex = Regex("""\[\s*((?:"[^"]+",?\s*)+)\]""")
        arrayRegex.findAll(unpacked).forEach { match ->
            val parts = Regex("\"([^\"]+)\"").findAll(match.groupValues[1])
                .map { it.groupValues[1] }
                .toList()
            if (parts.size > 5) {
                candidates.add(parts.joinToString(""))
            }
        }

        // B. Long strings in function calls
        val stringCallRegex = Regex("""\(\s*"([a-zA-Z0-9+/=]{30,})"\s*\)""")
        stringCallRegex.findAll(unpacked).forEach { match ->
            candidates.add(match.groupValues[1])
        }

        // 3. URL EXTRACTION
        // Try smart brute force on all candidates, fallback to pure base64
        val source = candidates.firstNotNullOfOrNull { smartBruteForce(it, magicNum, offset) }
            ?: extractPureBase64(unpacked)
            ?: error("Unable to fetch video URL")

        return Video(
            source = source,
            headers = mapOf("Referer" to mainUrl),
            type = MimeTypes.APPLICATION_M3U8
        )
    }

    /**
     * Tries all possible combinations of Reverse, ROT13, and Base64 transformations.
     * This makes the extractor resilient to changes in obfuscation order.
     */
    private fun smartBruteForce(inputData: String, magicNum: Long, offset: Int): String? {
        val stringTransforms = listOf<(String) -> String>(
            { it },                             // No change
            { it.reversed() },                  // Reverse string
            { rot13(it) },                      // ROT13
            { rot13(it.reversed()) },           // Reverse -> ROT13
            { rot13(it).reversed() }            // ROT13 -> Reverse
        )

        val byteTransforms = listOf<(ByteArray) -> ByteArray>(
            { it },                             // No change
            { it.reversedArray() },             // Reverse byte array
            { rot13Bytes(it) },                 // ROT13 bytes
            { rot13Bytes(it.reversedArray()) }, // Reverse -> ROT13 bytes
            { rot13Bytes(it).reversedArray() }  // ROT13 -> Reverse bytes
        )

        for (sTrans in stringTransforms) {
            for (bTrans in byteTransforms) {
                try {
                    val sRes = sTrans(inputData)
                    val b64Res = safeBase64Decode(sRes) ?: continue
                    val finalBytesCandidate = bTrans(b64Res)

                    // Try with the decryption loop (using dynamic constants)
                    val adjusted = unmixLoop(finalBytesCandidate, magicNum, offset)
                    val url = String(adjusted, Charsets.UTF_8).trim()
                    if (url.startsWith("http") && url.contains(".mp4")) {
                        return url
                    }

                    // Try without decryption loop (some variants don't use it)
                    val urlPlain = String(finalBytesCandidate, Charsets.UTF_8).trim()
                    if (urlPlain.startsWith("http") && urlPlain.contains(".mp4")) {
                        return urlPlain
                    }
                    
                    // Try with unmix loop BEFORE byte transforms (rare but possible order variation)
                    // If the order is Base64 -> Unmix -> Reverse/ROT13... unlikely given the loop nature but cheap to try
                } catch (e: Exception) {
                    continue
                }
            }
        }
        return null
    }

    private fun rot13Bytes(input: ByteArray): ByteArray {
        val output = input.clone()
        for (i in output.indices) {
            val b = output[i].toInt()
            output[i] = when (b) {
                in 'A'.code..'Z'.code -> ('A'.code + (b - 'A'.code + 13) % 26).toByte()
                in 'a'.code..'z'.code -> ('a'.code + (b - 'a'.code + 13) % 26).toByte()
                else -> output[i]
            }
        }
        return output
    }

    private fun extractPureBase64(unpacked: String): String? {
        val pureB64 = Regex("""["'](aHR0[a-zA-Z0-9+/=]{20,})["']""").find(unpacked)
        if (pureB64 != null) {
            val decoded = safeBase64Decode(pureB64.groupValues[1])
            if (decoded != null) {
                val urlCandidate = String(decoded, Charsets.UTF_8).trim()
                if (urlCandidate.startsWith("http")) {
                    return urlCandidate
                }
            }
        }
        return null
    }

    private fun unmixLoop(decodedBytes: ByteArray, magicNum: Long, offset: Int): ByteArray {
        val finalBytes = ByteArray(decodedBytes.size)
        for (i in decodedBytes.indices) {
            val b = decodedBytes[i].toInt() and 0xFF
            val adjustment = (magicNum % (i + offset)).toInt()
            finalBytes[i] = ((b - adjustment + 255 + 1) % 256).toByte() // Using +256
        }
        return finalBytes
    }

    private fun safeBase64Decode(str: String): ByteArray? {
        return try {
            val cleaned = str.replace(Regex("[^a-zA-Z0-9+/]"), "")
            Base64.decode(cleaned, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    private fun rot13(input: String): String = input.map {
        when (it) {
            in 'A'..'Z' -> 'A' + (it - 'A' + 13) % 26
            in 'a'..'z' -> 'a' + (it - 'a' + 13) % 26
            else -> it
        }
    }.joinToString("")

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
