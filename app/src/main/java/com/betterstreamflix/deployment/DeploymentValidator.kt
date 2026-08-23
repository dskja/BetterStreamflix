package com.betterstreamflix.deployment

/**
 * Deployment validator — validates that the app is ready for
 * deployment by checking all requirements.
 */
object DeploymentValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
    )

    /**
     * Validate the build for deployment.
     */
    fun validate(): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check build type
        if (BuildConfiguration.isDebug()) {
            errors.add("Cannot deploy debug build to production")
        }

        // Check version
        val versionName = BuildConfiguration.getBuildVersionName()
        if (versionName == "unknown" || versionName.isBlank()) {
            errors.add("Version name is not set")
        }

        // Check version code
        val versionCode = BuildConfiguration.getBuildVersionCode()
        if (versionCode <= 0) {
            errors.add("Version code must be positive")
        }

        // Check CI environment
        val ciInfo = CiCdConfiguration.getCiInfo()
        if (!ciInfo.isCi) {
            warnings.add("Not running in CI environment")
        }

        // Check branch
        val branch = ciInfo.branch
        if (branch != null && branch != "main" && branch != "master" && !ciInfo.isTaggedRelease) {
            warnings.add("Deploying from non-main branch: $branch")
        }

        // Check minification
        if (!BuildVariantManager.isMinificationEnabled()) {
            warnings.add("Minification is not enabled — APK will be larger")
        }

        // Check feature gates
        val gates = FeatureGate.getAllGates()
        if (gates.isEmpty()) {
            warnings.add("No feature gates registered")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
        )
    }

    /**
     * Check if the build passes all critical checks.
     */
    fun passesCriticalChecks(): Boolean {
        val result = validate()
        return result.isValid
    }

    /**
     * Generate a validation report.
     */
    fun generateReport(): String {
        val result = validate()
        return buildString {
            appendLine("=== Deployment Validation Report ===")
            appendLine()
            appendLine("Status: ${if (result.isValid) "PASS" else "FAIL"}")
            appendLine()

            if (result.errors.isNotEmpty()) {
                appendLine("Errors:")
                result.errors.forEach { appendLine("  ✗ $it") }
                appendLine()
            }

            if (result.warnings.isNotEmpty()) {
                appendLine("Warnings:")
                result.warnings.forEach { appendLine("  ⚠ $it") }
                appendLine()
            }

            if (result.errors.isEmpty() && result.warnings.isEmpty()) {
                appendLine("All checks passed. Ready for deployment.")
            }
        }
    }
}
