package com.betterstreamflix.resilience

import android.content.Context
import androidx.core.content.edit

/**
 * Graceful degradation manager — provides fallback strategies
 * when features or providers are unavailable.
 */
object GracefulDegradationManager {

    /**
     * Get the appropriate degradation strategy for a failed feature.
     */
    fun getStrategy(failureType: FailureType): DegradationStrategy {
        return when (failureType) {
            FailureType.PROVIDER_DOWN -> DegradationStrategy(
                showBanner = true,
                bannerMessage = "Provider temporarily unavailable. Trying alternatives...",
                fallbackAction = FallbackAction.TRY_OTHER_PROVIDERS,
            )
            FailureType.NETWORK_OFFLINE -> DegradationStrategy(
                showBanner = true,
                bannerMessage = "No internet connection. Showing cached content.",
                fallbackAction = FallbackAction.SHOW_CACHED,
            )
            FailureType.METADATA_UNAVAILABLE -> DegradationStrategy(
                showBanner = false,
                fallbackAction = FallbackAction.SHOW_BASIC_INFO,
            )
            FailureType.IMAGES_UNAVAILABLE -> DegradationStrategy(
                showBanner = false,
                fallbackAction = FallbackAction.SHOW_PLACEHOLDER,
            )
            FailureType.DOWNLOAD_FAILED -> DegradationStrategy(
                showBanner = true,
                bannerMessage = "Download failed. Will retry when online.",
                fallbackAction = FallbackAction.QUEUE_FOR_LATER,
            )
            FailureType.PLAYBACK_FAILED -> DegradationStrategy(
                showBanner = true,
                bannerMessage = "Playback error. Trying different server...",
                fallbackAction = FallbackAction.TRY_DIFFERENT_SERVER,
            )
        }
    }

    /**
     * Check if a feature should be disabled due to poor conditions.
     */
    fun shouldDisableFeature(
        feature: Feature,
        isOnline: Boolean,
        connectionQuality: ConnectionStateManager.ConnectionQuality,
    ): Boolean {
        if (!isOnline && feature.requiresNetwork) return true
        return connectionQuality == ConnectionStateManager.ConnectionQuality.NONE && feature.requiresNetwork
    }

    enum class FailureType {
        PROVIDER_DOWN,
        NETWORK_OFFLINE,
        METADATA_UNAVAILABLE,
        IMAGES_UNAVAILABLE,
        DOWNLOAD_FAILED,
        PLAYBACK_FAILED,
    }

    enum class FallbackAction {
        TRY_OTHER_PROVIDERS,
        SHOW_CACHED,
        SHOW_BASIC_INFO,
        SHOW_PLACEHOLDER,
        QUEUE_FOR_LATER,
        TRY_DIFFERENT_SERVER,
    }

    enum class Feature(val requiresNetwork: Boolean) {
        TMDB_METADATA(true),
        IMAGE_LOADING(true),
        DOWNLOADS(true),
        SEARCH(true),
        FAVORITES_SYNC(true),
        LOCAL_PLAYBACK(false),
        SETTINGS(false),
    }

    data class DegradationStrategy(
        val showBanner: Boolean,
        val bannerMessage: String? = null,
        val fallbackAction: FallbackAction,
    )
}
