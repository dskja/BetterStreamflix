package com.betterstreamflix.content

/**
 * Content import/export — imports and exports content lists
 * in various formats (JSON, M3U).
 */
object ContentImportExport {

    /**
     * Export content list as JSON.
     */
    fun <T> exportToJson(
        items: List<T>,
        serializer: (T) -> Map<String, Any?>,
    ): String {
        val array = org.json.JSONArray()
        items.forEach { item ->
            array.put(org.json.JSONObject(serializer(item)))
        }
        return array.toString(2)
    }

    /**
     * Import content list from JSON.
     */
    fun <T> importFromJson(
        json: String,
        deserializer: (Map<String, Any?>) -> T,
    ): List<T> {
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val map = mutableMapOf<String, Any?>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = obj.get(key)
                }
                deserializer(map)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Export as M3U playlist.
     */
    fun <T> exportToM3U(
        items: List<T>,
        titleExtractor: (T) -> String,
        urlExtractor: (T) -> String,
        logoExtractor: ((T) -> String?)? = null,
    ): String {
        val builder = StringBuilder()
        builder.append("#EXTM3U\n")
        items.forEach { item ->
            val title = titleExtractor(item)
            val logo = logoExtractor?.invoke(item)
            val logoPart = logo?.let { " tvg-logo=\"$it\"" } ?: ""
            builder.append("#EXTINF:-1$logoPart,$title\n")
            builder.append(urlExtractor(item))
            builder.append("\n")
        }
        return builder.toString()
    }

    /**
     * Import from M3U playlist.
     */
    fun importFromM3U(m3uContent: String): List<M3UEntry> {
        val entries = mutableListOf<M3UEntry>()
        val lines = m3uContent.lines()
        var currentTitle: String? = null
        var currentLogo: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                val commaIndex = trimmed.indexOf(",")
                currentTitle = if (commaIndex >= 0) trimmed.substring(commaIndex + 1) else null
                val logoMatch = Regex("tvg-logo=\"([^\"]+)\"").find(trimmed)
                currentLogo = logoMatch?.groupValues?.get(1)
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                entries.add(M3UEntry(currentTitle ?: "Unknown", trimmed, currentLogo))
                currentTitle = null
                currentLogo = null
            }
        }
        return entries
    }

    data class M3UEntry(
        val title: String,
        val url: String,
        val logoUrl: String?,
    )
}
