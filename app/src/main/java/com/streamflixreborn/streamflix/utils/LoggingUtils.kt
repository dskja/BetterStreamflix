package com.streamflixreborn.streamflix.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.streamflixreborn.streamflix.R

object LoggingUtils {
    
    /**
     * Displays a logging dialog with error details
     * @param context The context to show the dialog
     * @param error The throwable error to log
     */
    fun showErrorDialog(context: Context, error: Throwable) {
        val tag = "ErrorLog"
        val logBuilder = StringBuilder()
        
        val errorMessage = context.getString(R.string.error_dialog_message)
        val errorCause = context.getString(R.string.error_dialog_cause)
        val stackTrace = context.getString(R.string.error_dialog_stack_trace)
        val unknownError = context.getString(R.string.error_dialog_unknown)
        val noCause = context.getString(R.string.error_dialog_no_cause)
        
        fun log(message: String) {
            Log.e(tag, message)
            logBuilder.append(message).append("\n\n")
        }

        log("📋 $errorMessage:\n${error.message ?: unknownError}")
        log("🔗 $errorCause:\n${error.cause?.message ?: noCause}")
        log("📜 $stackTrace:\n${Log.getStackTraceString(error)}")

        val logContent = logBuilder.toString()

        AlertDialog.Builder(context)
            .setTitle("📝 ${context.getString(R.string.error_dialog_title)}")
            .setMessage(logContent)
            .setPositiveButton("OK", null)
            .setNeutralButton("📋 ${context.getString(R.string.error_dialog_copy)}") { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Error Log", logContent)
                clipboard.setPrimaryClip(clip)
                // Only show toast on Android 12 and below (Android 13+ shows system clipboard notification)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, context.getString(R.string.error_dialog_copied), Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}
