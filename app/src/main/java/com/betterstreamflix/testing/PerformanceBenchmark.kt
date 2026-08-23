package com.betterstreamflix.testing

import java.util.concurrent.ConcurrentHashMap

/**
 * Performance benchmark — lightweight performance benchmarking
 * utilities for measuring code execution.
 */
object PerformanceBenchmark {

    private val benchmarks = ConcurrentHashMap<String, BenchmarkResult>()

    data class BenchmarkResult(
        val name: String,
        val iterations: Int,
        val totalTimeMs: Long,
        val averageTimeMs: Double,
        val minTimeMs: Long,
        val maxTimeMs: Long,
    )

    /**
     * Run a benchmark.
     */
    fun benchmark(name: String, iterations: Int = 100, block: () -> Unit): BenchmarkResult {
        val times = mutableListOf<Long>()

        repeat(iterations) {
            val start = System.nanoTime()
            block()
            val duration = (System.nanoTime() - start) / 1_000_000
            times.add(duration)
        }

        val result = BenchmarkResult(
            name = name,
            iterations = iterations,
            totalTimeMs = times.sum(),
            averageTimeMs = times.average(),
            minTimeMs = times.min(),
            maxTimeMs = times.max(),
        )

        benchmarks[name] = result
        return result
    }

    /**
     * Run a suspend benchmark.
     */
    suspend fun benchmarkSuspend(name: String, iterations: Int = 100, block: suspend () -> Unit): BenchmarkResult {
        val times = mutableListOf<Long>()

        repeat(iterations) {
            val start = System.nanoTime()
            block()
            val duration = (System.nanoTime() - start) / 1_000_000
            times.add(duration)
        }

        val result = BenchmarkResult(
            name = name,
            iterations = iterations,
            totalTimeMs = times.sum(),
            averageTimeMs = times.average(),
            minTimeMs = times.min(),
            maxTimeMs = times.max(),
        )

        benchmarks[name] = result
        return result
    }

    /**
     * Get a benchmark result.
     */
    fun getResult(name: String): BenchmarkResult? = benchmarks[name]

    /**
     * Get all benchmark results.
     */
    fun getAllResults(): Map<String, BenchmarkResult> = benchmarks.toMap()

    /**
     * Clear all benchmark results.
     */
    fun clearResults() {
        benchmarks.clear()
    }

    /**
     * Format a benchmark result for display.
     */
    fun formatResult(result: BenchmarkResult): String {
        return buildString {
            appendLine("Benchmark: ${result.name}")
            appendLine("  Iterations: ${result.iterations}")
            appendLine("  Total: ${result.totalTimeMs}ms")
            appendLine("  Average: ${String.format("%.2f", result.averageTimeMs)}ms")
            appendLine("  Min: ${result.minTimeMs}ms")
            appendLine("  Max: ${result.maxTimeMs}ms")
        }
    }
}
