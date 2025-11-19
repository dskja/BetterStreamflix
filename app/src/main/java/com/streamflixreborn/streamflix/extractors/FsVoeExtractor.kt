package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DecryptHelper
import com.streamflixreborn.streamflix.utils.DnsResolver
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class FsVoeExtractor : Extractor() {

    override val name = "VOE"
    override val mainUrl = "https://kakaflix.lol/voe3/"
    override val aliasUrls = listOf("https://kakaflix.lol/voe")


    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)
        var embedUrl = service.getResponse(link, link).raw().request.url.toString()
        val document = service.getSource(embedUrl)
        val scriptContent = document.select("script").joinToString("\n") { it.data() }

        val regex = Regex("""window.location.href\s*=\s*'(.*?)';""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(scriptContent) ?: throw Exception("URL not found for VOE")
        val realUrl = match.groupValues[1]

        val source = service.getSource(realUrl)
        val scriptTag = source.selectFirst("script")
        val encodedStringInScriptTag = scriptTag?.data()?.trim().orEmpty()
        val encodedString = DecryptHelper.findEncodedRegex(source.html())

        val decryptedContent = if (encodedString != null) {
            DecryptHelper.decrypt(encodedString)
        } else {
            DecryptHelper.decrypt(encodedStringInScriptTag)
        }

        val mp4 =  decryptedContent.get("source")?.asString.orEmpty();

        return Video(
            source = mp4,
            subtitles = listOf()
        )
    }

    private interface Service {

        companion object {
            suspend fun build(baseUrl: String): Service {
                val trustAllCerts = arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
                )

                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, SecureRandom())
                val sslSocketFactory = sslContext.socketFactory
                val client = OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                    .dns(DnsResolver.doh)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET
        suspend fun getSource(@Url url: String): Document

        @GET
        suspend fun getResponse(
            @Url url: String,
            @Header("Referer") referer: String
        ): Response<ResponseBody>
    }
}