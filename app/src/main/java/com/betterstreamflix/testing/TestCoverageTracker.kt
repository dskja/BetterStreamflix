package com.betterstreamflix.testing

import java.util.concurrent.ConcurrentHashMap

/**
 * Test coverage tracker — tracks which code paths have been
 * exercised during testing.
 */
object TestCoverageTracker {

    private val coveredPaths = ConcurrentHashMap<String, Int>()
    private val totalPaths = ConcurrentHashMap<String, Int>()

    /**
     * Register a code path.
     */
    fun registerPath(module: String, pathName: String) {
        val key = "$module:$pathName"
        totalPaths[key] = 1
    }

    /**
     * Mark a code path as covered.
     */
    fun coverPath(module: String, pathName: String) {
        val key = "$module:$pathName"
        coveredPaths[key] = (coveredPaths[key] ?: 0) + 1
    }

    /**
     * Get coverage for a module.
     */
    fun getModuleCoverage(module: String): CoverageInfo {
        val modulePaths = totalPaths.keys.filter { it.startsWith("$module:") }
        val covered = modulePaths.count { coveredPaths.containsKey(it) }
        val total = modulePaths.size
        return CoverageInfo(
            module = module,
            coveredPaths = covered,
            totalPaths = total,
            coveragePercent = if (total > 0) (covered.toFloat() / total * 100).toInt() else 0,
        )
    }

    /**
     * Get overall coverage.
     */
    fun getOverallCoverage(): CoverageInfo {
        val covered = coveredPaths.size
        val total = totalPaths.size
        return CoverageInfo(
            module = "overall",
            coveredPaths = covered,
            totalPaths = total,
            coveragePercent = if (total > 0) (covered.toFloat() / total * 100).toInt() else 0,
        )
    }

    /**
     * Get all module coverage.
     */
    fun getAllModuleCoverage(): List<CoverageInfo> {
        val modules = totalPaths.keys.map { it.substringBefore(":") }.toSet()
        return modules.map { getModuleCoverage(it) }
    }

    /**
     * Get uncovered paths for a module.
     */
    fun getUncoveredPaths(module: String): List<String> {
        val modulePaths = totalPaths.keys.filter { it.startsWith("$module:") }
        return modulePaths.filter { !coveredPaths.containsKey(it) }.map { it.substringAfter(":") }
    }

    /**
     * Clear all coverage data.
     */
    fun clearAll() {
        coveredPaths.clear()
        totalPaths.clear()
    }

    /**
     * Format coverage report.
     */
    fun formatReport(): String {
        return buildString {
            appendLine("=== Test Coverage Report ===")
            appendLine()
            getAllModuleCoverage().forEach { info ->
                appendLine("${info.module}: ${info.coveragePercent}% (${info.coveredPaths}/${info.totalPaths})")
            }
            appendLine()
            appendLine("Overall: ${getOverallCoverage().coveragePercent}%")
        }
    }

    data class CoverageInfo(
        val module: String,
        val coveredPaths: Int,
        val totalPaths: Int,
        val coveragePercent: Int,
    )
}
