package com.betterstreamflix.search

/**
 * Search result aggregator — merges results from multiple providers
 * and deduplicates by title similarity.
 */
object SearchResultAggregator {

    data class AggregatedResult(
        val title: String,
        val providerResults: Map<String, ProviderResult>,
        val bestScore: Double,
    )

    data class ProviderResult(
        val providerName: String,
        val title: String,
        val type: String,
        val posterUrl: String?,
        val rating: Double?,
        val year: String?,
        val url: String,
    )

    /**
     * Aggregate results from multiple providers.
     */
    fun aggregate(
        providerResults: Map<String, List<ProviderResult>>,
        similarityThreshold: Double = 0.85,
    ): List<AggregatedResult> {
        val aggregated = mutableListOf<AggregatedResult>()
        val usedIndices = mutableMapOf<String, MutableSet<Int>>()

        for ((providerName, results) in providerResults) {
            results.forEachIndexed { index, result ->
                if (usedIndices[providerName]?.contains(index) == true) return@forEachIndexed

                // Find matching results in other providers
                val matches = mutableMapOf(providerName to result)
                for ((otherProvider, otherResults) in providerResults) {
                    if (otherProvider == providerName) continue
                    otherResults.forEachIndexed { otherIndex, otherResult ->
                        if (usedIndices[otherProvider]?.contains(otherIndex) == true) return@forEachIndexed

                        val sim = FuzzySearch.similarity(result.title, otherResult.title)
                        if (sim >= similarityThreshold) {
                            matches[otherProvider] = otherResult
                            usedIndices.getOrPut(otherProvider) { mutableSetOf() }.add(otherIndex)
                        }
                    }
                }

                usedIndices.getOrPut(providerName) { mutableSetOf() }.add(index)
                aggregated.add(
                    AggregatedResult(
                        title = result.title,
                        providerResults = matches,
                        bestScore = matches.values.map { it.rating ?: 0.0 }.maxOrNull() ?: 0.0,
                    ),
                )
            }
        }

        return aggregated.sortedByDescending { it.bestScore }
    }
}
