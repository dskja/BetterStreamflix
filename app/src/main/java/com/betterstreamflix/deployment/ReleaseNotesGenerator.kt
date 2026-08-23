package com.betterstreamflix.deployment

/**
 * Release notes generator — generates release notes from version
 * information and changelog.
 */
object ReleaseNotesGenerator {

    private val changelog = mutableListOf<ChangelogEntry>()

    data class ChangelogEntry(
        val version: String,
        val date: String,
        val features: List<String>,
        val fixes: List<String>,
        val improvements: List<String>,
        val breakingChanges: List<String>,
    )

    /**
     * Add a changelog entry.
     */
    fun addEntry(entry: ChangelogEntry) {
        changelog.add(entry)
    }

    /**
     * Get changelog for a specific version.
     */
    fun getChangelog(version: String): ChangelogEntry? {
        return changelog.find { it.version == version }
    }

    /**
     * Get all changelog entries.
     */
    fun getAllEntries(): List<ChangelogEntry> = changelog.toList()

    /**
     * Generate release notes for a version.
     */
    fun generateReleaseNotes(version: String): String {
        val entry = getChangelog(version) ?: return "No release notes available for version $version"

        return buildString {
            appendLine("BetterStreamflix v$version")
            appendLine("Released: ${entry.date}")
            appendLine()

            if (entry.features.isNotEmpty()) {
                appendLine("New Features:")
                entry.features.forEach { appendLine("  + $it") }
                appendLine()
            }

            if (entry.improvements.isNotEmpty()) {
                appendLine("Improvements:")
                entry.improvements.forEach { appendLine("  * $it") }
                appendLine()
            }

            if (entry.fixes.isNotEmpty()) {
                appendLine("Bug Fixes:")
                entry.fixes.forEach { appendLine("  - $it") }
                appendLine()
            }

            if (entry.breakingChanges.isNotEmpty()) {
                appendLine("Breaking Changes:")
                entry.breakingChanges.forEach { appendLine("  ! $it") }
            }
        }
    }

    /**
     * Generate a full changelog.
     */
    fun generateFullChangelog(): String {
        return buildString {
            appendLine("=== BetterStreamflix Changelog ===")
            appendLine()
            changelog.forEach { entry ->
                appendLine(generateReleaseNotes(entry.version))
                appendLine("---")
                appendLine()
            }
        }
    }

    /**
     * Generate release notes in markdown format.
     */
    fun generateMarkdownReleaseNotes(version: String): String {
        val entry = getChangelog(version) ?: return "No release notes available for version $version"

        return buildString {
            appendLine("## BetterStreamflix v$version")
            appendLine("*Released: ${entry.date}*")
            appendLine()

            if (entry.features.isNotEmpty()) {
                appendLine("### New Features")
                entry.features.forEach { appendLine("- $it") }
                appendLine()
            }

            if (entry.improvements.isNotEmpty()) {
                appendLine("### Improvements")
                entry.improvements.forEach { appendLine("- $it") }
                appendLine()
            }

            if (entry.fixes.isNotEmpty()) {
                appendLine("### Bug Fixes")
                entry.fixes.forEach { appendLine("- $it") }
                appendLine()
            }

            if (entry.breakingChanges.isNotEmpty()) {
                appendLine("### Breaking Changes")
                entry.breakingChanges.forEach { appendLine("- **$it**") }
            }
        }
    }

    /**
     * Clear all changelog entries.
     */
    fun clearAll() {
        changelog.clear()
    }
}
