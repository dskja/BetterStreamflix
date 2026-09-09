package com.dskja.betterstreamflix.download

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.dskja.betterstreamflix.utils.DnsResolver
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Builds HTTP data sources that inject per-download headers from [DownloadHeaderStore].
 * Media3 passes the download request id via custom cache key / we key headers by media3Id
 * and also merge a thread-local fallback set during prepare/enqueue.
 */
class DownloadDataSourceFactory(
    private val context: Context,
) : DataSource.Factory {
    private val appContext = context.applicationContext

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(DnsResolver.doh)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    var activeMedia3Id: String? = null

    @Volatile
    var activeHeaders: Map<String, String> = emptyMap()

    override fun createDataSource(): DataSource {
        val media3Id = activeMedia3Id
        val stored = media3Id?.let { DownloadHeaderStore.get(appContext, it) }.orEmpty()
        val headers = if (stored.isNotEmpty()) stored else activeHeaders
        val factory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(
                headers["User-Agent"]
                    ?: "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
            )
        if (headers.isNotEmpty()) {
            factory.setDefaultRequestProperties(headers)
        }
        return factory.createDataSource()
    }

    fun httpFactory(): HttpDataSource.Factory {
        return object : HttpDataSource.Factory {
            override fun createDataSource(): HttpDataSource {
                val media3Id = activeMedia3Id
                val stored = media3Id?.let { DownloadHeaderStore.get(appContext, it) }.orEmpty()
                val headers = if (stored.isNotEmpty()) stored else activeHeaders
                val factory = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(30_000)
                    .setReadTimeoutMs(60_000)
                    .setUserAgent(
                        headers["User-Agent"]
                            ?: "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
                    )
                if (headers.isNotEmpty()) {
                    factory.setDefaultRequestProperties(headers)
                }
                return factory.createDataSource()
            }

            override fun setDefaultRequestProperties(defaultRequestProperties: MutableMap<String, String>): HttpDataSource.Factory {
                activeHeaders = defaultRequestProperties.toMap()
                return this
            }
        }
    }
}
