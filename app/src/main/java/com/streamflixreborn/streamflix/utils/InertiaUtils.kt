package com.streamflixreborn.streamflix.utils

import android.util.Log
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

object InertiaUtils {
    private const val TAG = "InertiaUtils"

    fun parseInertiaData(doc: Document): JSONObject {
        val dataAttr = doc.selectFirst("#app")?.attr("data-page") ?: ""
        if (dataAttr.isBlank()) {
            Log.e(TAG, "parseInertiaData: data-page attribute is empty!")
            return JSONObject()
        }
        return try {
            val decoded = Parser.unescapeEntities(dataAttr, false)
            JSONObject(decoded)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Inertia JSON", e)
            JSONObject()
        }
    }

    fun getVersion(doc: Document): String {
        return parseInertiaData(doc).optString("version", "")
    }
}
