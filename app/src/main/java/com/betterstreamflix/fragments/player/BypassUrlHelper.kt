package com.betterstreamflix.fragments.player

import android.net.Uri
import android.webkit.CookieManager
import com.betterstreamflix.models.Video
import com.betterstreamflix.providers.SerienStreamProvider
import com.betterstreamflix.utils.UserPreferences

/**
 * Handles SerienStream bypass URL detection and cookie application.
 * Extracted from PlayerMobileFragment/PlayerTvFragment for shared use.
 */
object BypassUrlHelper {

    /**
     * Check if a URL belongs to SerienStream (by host comparison).
     */
    fun isSerienStreamBypassUrl(url: String): Boolean {
        return runCatching {
            val host = Uri.parse(url).host
            host.equals("serienstream.to", ignoreCase = true) ||
                host.equals(
                    UserPreferences.serienstreamDomain
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .trimEnd('/'),
                    ignoreCase = true
                )
        }.getOrDefault(false)
    }

    /**
     * Build a SerienStream bypass URL for the current episode.
     * Returns null if the current provider is not SerienStream or if it's a movie.
     */
    fun buildSerienStreamBypassUrl(videoType: Video.Type): String? {
        val provider = UserPreferences.currentProvider ?: return null
        if (provider != SerienStreamProvider) return null

        val episodeId = when (videoType) {
            is Video.Type.Episode -> videoType.id
            is Video.Type.Movie -> return null
        }

        return "${SerienStreamProvider.baseUrl}serie/$episodeId"
    }

    /**
     * Apply bypass cookies to CookieManager for the given URL.
     */
    fun applyBypassCookies(url: String, cookieHeader: String) {
        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
        val targets = linkedSetOf<String>().apply {
            add(url)
            if (host.isNotBlank()) {
                add("https://$host/")
                add("http://$host/")
            }
        }

        val cookieManager = CookieManager.getInstance()
        cookieHeader.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { cookie ->
                targets.forEach { target ->
                    cookieManager.setCookie(target, cookie)
                }
            }
        cookieManager.flush()
    }
}
