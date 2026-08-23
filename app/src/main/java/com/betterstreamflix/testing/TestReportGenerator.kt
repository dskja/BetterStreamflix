package com.betterstreamflix.testing

/**
 * Test report generator — generates test reports in various formats.
 */
object TestReportGenerator {

    /**
     * Generate a text report.
     */
    fun generateTextReport(): String {
        return buildString {
            appendLine("=== BetterStreamflix Test Report ===")
            appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
            appendLine()

            // Scenario results
            val scenarioResults = TestScenarioRunner.getResults()
            if (scenarioResults.isNotEmpty()) {
                appendLine("--- Test Scenarios ---")
                appendLine(TestScenarioRunner.formatResults())
                appendLine()
            }

            // Coverage report
            appendLine("--- Coverage ---")
            appendLine(TestCoverageTracker.formatReport())
            appendLine()

            // Performance benchmarks
            val benchmarks = PerformanceBenchmark.getAllResults()
            if (benchmarks.isNotEmpty()) {
                appendLine("--- Performance Benchmarks ---")
                benchmarks.values.forEach { result ->
                    appendLine(PerformanceBenchmark.formatResult(result))
                }
                appendLine()
            }

            // Error injection stats
            if (ErrorInjector.isEnabled()) {
                appendLine("--- Error Injection ---")
                appendLine("Total injected errors: ${ErrorInjector.getTotalErrorCount()}")
                appendLine()
            }

            // Summary
            appendLine("--- Summary ---")
            val totalScenarios = scenarioResults.size
            val passedScenarios = scenarioResults.count { it.passed }
            appendLine("Scenarios: $passedScenarios/$totalScenarios passed")
            appendLine("Coverage: ${TestCoverageTracker.getOverallCoverage().coveragePercent}%")
        }
    }

    /**
     * Generate a JSON report.
     */
    fun generateJsonReport(): String {
        val sb = StringBuilder()
        sb.append("{")

        // Scenarios
        val results = TestScenarioRunner.getResults()
        sb.append("\"scenarios\":[")
        results.forEachIndexed { index, result ->
            if (index > 0) sb.append(",")
            sb.append("{")
            sb.append("\"name\":\"${result.scenarioName}\",")
            sb.append("\"passed\":${result.passed},")
            sb.append("\"durationMs\":${result.durationMs},")
            sb.append("\"steps\":[")
            result.stepResults.forEachIndexed { sIndex, step ->
                if (sIndex > 0) sb.append(",")
                sb.append("{\"name\":\"${step.stepName}\",\"passed\":${step.passed}}")
            }
            sb.append("]}")
        }
        sb.append("],")

        // Coverage
        val coverage = TestCoverageTracker.getOverallCoverage()
        sb.append("\"coverage\":{")
        sb.append("\"percent\":${coverage.coveragePercent},")
        sb.append("\"covered\":${coverage.coveredPaths},")
        sb.append("\"total\":${coverage.totalPaths}")
        sb.append("}")

        sb.append("}")
        return sb.toString()
    }

    /**
     * Save report to a file.
     */
    fun saveReport(filePath: String, format: ReportFormat = ReportFormat.TEXT): Boolean {
        return try {
            val content = when (format) {
                ReportFormat.TEXT -> generateTextReport()
                ReportFormat.JSON -> generateJsonReport()
            }
            java.io.File(filePath).writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    enum class ReportFormat { TEXT, JSON }
}
