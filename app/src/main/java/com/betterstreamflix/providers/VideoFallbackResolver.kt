package com.betterstreamflix.providers

import com.betterstreamflix.data.Result
import com.betterstreamflix.data.RetryHelper
import com.betterstreamflix.models.Video

/**
 * Provides fallback video resolution when primary provider/extractor fails.
 * Tries alternative servers, extractors, and providers in sequence.
 */
object VideoFallbackResolver {

    /**
     * Try to get a playable video from multiple servers with fallback.
     */
    suspend fun resolveVideoWithFallback(
        servers: List<Video.Server>,
        primaryProvider: Provider,
        fallbackProviders: List<Provider> = emptyList(),
    ): Result<Video> {
        for (server in servers) {
            try {
                val video = primaryProvider.getVideo(server)
                ProviderHealthMonitor.recordSuccess(primaryProvider.name)
                return Result.Success(video)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ProviderHealthMonitor.recordFailure(primaryProvider.name, e.message ?: "Unknown error")
            }
        }

        // Try fallback providers
        for (provider in fallbackProviders) {
            if (!ProviderHealthMonitor.isHealthy(provider.name)) continue
            for (server in servers) {
                try {
                    val video = provider.getVideo(server)
                    ProviderHealthMonitor.recordSuccess(provider.name)
                    return Result.Success(video)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    ProviderHealthMonitor.recordFailure(provider.name, e.message ?: "Unknown error")
                }
            }
        }

        return Result.Error(com.betterstreamflix.data.ErrorType.Provider(
            primaryProvider.name,
            "All servers and fallback providers failed",
        ))
    }

    /**
     * Try an extractor with retry and fallback.
     */
    suspend fun resolveWithRetry(
        url: String,
        maxRetries: Int = 2,
        block: suspend (String) -> Video,
    ): Result<Video> {
        return try {
            val video = RetryHelper.retryOrThrow(maxRetries) { block(url) }
            Result.Success(video)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.Error(com.betterstreamflix.data.ErrorType.Unknown(e.message ?: "Extractor failed"))
        }
    }
}
