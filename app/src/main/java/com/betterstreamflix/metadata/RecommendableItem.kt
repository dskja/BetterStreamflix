package com.betterstreamflix.metadata

/**
 * An item that can be recommended by [RecommendationEngineV2].
 */
data class RecommendableItem(
    val title: String,
    val type: String,
    val providerName: String,
    val thumbnailUrl: String?,
)
