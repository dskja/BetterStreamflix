package com.betterstreamflix.deployment

/**
 * CI/CD configuration — provides configuration values for
 * CI/CD pipelines and build automation.
 */
object CiCdConfiguration {

    /**
     * CI/CD environment.
     */
    enum class CiEnvironment {
        LOCAL, GITHUB_ACTIONS, GITLAB_CI, CIRCLE_CI, JENKINS, BITRISE, UNKNOWN
    }

    /**
     * Detect the current CI environment.
     */
    fun detectEnvironment(): CiEnvironment {
        val env = System.getenv()
        return when {
            env.containsKey("GITHUB_ACTIONS") -> CiEnvironment.GITHUB_ACTIONS
            env.containsKey("GITLAB_CI") -> CiEnvironment.GITLAB_CI
            env.containsKey("CIRCLECI") -> CiEnvironment.CIRCLE_CI
            env.containsKey("JENKINS_URL") -> CiEnvironment.JENKINS
            env.containsKey("BITRISE_IO") -> CiEnvironment.BITRISE
            else -> CiEnvironment.LOCAL
        }
    }

    /**
     * Check if running in CI.
     */
    fun isRunningInCi(): Boolean {
        return detectEnvironment() != CiEnvironment.LOCAL
    }

    /**
     * Get the current branch name.
     */
    fun getBranchName(): String? {
        return System.getenv("GITHUB_REF_NAME")
            ?: System.getenv("CI_COMMIT_BRANCH")
            ?: System.getenv("CIRCLE_BRANCH")
            ?: System.getenv("GIT_BRANCH")
            ?: System.getenv("BITRISE_GIT_BRANCH")
    }

    /**
     * Get the commit hash.
     */
    fun getCommitHash(): String? {
        return System.getenv("GITHUB_SHA")
            ?: System.getenv("CI_COMMIT_SHA")
            ?: System.getenv("CIRCLE_SHA1")
            ?: System.getenv("GIT_COMMIT")
            ?: System.getenv("BITRISE_GIT_COMMIT")
    }

    /**
     * Get the build number.
     */
    fun getBuildNumber(): String? {
        return System.getenv("GITHUB_RUN_NUMBER")
            ?: System.getenv("CI_JOB_ID")
            ?: System.getenv("CIRCLE_BUILD_NUM")
            ?: System.getenv("BUILD_NUMBER")
            ?: System.getenv("BITRISE_BUILD_NUMBER")
    }

    /**
     * Check if this is a tagged release.
     */
    fun isTaggedRelease(): Boolean {
        val ref = System.getenv("GITHUB_REF") ?: ""
        return ref.startsWith("refs/tags/")
    }

    /**
     * Get the tag name if available.
     */
    fun getTagName(): String? {
        val ref = System.getenv("GITHUB_REF") ?: return null
        if (!ref.startsWith("refs/tags/")) return null
        return ref.removePrefix("refs/tags/")
    }

    /**
     * Get CI configuration info.
     */
    fun getCiInfo(): CiInfo {
        return CiInfo(
            environment = detectEnvironment(),
            isCi = isRunningInCi(),
            branch = getBranchName(),
            commitHash = getCommitHash(),
            buildNumber = getBuildNumber(),
            isTaggedRelease = isTaggedRelease(),
            tagName = getTagName(),
        )
    }

    data class CiInfo(
        val environment: CiEnvironment,
        val isCi: Boolean,
        val branch: String?,
        val commitHash: String?,
        val buildNumber: String?,
        val isTaggedRelease: Boolean,
        val tagName: String?,
    )
}
