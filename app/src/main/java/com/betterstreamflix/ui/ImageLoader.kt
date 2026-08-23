package com.betterstreamflix.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener

/**
 * Image loading helper — provides centralized Glide configuration
 * with consistent caching and error handling.
 */
object ImageLoader {

    /**
     * Load an image into an ImageView with standard configuration.
     */
    fun load(
        context: Context,
        url: String?,
        imageView: ImageView,
        placeholder: Int? = null,
        errorImage: Int? = null,
        crossFade: Boolean = true,
        centerCrop: Boolean = true,
    ) {
        val request = Glide.with(context)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)

        placeholder?.let { request.placeholder(it) }
        errorImage?.let { request.error(it) }

        if (crossFade) request.transition(DrawableTransitionOptions.withCrossFade())
        if (centerCrop) request.centerCrop()

        request.into(imageView)
    }

    /**
     * Load an image with a custom request listener.
     */
    fun loadWithListener(
        context: Context,
        url: String?,
        imageView: ImageView,
        listener: RequestListener<Drawable>,
    ) {
        Glide.with(context)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .listener(listener)
            .into(imageView)
    }

    /**
     * Preload an image into the disk cache.
     */
    fun preload(context: Context, url: String?) {
        Glide.with(context)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .preload()
    }

    /**
     * Clear an ImageView.
     */
    fun clear(imageView: ImageView) {
        Glide.with(imageView.context).clear(imageView)
    }
}
