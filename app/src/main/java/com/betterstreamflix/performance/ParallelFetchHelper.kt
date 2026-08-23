package com.betterstreamflix.performance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Parallel fetch helper — runs multiple async operations in parallel
 * and collects results.
 */
object ParallelFetchHelper {

    /**
     * Run multiple operations in parallel and return all results.
     */
    suspend fun <T> parallelFetch(
        operations: List<suspend () -> T>,
    ): List<Result<T>> = coroutineScope {
        operations.map { operation ->
            async(Dispatchers.IO) {
                try {
                    Result.success(operation())
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }.awaitAll()
    }

    /**
     * Run operations in parallel and return first successful result.
     */
    suspend fun <T> raceToSuccess(
        operations: List<suspend () -> T>,
    ): T? = coroutineScope {
        val deferreds = operations.map { op ->
            async(Dispatchers.IO) {
                try {
                    Result.success(op())
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

        for (deferred in deferreds) {
            val result = deferred.await()
            if (result.isSuccess) return@coroutineScope result.getOrNull()
        }
        null
    }

    /**
     * Run operations in batches to avoid overwhelming the system.
     */
    suspend fun <T, R> batchProcess(
        items: List<T>,
        batchSize: Int = 10,
        processor: suspend (T) -> R,
    ): List<R> = coroutineScope {
        items.chunked(batchSize).flatMap { batch ->
            batch.map { item ->
                async(Dispatchers.IO) { processor(item) }
            }.awaitAll()
        }
    }
}
