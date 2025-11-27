package com.streamflixreborn.streamflix.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.streamflixreborn.streamflix.models.Video
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

class AmazonDriveExtractor : Extractor() {

    override val name = "AmazonDrive"
    override val mainUrl = "https://www.amazon.com"

    override suspend fun extract(link: String): Video {
        val shareIdMatch = Regex("""/shares/([^/]+)""").find(link)
        val shareId = shareIdMatch?.groupValues?.get(1)
            ?: throw Exception("ShareId not found in URL")

        val service = Service.build(mainUrl)

        val shareResponse = service.getShare(
            shareId = shareId,
            resourceVersion = "V2",
            contentType = "JSON",
            asset = "ALL"
        )
        val shareJson = JsonParser.parseString(shareResponse.string()).asJsonObject
        val nodeId = shareJson.getAsJsonObject("nodeInfo")?.get("id")?.asString
            ?: throw Exception("Node ID not found in share response")

        val childrenResponse = service.getChildren(
            nodeId = nodeId,
            resourceVersion = "V2",
            contentType = "JSON",
            limit = "200",
            sort = """["kind DESC", "modifiedDate DESC"]""",
            asset = "ALL",
            tempLink = "true",
            shareId = shareId
        )
        val childrenJson = JsonParser.parseString(childrenResponse.string()).asJsonObject

        val tempLink = childrenJson.getAsJsonArray("data")
            ?.get(0)?.asJsonObject
            ?.get("tempLink")?.asString
            ?: throw Exception("No tempLink found in data[0]")

        return Video(
            source = tempLink
        )
    }

    private interface Service {

        companion object {
            private const val DEFAULT_USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("Accept", "application/json, text/plain, */*")
                            .header("Accept-Language", "en-US,en;q=0.9")
                            .header("User-Agent", DEFAULT_USER_AGENT)
                            .header("Referer", "$baseUrl/clouddrive")
                            .build()
                        chain.proceed(request)
                    }
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET("drive/v1/shares/{shareId}")
        suspend fun getShare(
            @Path("shareId") shareId: String,
            @Query("resourceVersion") resourceVersion: String,
            @Query("ContentType") contentType: String,
            @Query("asset") asset: String
        ): ResponseBody

        @GET("drive/v1/nodes/{nodeId}/children")
        suspend fun getChildren(
            @Path("nodeId") nodeId: String,
            @Query("resourceVersion") resourceVersion: String,
            @Query("ContentType") contentType: String,
            @Query("limit") limit: String,
            @Query("sort") sort: String,
            @Query("asset") asset: String,
            @Query("tempLink") tempLink: String,
            @Query("shareId") shareId: String
        ): ResponseBody
    }
}

