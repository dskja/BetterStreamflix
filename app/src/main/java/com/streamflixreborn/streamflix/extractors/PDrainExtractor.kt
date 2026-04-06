package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import androidx.media3.common.MimeTypes
import java.lang.Exception

class PDrainExtractor : Extractor() {

    override val name = "PDrain"
    override val mainUrl = "https://pixeldrain.com"

    override suspend fun extract(link: String): Video {
        try {
            // Extraemos el ID del link (ej: https://pixeldrain.com/u/ExkhFTyB?embed -> ExkhFTyB)
            val id = link.substringAfter("/u/").substringBefore("?")

            // La API de pixeldrain entrega el archivo directamente en esta ruta
            val videoSource = "$mainUrl/api/file/$id"

            return Video(
                source = videoSource,
                type = MimeTypes.VIDEO_MP4, // PixelDrain sirve MP4 por defecto en su API
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer" to link,
                    "Origin" to mainUrl,
                    "Connection" to "keep-alive"
                )
            )

        } catch (e: Exception) {
            throw Exception("PDrainExtractor failed: ${e.message}", e)
        }
    }
}
