package com.betterstreamflix.resilience

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Error reporter — collects and persists error reports for later
 * submission or debugging.
 */
object ErrorReporter {

    private const val PREFS_NAME = "error_reports"
    private const val KEY_REPORTS = "reports"
    private const val MAX_REPORTS = 50

    data class ErrorReport(
        val timestamp: Long,
        val type: String,
        val message: String,
        val stackTrace: String?,
        val providerName: String?,
        val context: String?,
    )

    /**
     * Report an error.
     */
    fun report(
        context: Context,
        type: String,
        message: String,
        stackTrace: String? = null,
        providerName: String? = null,
        additionalContext: String? = null,
    ) {
        val report = ErrorReport(
            timestamp = System.currentTimeMillis(),
            type = type,
            message = message,
            stackTrace = stackTrace,
            providerName = providerName,
            context = additionalContext,
        )
        addReport(context, report)
    }

    /**
     * Get all stored error reports.
     */
    fun getReports(context: Context): List<ErrorReport> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_REPORTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ErrorReport(
                    timestamp = obj.getLong("timestamp"),
                    type = obj.getString("type"),
                    message = obj.getString("message"),
                    stackTrace = obj.optString("stackTrace").ifBlank { null },
                    providerName = obj.optString("providerName").ifBlank { null },
                    context = obj.optString("context").ifBlank { null },
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Clear all error reports.
     */
    fun clearReports(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_REPORTS)
        }
    }

    private fun addReport(context: Context, report: ErrorReport) {
        val reports = getReports(context).toMutableList()
        reports.add(0, report)
        if (reports.size > MAX_REPORTS) {
            reports.subList(MAX_REPORTS, reports.size).clear()
        }

        val array = JSONArray()
        reports.forEach { r ->
            array.put(JSONObject().apply {
                put("timestamp", r.timestamp)
                put("type", r.type)
                put("message", r.message)
                r.stackTrace?.let { put("stackTrace", it) }
                r.providerName?.let { put("providerName", it) }
                r.context?.let { put("context", it) }
            })
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_REPORTS, array.toString())
        }
    }
}
