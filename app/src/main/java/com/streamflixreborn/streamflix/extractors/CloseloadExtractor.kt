package com.streamflixreborn.streamflix.extractors

import android.net.Uri
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
    override val aliasUrls = listOf("https://ridorapid.closeload.top/")

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)
        val document = service.get(link, RidomoviesProvider.URL)
        val html = document.toString()
        var searchHtml = html
        
        // Find and unpack all eval blocks for Closeload/Ridorapid
        val evalRegex = Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*e""")
        evalRegex.findAll(html).forEach { match ->
            val endIdx = (match.range.first + 5000).coerceAtMost(html.length)
            val chunk = html.substring(match.range.first, endIdx)
            val unpacker = JsUnpacker(chunk)
            if (unpacker.detect()) {
                unpacker.unpack()?.let {
                    searchHtml += "\n" + it
                }
            }
        }

        // --- NEW CLOSLOAD EXTRACTOR LOGIC ---
        // Find the JS decryption function (handle optional semicolon)
        val funcMatch = Regex("""function\s+(dc_[a-zA-Z0-9_]+)\(value_parts\)\s*\{(.*?return unmix;?)\s*\}""", RegexOption.DOT_MATCHES_ALL).find(searchHtml)
            ?: throw Exception("Decryption function not found")

        val funcName = funcMatch.groupValues[1]
        val funcBody = funcMatch.groupValues[2]

        // Parse operations in order
        val operations = mutableListOf<Pair<String, Int?>>()
        val opRegex = Regex("""(atob\()|(reverse\(\))|(replace\(\/\[a-zA-Z\]\/g.*?o\s*-\s*base\s*\+\s*(\d+)\s*\)\s*%\s*26)""", RegexOption.DOT_MATCHES_ALL)
        opRegex.findAll(funcBody).forEach { match ->
            if (match.groupValues[1].isNotEmpty()) {
                operations.add(Pair("atob", null))
            } else if (match.groupValues[2].isNotEmpty()) {
                operations.add(Pair("reverse", null))
            } else if (match.groupValues[3].isNotEmpty()) {
                val rotVal = match.groupValues[4].toInt()
                operations.add(Pair("rot", rotVal))
            }
        }

        // Extract unmix loop constants
        var accInit = 2
        var accAdd = 9
        Regex("""var\s+acc\s*=\s*(\d+)""").find(funcBody)?.let { accInit = it.groupValues[1].toInt() }
        Regex("""acc\s*=\s*\(\s*acc\s*\+\s*(\d+)\s*\)\s*%\s*256""").find(funcBody)?.let { accAdd = it.groupValues[1].toInt() }

        // Find the array matches
        val arrayMatches = Regex("""$funcName\(\s*\[\s*((?:"[^"]+",?\s*)+)\s*\]\s*\)""").findAll(searchHtml)
        
        var source: String? = null
        
        fun safeB64Decode(s: String): ByteArray {
            val cleanStr = s.replace(Regex("""\s+"""), "")
            val pad = cleanStr.length % 4
            val paddedStr = if (pad > 0) cleanStr + "=".repeat(4 - pad) else cleanStr
            return Base64.decode(paddedStr, Base64.DEFAULT)
        }

        fun safeB64DecodeBytes(b: ByteArray): ByteArray {
            val s = String(b, Charsets.ISO_8859_1)
            return safeB64Decode(s)
        }

        for (arrayMatch in arrayMatches) {
            val partsStr = arrayMatch.groupValues[1]
            val parts = Regex(""""([^"]+)"""").findAll(partsStr).map { it.groupValues[1] }.toList()
            val value = parts.joinToString("").replace("\\/", "/")
            
            var resultStr: String? = value
            var resultBytes: ByteArray? = null
            var success = true
            
            for ((op, param) in operations) {
                when (op) {
                    "atob" -> {
                        try {
                            resultBytes = if (resultStr != null) {
                                safeB64Decode(resultStr)
                            } else {
                                safeB64DecodeBytes(resultBytes!!)
                            }
                            resultStr = String(resultBytes, Charsets.ISO_8859_1)
                        } catch (e: Exception) {
                            success = false
                            break
                        }
                    }
                    "reverse" -> {
                        resultStr = resultStr?.reversed() ?: String(resultBytes!!, Charsets.ISO_8859_1).reversed()
                        resultBytes = null
                    }
                    "rot" -> {
                        val rotOffset = param!!
                        val currentStr = resultStr ?: String(resultBytes!!, Charsets.ISO_8859_1)
                        val rotResult = StringBuilder()
                        for (c in currentStr) {
                            if (c in 'a'..'z') {
                                rotResult.append((((c - 'a') + rotOffset) % 26 + 'a'.code).toChar())
                            } else if (c in 'A'..'Z') {
                                rotResult.append((((c - 'A') + rotOffset) % 26 + 'A'.code).toChar())
                            } else {
                                rotResult.append(c)
                            }
                        }
                        resultStr = rotResult.toString()
                        resultBytes = null
                    }
                }
            }
            
            if (!success) continue
            
            val finalBytes = resultBytes ?: resultStr!!.toByteArray(Charsets.ISO_8859_1)
            var acc = accInit
            val unmix = StringBuilder()
            for (b in finalBytes) {
                val bInt = b.toInt() and 0xFF
                acc = (acc + accAdd) % 256
                val plain = bInt xor acc
                acc = (acc + bInt) % 256
                unmix.append(plain.toChar())
            }
            
            val urlStr = unmix.toString().trim()
            if (urlStr.startsWith("http")) {
                source = urlStr
                break
            }
        }

        if (source == null) throw Exception("No video found")

        val url = Uri.parse(link)
        val referer = "${url.scheme}://${url.host}/"

        return Video(source, headers = mapOf("Referer" to referer), type = MimeTypes.APPLICATION_M3U8)
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
