package com.betterstreamflix.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

/**
 * Web content fetcher — fetches and parses web pages for provider scraping.
 */
object WebContentFetcher {

    private val client by lazy { HttpClientFactory.createDefault() }

    /**
     * Fetch HTML content from a URL.
     */
    fun fetchHtml(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val builder = Request.Builder().url(url)
            headers.forEach { (key, value) -> builder.header(key, value) }
            val response = client.newCall(builder.build()).execute()
            if (response.isSuccessful) response.body?.string() else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetch content as a stream (for large files).
     */
    fun fetchStream(url: String, headers: Map<String, String> = emptyMap()): InputStream? {
        return try {
            val builder = Request.Builder().url(url)
            headers.forEach { (key, value) -> builder.header(key, value) }
            val response = client.newCall(builder.build()).execute()
            if (response.isSuccessful) response.body?.byteStream() else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if a URL is reachable.
     */
    fun isReachable(url: String): Boolean {
        return try {
            val request = Request.Builder().url(url).head().build()
            val response = client.newCall(request).execute()
            val code = response.code
            response.close()
            code in 200..399
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the final URL after redirects.
     */
    fun resolveRedirects(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            response.close()
            finalUrl
        } catch (e: Exception) {
            null
        }
    }
}
