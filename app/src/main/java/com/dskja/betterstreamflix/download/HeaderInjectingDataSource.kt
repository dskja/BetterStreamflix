package com.dskja.betterstreamflix.download

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Delegating [DataSource] that injects the HTTP headers stored for the
 * requested URL (see [DownloadHeaderStore.putForUrl]) right when [open] runs.
 *
 * Media3 downloads create one data source per download task on shared
 * factories — mutable "active" fields would leak headers across parallel
 * downloads. Keying headers by URL keeps every segment request scoped to the
 * download that produced it.
 */
class HeaderInjectingDataSource(
    context: Context,
    private val delegate: DataSource,
) : DataSource {

    private val appContext = context.applicationContext

    override fun open(dataSpec: DataSpec): Long {
        val headers = DownloadHeaderStore.getForUrl(appContext, dataSpec.uri.toString())
        val spec = if (headers.isEmpty()) {
            dataSpec
        } else {
            dataSpec.buildUpon()
                .setHttpRequestHeaders(dataSpec.httpRequestHeaders + headers)
                .build()
        }
        return delegate.open(spec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate.read(buffer, offset, length)

    override fun addTransferListener(transferListener: TransferListener) =
        delegate.addTransferListener(transferListener)

    override fun getUri() = delegate.uri

    override fun getResponseHeaders() = delegate.responseHeaders

    override fun close() = delegate.close()
}
