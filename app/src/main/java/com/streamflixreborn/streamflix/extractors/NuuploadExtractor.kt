package com.streamflixreborn.streamflix.extractors

import android.util.Base64
import androidx.media3.common.MimeTypes
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.JsUnpacker
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class NuuploadExtractor : Extractor() {

    override val name = "Nuupload"
    override val mainUrl = "https://nupload.top/"
    override val aliasUrls = listOf(
        "https://nupupload.top/",
        "https://nupload.top",
        "https://ap.nupload.me/",
        "https://nupload.me/",
    )

    override suspend fun extract(link: String): Video {
        decodeUrlParameter(link)?.let { nested ->
            return Extractor.extract(nested)
        }

        val page = fetchPage(link)
        val document = page.document
        val htmlCandidates = buildList {
            add(page.html)
            document.select("script").forEach { script ->
                val data = script.data()
                if (data.isNotBlank()) add(data)
            }
        }.flatMap { html ->
            val unpacked = JsUnpacker(html).takeIf { it.detect() }?.unpack()
            listOfNotNull(html, unpacked)
        }.distinct()

        findDelegatedLink(document, page.finalUrl, htmlCandidates)?.let { delegated ->
            return Extractor.extract(delegated)
        }

        val rawSourceUrl = findDirectSource(document, page.finalUrl, htmlCandidates)
            ?: throw Exception("No playable source found for Nuupload")
        val sourceUrl = resolvePlayableUrl(rawSourceUrl, page.finalUrl)

        val videoType = when {
            sourceUrl.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            isLikelyHlsSessionUrl(sourceUrl, page.finalUrl, htmlCandidates) -> MimeTypes.APPLICATION_M3U8
            sourceUrl.contains(".mp4", ignoreCase = true) -> MimeTypes.VIDEO_MP4
            else -> null
        }

        val subtitles = document.select("track[src]").mapNotNull { track ->
            val src = track.attr("src").trim()
            if (src.isBlank()) return@mapNotNull null
            Video.Subtitle(
                label = track.attr("label").ifBlank { track.attr("srclang").ifBlank { "Subtitle" } },
                file = absolutize(src, page.finalUrl),
                default = track.hasAttr("default"),
                initialDefault = track.hasAttr("default")
            )
        }

        return Video(
            source = sourceUrl,
            subtitles = subtitles,
            headers = mapOf(
                "Referer" to "${URL(page.finalUrl).protocol}://${URL(page.finalUrl).host}/",
                "Origin" to "${URL(page.finalUrl).protocol}://${URL(page.finalUrl).host}"
            ),
            type = videoType
        )
    }

    private fun findDirectSource(document: Document, pageUrl: String, htmlCandidates: List<String>): String? {
        document.selectFirst("source[src], video[src]")?.attr("src")?.trim()?.takeIf { it.isNotBlank() }?.let {
            return absolutize(it, pageUrl)
        }

        val directPatterns = listOf(
            Regex("""["']file["']\s*[:=]\s*["']((?:https?://|/)[^"']+\.(?:m3u8|mp4)[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""["']src["']\s*[:=]\s*["']((?:https?://|/)[^"']+\.(?:m3u8|mp4)[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""["']hls\d*["']\s*[:=]\s*["']((?:https?://|/)[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""sources?\s*:\s*\[\s*\{[^}]*file\s*:\s*["']((?:https?://|/)[^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""https?://[^"'\\\s<]+\.m3u8[^"'\\\s<]*""", RegexOption.IGNORE_CASE),
            Regex("""https?://[^"'\\\s<]+\.mp4[^"'\\\s<]*""", RegexOption.IGNORE_CASE)
        )

        htmlCandidates.forEach { html ->
            extractObfuscatedNuuploadSource(html, pageUrl)?.let { return it }

            directPatterns.forEach { pattern ->
                val match = pattern.find(html)?.groupValues?.lastOrNull()?.takeIf { it.isNotBlank() }
                if (match != null) {
                    return absolutize(match, pageUrl)
                }
            }

            Regex("""["'](aHR0[a-zA-Z0-9+/=]{20,})["']""").findAll(html).forEach { match ->
                safeBase64Decode(match.groupValues[1])?.let { decoded ->
                    val value = decoded.trim()
                    if (value.startsWith("http") && (value.contains(".m3u8") || value.contains(".mp4"))) {
                        return absolutize(value, pageUrl)
                    }
                }
            }
        }

        return null
    }

    private fun extractObfuscatedNuuploadSource(html: String, pageUrl: String): String? {
        val offset = Regex("""String\.fromCharCode\(parseInt\(atob\(value\)\.replace\(/\\D/g,''\)\)\s*-\s*(\d+)\)""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: return null

        val arrayContent = Regex("""var\s+\w+\s*=\s*\[((?:"[^"]*"|'[^']*')(?:\s*,\s*(?:"[^"]*"|'[^']*'))*)]""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null

        val encodedParts = Regex("""["']([^"']+)["']""")
            .findAll(arrayContent)
            .map { it.groupValues[1] }
            .toList()

        if (encodedParts.isEmpty()) {
            return null
        }

        val baseUrl = buildString {
            encodedParts.forEach { encoded ->
                val decoded = safeBase64Decode(encoded) ?: return@forEach
                val numeric = decoded.replace(Regex("""\D"""), "")
                val codePoint = numeric.toIntOrNull()?.minus(offset) ?: return@forEach
                append(codePoint.toChar())
            }
        }.trim()

        if (!baseUrl.startsWith("http")) {
            return null
        }

        val session = Regex("""var\s+sesz\s*=\s*["']([^"']+)["']""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()

        val finalUrl = if (!session.isNullOrBlank()) {
            val separator = if ('?' in baseUrl) '&' else '?'
            "$baseUrl${separator}s=$session"
        } else {
            baseUrl
        }

        return absolutize(finalUrl, pageUrl)
    }

    private fun isLikelyHlsSessionUrl(sourceUrl: String, pageUrl: String, htmlCandidates: List<String>): Boolean {
        if (!pageUrl.contains("/watch/")) return false
        if (!sourceUrl.contains("?s=")) return false

        return htmlCandidates.any { html ->
            html.contains("jwplayer(", ignoreCase = true) &&
                html.contains("type:\"hls\"", ignoreCase = true) ||
                html.contains("type:'hls'", ignoreCase = true) ||
                html.contains("type:\"application/vnd.apple.mpegurl\"", ignoreCase = true) ||
                html.contains("file:", ignoreCase = true) && html.contains("?s=", ignoreCase = true)
        }
    }

    private fun resolvePlayableUrl(sourceUrl: String, pageUrl: String): String {
        if (!sourceUrl.startsWith("http")) {
            return sourceUrl
        }

        val client = OkHttpClient.Builder()
            .dns(DnsResolver.doh)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val originUrl = URL(pageUrl)
        val referer = "${originUrl.protocol}://${originUrl.host}/"
        val origin = "${originUrl.protocol}://${originUrl.host}"

        return runCatching {
            client.newCall(
                Request.Builder()
                    .url(sourceUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", referer)
                    .header("Origin", origin)
                    .header("Accept", "*/*")
                    .build()
            ).execute().use { response ->
                response.request.url.toString()
            }
        }.getOrDefault(sourceUrl)
    }

    private fun findDelegatedLink(document: Document, pageUrl: String, htmlCandidates: List<String>): String? {
        val domCandidates = listOfNotNull(
            document.selectFirst("iframe[src]")?.attr("src"),
            document.selectFirst("a[href*='embed'], a[href*='watch'], a[href*='voe'], a[href*='filemoon']")?.attr("href"),
            document.selectFirst("meta[http-equiv=refresh]")?.attr("content")?.substringAfter("url=", "")
        )

        domCandidates
            .asSequence()
            .map { it.trim() }
            .mapNotNull { normalizeDelegatedCandidate(it, pageUrl) }
            .firstOrNull()
            ?.let { return it }

        htmlCandidates.forEach { html ->
            listOf(
                Regex("""window\.location(?:\.href|\.replace)?\(['"]([^'"]+)['"]\)""", RegexOption.IGNORE_CASE),
                Regex("""window\.location\.href\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
                Regex("""location\.href\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
                Regex("""["']((?:https?:)?//[^"'\\\s<]+(?:voe|streamtape|filemoon|vidhide|streamwish|upzone|nupload)[^"'\\\s<]*)["']""", RegexOption.IGNORE_CASE)
            ).forEach { pattern ->
                pattern.findAll(html).forEach { match ->
                    val candidate = match.groupValues.lastOrNull().orEmpty()
                    normalizeDelegatedCandidate(candidate, pageUrl)?.let { return it }
                }
            }

            Regex("""["'](aHR0[a-zA-Z0-9+/=]{20,})["']""").findAll(html).forEach { match ->
                safeBase64Decode(match.groupValues[1])?.let { decoded ->
                    normalizeDelegatedCandidate(decoded, pageUrl)?.let { return it }
                }
            }
        }

        return null
    }

    private fun normalizeDelegatedCandidate(candidate: String, pageUrl: String): String? {
        val trimmed = candidate.trim().removePrefix("src=").trim('"', '\'')
        if (trimmed.isBlank()) return null

        decodeUrlParameter(trimmed)?.let { return it }

        val absolute = absolutize(trimmed, pageUrl)
        if (!absolute.startsWith("http")) return null
        if (absolute == pageUrl) return null
        if (isInternalNuuploadUrl(absolute)) return null
        return absolute
    }

    private fun decodeUrlParameter(candidate: String): String? {
        val parsed = candidate.toHttpUrlOrNull() ?: return null
        val nested = parsed.queryParameter("url") ?: return null
        return URLDecoder.decode(nested, "UTF-8").takeIf { it.startsWith("http") }
    }

    private fun absolutize(value: String, pageUrl: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> {
                val url = URL(pageUrl)
                "${url.protocol}://${url.host}$trimmed"
            }
            else -> trimmed
        }
    }

    private fun safeBase64Decode(value: String): String? {
        return runCatching {
            String(Base64.decode(value, Base64.DEFAULT)).trim()
        }.getOrNull()
    }

    private fun isInternalNuuploadUrl(url: String): Boolean {
        val host = url.toHttpUrlOrNull()?.host?.lowercase().orEmpty()
        if (host.isBlank()) return false
        return host.contains("nupload.top") || host.contains("nupload.me") || host.contains("nupupload.top")
    }

    private fun fetchPage(url: String): FetchedPage {
        val client = OkHttpClient.Builder()
            .dns(DnsResolver.doh)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val response = client.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", mainUrl)
                .build()
        ).execute()

        val body = response.body?.string().orEmpty()
        val finalUrl = response.request.url.toString()
        val document = Jsoup.parse(body, finalUrl)

        return FetchedPage(
            html = body,
            finalUrl = finalUrl,
            document = document
        )
    }

    private data class FetchedPage(
        val html: String,
        val finalUrl: String,
        val document: Document
    )

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    private interface Service {
        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder()
                    .dns(DnsResolver.doh)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                    .create(Service::class.java)
            }
        }

        @Headers(
            "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Referer: https://nupload.top/"
        )
        @GET
        suspend fun getPage(@Url url: String): Document
    }
}
