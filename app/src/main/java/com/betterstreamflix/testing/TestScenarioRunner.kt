package com.betterstreamflix.testing

/**
 * Test scenario runner — runs predefined test scenarios for
 * integration testing.
 */
object TestScenarioRunner {

    private val scenarios = mutableListOf<TestScenario>()
    private val results = mutableListOf<ScenarioResult>()

    data class TestScenario(
        val name: String,
        val description: String,
        val steps: List<TestStep>,
    )

    data class TestStep(
        val name: String,
        val action: () -> Boolean,
    )

    data class ScenarioResult(
        val scenarioName: String,
        val passed: Boolean,
        val stepResults: List<StepResult>,
        val durationMs: Long,
    )

    data class StepResult(
        val stepName: String,
        val passed: Boolean,
        val errorMessage: String?,
    )

    /**
     * Register a test scenario.
     */
    fun registerScenario(scenario: TestScenario) {
        scenarios.add(scenario)
    }

    /**
     * Run a single scenario.
     */
    fun runScenario(scenario: TestScenario): ScenarioResult {
        val startTime = System.currentTimeMillis()
        val stepResults = mutableListOf<StepResult>()
        var allPassed = true

        for (step in scenario.steps) {
            try {
                val passed = step.action()
                stepResults.add(StepResult(step.name, passed, if (!passed) "Step returned false" else null))
                if (!passed) allPassed = false
            } catch (e: Exception) {
                stepResults.add(StepResult(step.name, false, e.message))
                allPassed = false
            }
        }

        val result = ScenarioResult(
            scenarioName = scenario.name,
            passed = allPassed,
            stepResults = stepResults,
            durationMs = System.currentTimeMillis() - startTime,
        )
        results.add(result)
        return result
    }

    /**
     * Run all registered scenarios.
     */
    fun runAllScenarios(): List<ScenarioResult> {
        return scenarios.map { runScenario(it) }
    }

    /**
     * Get all results.
     */
    fun getResults(): List<ScenarioResult> = results.toList()

    /**
     * Get pass rate.
     */
    fun getPassRate(): Float {
        if (results.isEmpty()) return 0f
        return results.count { it.passed }.toFloat() / results.size
    }

    /**
     * Clear all scenarios and results.
     */
    fun clearAll() {
        scenarios.clear()
        results.clear()
    }

    /**
     * Format results for display.
     */
    fun formatResults(): String {
        return buildString {
            results.forEach { result ->
                appendLine("[${if (result.passed) "PASS" else "FAIL"}] ${result.scenarioName} (${result.durationMs}ms)")
                result.stepResults.forEach { step ->
                    appendLine("  [${if (step.passed) "✓" else "✗"}] ${step.stepName}")
                    step.errorMessage?.let { appendLine("    Error: $it") }
                }
            }
            appendLine("\nPass rate: ${String.format("%.1f", getPassRate() * 100)}%")
        }
    }
}
