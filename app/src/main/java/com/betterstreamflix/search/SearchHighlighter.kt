package com.betterstreamflix.search

/**
 * Search highlighter — highlights matching text in search results.
 */
object SearchHighlighter {

    /**
     * Highlight matching portions of text.
     */
    fun highlight(text: String, query: String): List<HighlightSegment> {
        if (query.isBlank()) return listOf(HighlightSegment(text, false))

        val segments = mutableListOf<HighlightSegment>()
        val textLower = text.lowercase()
        val queryLower = query.lowercase()

        var lastIndex = 0
        var searchStart = 0

        while (searchStart <= textLower.length - queryLower.length) {
            val matchIndex = textLower.indexOf(queryLower, searchStart)
            if (matchIndex == -1) break

            // Add non-matched segment before
            if (matchIndex > lastIndex) {
                segments.add(HighlightSegment(text.substring(lastIndex, matchIndex), false))
            }

            // Add matched segment
            segments.add(HighlightSegment(text.substring(matchIndex, matchIndex + queryLower.length), true))

            lastIndex = matchIndex + queryLower.length
            searchStart = lastIndex
        }

        // Add remaining text
        if (lastIndex < text.length) {
            segments.add(HighlightSegment(text.substring(lastIndex), false))
        }

        return segments
    }

    /**
     * Get a simple highlighted HTML string.
     */
    fun highlightHtml(text: String, query: String): String {
        return highlight(text, query).joinToString("") { segment ->
            if (segment.isMatch) "<b>${segment.text}</b>" else segment.text
        }
    }

    data class HighlightSegment(
        val text: String,
        val isMatch: Boolean,
    )
}
