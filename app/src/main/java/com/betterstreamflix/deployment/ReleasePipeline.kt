package com.betterstreamflix.deployment

/**
 * Release pipeline — manages the release pipeline steps and
 * their execution order.
 */
object ReleasePipeline {

    enum class PipelineStep(val order: Int, val description: String) {
        CHECKOUT(0, "Checkout source code"),
        SETUP_BUILD_ENV(1, "Set up build environment"),
        RUN_TESTS(2, "Run unit and integration tests"),
        RUN_LINT(3, "Run lint checks"),
        BUILD_APK(4, "Build APK"),
        BUILD_AAB(5, "Build AAB for Play Store"),
        SIGN_BUILD(6, "Sign the build"),
        VALIDATE_SIGNING(7, "Validate signing configuration"),
        CREATE_RELEASE_NOTES(8, "Generate release notes"),
        UPLOAD_ARTIFACTS(9, "Upload build artifacts"),
        CREATE_GITHUB_RELEASE(10, "Create GitHub release"),
        NOTIFY(11, "Notify stakeholders"),
    }

    private val completedSteps = mutableSetOf<PipelineStep>()
    private val failedSteps = mutableMapOf<PipelineStep, String>()

    /**
     * Mark a step as completed.
     */
    fun markStepCompleted(step: PipelineStep) {
        completedSteps.add(step)
        failedSteps.remove(step)
    }

    /**
     * Mark a step as failed.
     */
    fun markStepFailed(step: PipelineStep, error: String) {
        failedSteps[step] = error
    }

    /**
     * Check if a step is completed.
     */
    fun isStepCompleted(step: PipelineStep): Boolean = completedSteps.contains(step)

    /**
     * Check if a step has failed.
     */
    fun isStepFailed(step: PipelineStep): Boolean = failedSteps.containsKey(step)

    /**
     * Get the current pipeline progress.
     */
    fun getProgress(): PipelineProgress {
        val totalSteps = PipelineStep.entries.size
        val completedCount = completedSteps.size
        val failedCount = failedSteps.size
        val currentStep = PipelineStep.entries.firstOrNull {
            !completedSteps.contains(it) && !failedSteps.containsKey(it)
        }
        return PipelineProgress(
            totalSteps = totalSteps,
            completedSteps = completedCount,
            failedSteps = failedCount,
            currentStep = currentStep,
            isComplete = completedCount == totalSteps,
            hasFailures = failedCount > 0,
        )
    }

    /**
     * Get the next step to execute.
     */
    fun getNextStep(): PipelineStep? {
        return PipelineStep.entries.firstOrNull {
            !completedSteps.contains(it) && !failedSteps.containsKey(it)
        }
    }

    /**
     * Reset the pipeline.
     */
    fun reset() {
        completedSteps.clear()
        failedSteps.clear()
    }

    /**
     * Generate a pipeline status report.
     */
    fun generateStatusReport(): String {
        val progress = getProgress()
        return buildString {
            appendLine("=== Release Pipeline Status ===")
            appendLine("Progress: ${progress.completedSteps}/${progress.totalSteps}")
            appendLine("Failed: ${progress.failedSteps}")
            appendLine("Complete: ${progress.isComplete}")
            appendLine()
            appendLine("Steps:")
            PipelineStep.entries.sortedBy { it.order }.forEach { step ->
                val status = when {
                    completedSteps.contains(step) -> "✓"
                    failedSteps.containsKey(step) -> "✗"
                    else -> "○"
                }
                appendLine("  [$status] ${step.name}: ${step.description}")
                failedSteps[step]?.let { error ->
                    appendLine("         Error: $error")
                }
            }
        }
    }

    data class PipelineProgress(
        val totalSteps: Int,
        val completedSteps: Int,
        val failedSteps: Int,
        val currentStep: PipelineStep?,
        val isComplete: Boolean,
        val hasFailures: Boolean,
    )
}
