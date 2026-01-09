package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DecryptHelper
import com.streamflixreborn.streamflix.utils.DnsResolver
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.net.URL

class VoeExtractor : Extractor() {

    override val name = "VOE"
    override val mainUrl = "https://voe.sx/"
    override val aliasUrls = listOf("https://jilliandescribecompany.com", "https://mikaylaarealike.com","https://christopheruntilpoint.com","https://walterprettytheir.com","https://crystaltreatmenteast.com")


    override suspend fun extract(link: String): Video {
        val service = VoeExtractorService.build(mainUrl, link)
        
        // Extract path from original link (handles both mainUrl and alias URLs)
        val parsedUrl = URL(link)
        val originalPath = parsedUrl.path + if (parsedUrl.query != null) "?${parsedUrl.query}" else ""

        val source = service.getSource(originalPath)
        val scriptTag = source.selectFirst("script[type=application/json]")
        val encodedStringInScriptTag = scriptTag?.data()?.trim().orEmpty()
        val encodedString = DecryptHelper.findEncodedRegex(source.html())
        val decryptedContent = if (encodedString != null) {
            DecryptHelper.decrypt(encodedString)
        } else {
            DecryptHelper.decrypt(encodedStringInScriptTag)
        }
        val m3u8 = decryptedContent.get("source")?.asString.orEmpty()

        return Video(
            source = m3u8,
            subtitles = listOf()
        )
    }


    private interface VoeExtractorService {

        companion object {
            suspend fun build(baseUrl: String, originalLink: String): VoeExtractorService {
                val client = OkHttpClient.Builder()
                    .dns(DnsResolver.doh)
                    .build()

                val retrofitVOE = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(JsoupConverterFactory.create())

                    .build()
                val retrofitVOEBuiled = retrofitVOE.create(VoeExtractorService::class.java)

                // Extract path from original link (handles both mainUrl and alias URLs)
                val relativePath = if (originalLink.startsWith(baseUrl)) {
                    originalLink.replace(baseUrl, "")
                } else {
                    // If link doesn't start with baseUrl, extract path directly (alias URL)
                    val parsedUrl = URL(originalLink)
                    parsedUrl.path + if (parsedUrl.query != null) "?${parsedUrl.query}" else ""
                }

                val retrofitVOEhtml = retrofitVOEBuiled.getSource(relativePath).html()

                val regex = Regex("""https://([a-zA-Z0-9.-]+)(?:/[^'"]*)?""")
                val match = regex.find(retrofitVOEhtml)
                val redirectBaseUrl = if (match != null) {
                    "https://${match.groupValues[1]}/"
                } else {
                    throw Exception("Base url not found for VOE")
                }

                val retrofitRedirected = Retrofit.Builder()
                    .baseUrl(redirectBaseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                return retrofitRedirected.create(VoeExtractorService::class.java)
            }
        }

        @GET
        suspend fun getSource(@Url url: String): Document
    }
}
