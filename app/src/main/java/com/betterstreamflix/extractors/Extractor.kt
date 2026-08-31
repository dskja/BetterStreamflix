package com.betterstreamflix.extractors

import android.util.Log
import com.betterstreamflix.models.Video

abstract class Extractor {

    abstract val name: String
    abstract val mainUrl: String
    open val aliasUrls: List<String> = emptyList()
    open val rotatingDomain: List<Regex> = emptyList()

    // THIS is the main method all subclasses must implement
    abstract suspend fun extract(link: String): Video

    // THIS is a convenience helper
    open suspend fun extract(link: String, server: Video.Server? = null): Video {
        return extract(link)
    }

    /**
     * Structured error thrown when [Extractor.extract] cannot resolve a playable [Video].
     * Carries the original link and the names of every extractor that was attempted (if any),
     * so callers/logs can tell "no extractor matched" apart from "extractor(s) matched but failed".
     */
    class ExtractionFailedException(
        val link: String,
        val attemptedExtractors: List<String>,
        cause: Throwable? = null,
    ) : Exception(
        if (attemptedExtractors.isEmpty()) {
            "No extractors found for URL: $link"
        } else {
            "Extraction failed for URL: $link (tried: ${attemptedExtractors.joinToString()})"
        },
        cause,
    )

    companion object {
        private val extractors = listOf(
            JKPlayerExtractor(),
            RabbitstreamExtractor(),
            RabbitstreamExtractor.MegacloudExtractor(),
            RabbitstreamExtractor.DokicloudExtractor(),
            RabbitstreamExtractor.PremiumEmbedingExtractor(),
            UpzoneExtractor(),
            StreamhubExtractor(),
            VtubeExtractor(),
            NuuploadExtractor(),
            VoeExtractor(),
            StreamtapeExtractor(),
            VidozaExtractor(),
            VidsrcToExtractor(),
            VidplayExtractor(),
            NekostreamExtractor(),
            FilemoonExtractor(),
            VidplayExtractor.MyCloud(),
            VidplayExtractor.VidplayOnline(),
            MyFileStorageExtractor(),
            MoflixExtractor(),
            MStreamDayExtractor(),
            VidsrcNetExtractor(),
            StreamWishExtractor(),
            StreamWishExtractor.UqloadsXyz(),
            StreamWishExtractor.SwishExtractor(),
            StreamWishExtractor.HlswishExtractor(),
            StreamWishExtractor.PlayerwishExtractor(),
            StreamWishExtractor.SwiftPlayersExtractor(),
            TwoEmbedExtractor(),
            ChillxExtractor(),
            ChillxExtractor.JeanExtractor(),
            MoviesapiExtractor(),
            CloseloadExtractor(),
            LuluVdoExtractor(),
            DoodLaExtractor(),
            DoodLaExtractor.DoodLiExtractor(),
            VidPlyExtractor(),
            MagaSavorExtractor(),
            VidMoLyExtractor(),
            VidMoLyExtractor.ToDomain(),
            VideoSibNetExtractor(),
            SaveFilesExtractor(),
            BigWarpExtractor(),
            DoodLaExtractor.DoodExtractor(),
            LoadXExtractor(),
            VidHideExtractor(),
            VeevExtractor(),
            RidooExtractor(),
            USTRExtractor(),
            VidGuardExtractor(),
            OkruExtractor(),
            StreamSBExtractor(),
            Mp4UploadExtractor(),
            StreamlareExtractor(),
            NinjaStreamExtractor(),
            UchExtractor(),
            VixSrcExtractor(),
            GoodstreamExtractor(),
            LamovieExtractor(),
            UqloadExtractor(),
            MailRuExtractor(),
            MixDropExtractor(),
            SupervideoExtractor(),
            DroploadExtractor(),
            RpmvidExtractor(),
            YourUploadExtractor(),
            PlusPomlaExtractor(),
            OneuploadExtractor(),
            FsvidExtractor(),
            GoogleDriveExtractor(),
            PcloudExtractor(),
            AmazonDriveExtractor(),
            VidzyExtractor(),
            GuploadExtractor(),
            StreamUpExtractor(),
            EinschaltenExtractor(),
            VidLinkExtractor(),
            VidsrcRuExtractor(),
            VidflixExtractor(),
            VidrockExtractor(),
            VideasyExtractor(),
            VidzeeExtractor(),
            VidnestExtractor(),
            PrimeSrcExtractor(),
            VidoraExtractor(),
            GxPlayerExtractor(),
            UpZurExtractor(),
            DailymotionExtractor(),
            ApiVoirFilmExtractor(),
            StreamixExtractor(),
            ShareCloudyExtractor(),
            StreamrubyExtractor(),
            VidaraExtractor(),
            VidsonicExtractor(),
            HxfileExtractor(),
            ZillaExtractor(),
            PDrainExtractor(),
            MaxstreamExtractor(),
            VidxGoExtractor()
        )

        suspend fun extract(link: String, server: Video.Server? = null): Video {
            var finalLink = link
            
            // 1. RISOLUZIONE BRIDGE UNIVERSALE (StreamHG/Sync/Cuevana)
            // Facciamo questo PRIMA di cercare l'estrattore perché il link bridge (es. mysync.mov)
            // non appartiene a nessun estrattore specifico, ma il link risolto sì (es. filemoon).
            if (link.contains("mysync.mov/stream/")) {
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()
                    
                    val responseBody = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val request = okhttp3.Request.Builder()
                            .url(link)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                            .build()
                        client.newCall(request).execute().use { it.body?.string() }
                    } ?: ""
                    
                    val redirectUrl = responseBody.substringAfter("window.location.replace(\"", "").substringBefore("\"")
                        .ifEmpty { responseBody.substringAfter("window.location.href = \"", "").substringBefore("\"") }
                        .ifEmpty { responseBody.substringAfter("src=\"", "").substringBefore("\"") }
                    
                    if (redirectUrl.isNotEmpty() && redirectUrl.startsWith("http")) {
                        Log.d("Extractor", "Universal Bridge resolved: $link -> $redirectUrl")
                        finalLink = redirectUrl
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e("Extractor", "Universal Bridge error: ${e.message}")
                }
            }

            val urlRegex = Regex("^(https?://)?(www\\.)?")
            val compareUrl = finalLink.lowercase().replace(urlRegex, "")

            var foundExtractor: Extractor? = null

            for (extractor in extractors) {
                if (compareUrl.startsWith(extractor.mainUrl.replace(urlRegex, ""))) {
                    foundExtractor = extractor
                    break
                } else {
                    for (aliasUrl in extractor.aliasUrls) {
                        if (compareUrl.startsWith(aliasUrl.lowercase().replace(urlRegex, ""))) {
                            foundExtractor = extractor
                            break
                        }
                    }
                }
                if (foundExtractor != null) break
            }

            if (foundExtractor == null) {
                for (extractor in extractors) {
                    if (compareUrl.startsWith(
                            extractor.mainUrl.replace(
                                Regex("^(https?://)?(www\\.)?(.*?)(\\.[a-z]+)"),
                                "$3"
                            )
                        )
                    ) {
                        foundExtractor = extractor
                        break
                    } else {
                        for (aliasUrl in extractor.aliasUrls) {
                            if (compareUrl.startsWith(
                                    aliasUrl.replace(
                                        Regex("^(https?://)?(www\\.)?(.*?)(\\.[a-z]+)"),
                                        "$3"
                                    )
                                )
                            ) {
                                foundExtractor = extractor
                                break
                            }
                        }
                    }
                    if (foundExtractor != null) break
                }
            }

            if (foundExtractor == null) {
                for (extractor in extractors) {
                    if (extractor.rotatingDomain.any { it.containsMatchIn(compareUrl) }) {
                        foundExtractor = extractor
                        break
                    }
                }
            }

            if (foundExtractor == null) {
                for (extractor in extractors) {
                    if ((server?.name?.lowercase() ?: "").contains(extractor.name.lowercase())) {
                        foundExtractor = extractor
                        break
                    }
                }
            }

            if (foundExtractor != null) {
                Log.i("StreamFlixES", "[EXTRACTOR] -> Starting: ${foundExtractor.name} (URL: $finalLink)")
                val attempted = mutableListOf(foundExtractor.name)
                try {
                    val video = foundExtractor.extract(finalLink)
                    Log.i("StreamFlixES", "[VIDEO] -> Extracted: ${video.source}")
                    return video
                } catch (primaryError: Exception) {
                    if (primaryError is kotlinx.coroutines.CancellationException) throw primaryError
                    Log.w("Extractor", "Primary ${foundExtractor.name} failed: ${primaryError.message}")
                    // Try other extractors that share the same host / name hint before giving up.
                    val host = runCatching { java.net.URI(finalLink).host?.lowercase() }.getOrNull().orEmpty()
                    val candidates = extractors.filter { extractor ->
                        extractor !== foundExtractor && (
                            host.isNotBlank() && (
                                extractor.mainUrl.contains(host, ignoreCase = true) ||
                                    extractor.aliasUrls.any { it.contains(host, ignoreCase = true) }
                                ) ||
                                (server?.name?.lowercase()?.contains(extractor.name.lowercase()) == true)
                            )
                    }
                    var lastError: Throwable = primaryError
                    for (fallback in candidates) {
                        attempted += fallback.name
                        try {
                            Log.i("Extractor", "Trying fallback extractor ${fallback.name}")
                            val video = fallback.extract(finalLink)
                            Log.i("StreamFlixES", "[VIDEO] -> Extracted via fallback ${fallback.name}: ${video.source}")
                            return video
                        } catch (fallbackError: Exception) {
                            if (fallbackError is kotlinx.coroutines.CancellationException) throw fallbackError
                            Log.w("Extractor", "Fallback ${fallback.name} failed: ${fallbackError.message}")
                            lastError = fallbackError
                        }
                    }
                    // Every matching extractor (primary + host/name fallbacks) failed: surface a
                    // structured error instead of the raw first exception so logs/UI can tell this
                    // apart from "no extractor matched at all".
                    throw ExtractionFailedException(finalLink, attempted, lastError)
                }
            }

            throw ExtractionFailedException(finalLink, emptyList())
        }
    }
}
