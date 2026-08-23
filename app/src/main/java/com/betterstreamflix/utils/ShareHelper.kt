package com.betterstreamflix.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Helper for sharing diagnostic reports and logs.
 */
object ShareHelper {

    /**
     * Share text content via intent.
     */
    fun shareText(context: Context, text: String, title: String = "Share") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    /**
     * Share a file via FileProvider.
     */
    fun shareFile(context: Context, file: File, mimeType: String = "*/*", title: String = "Share") {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    /**
     * Share diagnostic report.
     */
    fun shareDiagnosticReport(context: Context) {
        val report = DiagnosticInfo.collect(context)
        shareText(context, report, "BetterStreamflix Diagnostic Report")
    }
}
