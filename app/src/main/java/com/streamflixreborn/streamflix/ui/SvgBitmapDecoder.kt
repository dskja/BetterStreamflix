package com.streamflixreborn.streamflix.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.bumptech.glide.request.target.Target
import com.caverock.androidsvg.SVG
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.ceil

/** Decodes SVG streams into pooled bitmaps so they work with normal Glide image requests. */
class SvgBitmapDecoder(
    private val bitmapPool: BitmapPool,
) : ResourceDecoder<InputStream, Bitmap> {

    override fun handles(source: InputStream, options: Options): Boolean {
        if (!source.markSupported()) return false

        source.mark(SVG_HEADER_LIMIT)
        return try {
            val header = ByteArray(SVG_HEADER_LIMIT)
            val bytesRead = source.read(header)
            bytesRead > 0 && String(header, 0, bytesRead, StandardCharsets.UTF_8)
                .lowercase(Locale.ROOT)
                .contains("<svg")
        } finally {
            source.reset()
        }
    }

    override fun decode(
        source: InputStream,
        width: Int,
        height: Int,
        options: Options,
    ): Resource<Bitmap> {
        val svg = try {
            SVG.getFromInputStream(source)
        } catch (error: Exception) {
            throw IOException("Unable to parse SVG image", error)
        }

        val viewBox = svg.documentViewBox
        val intrinsicWidth = svg.documentWidth.takeIf { it.isFinite() && it > 0f }
            ?: viewBox?.width()
        val intrinsicHeight = svg.documentHeight.takeIf { it.isFinite() && it > 0f }
            ?: viewBox?.height()

        val bitmapWidth = resolveDimension(width, intrinsicWidth)
        val bitmapHeight = resolveDimension(height, intrinsicHeight)
        val bitmap = bitmapPool.get(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
        }

        return try {
            svg.documentWidth = bitmapWidth.toFloat()
            svg.documentHeight = bitmapHeight.toFloat()
            svg.renderToCanvas(Canvas(bitmap))
            BitmapResource(bitmap, bitmapPool)
        } catch (error: Exception) {
            bitmapPool.put(bitmap)
            throw IOException("Unable to render SVG image", error)
        }
    }

    private fun resolveDimension(requested: Int, intrinsic: Float?): Int {
        val resolved = when {
            requested > 0 && requested != Target.SIZE_ORIGINAL -> requested
            intrinsic != null && intrinsic.isFinite() && intrinsic > 0f -> ceil(intrinsic).toInt()
            else -> DEFAULT_SVG_SIZE
        }
        return resolved.coerceIn(1, MAX_SVG_SIZE)
    }

    private companion object {
        const val SVG_HEADER_LIMIT = 8 * 1024
        const val DEFAULT_SVG_SIZE = 512
        const val MAX_SVG_SIZE = 2_048
    }
}
