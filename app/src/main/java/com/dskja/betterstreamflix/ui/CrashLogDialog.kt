package com.dskja.betterstreamflix.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.utils.CrashReporter

/**
 * Full-featured local crash log viewer with copy / share / clear.
 */
object CrashLogDialog {
    fun show(context: Context) {
        val text = CrashReporter.latestCrashText(context)
        if (text.isNullOrBlank()) {
            Toast.makeText(context, R.string.settings_view_crash_log_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_crash_log, null, false)
        val body = view.findViewById<TextView>(R.id.tv_crash_log_body)
        body.text = text
        body.setTextIsSelectable(true)

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.settings_view_crash_log_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .create()

        view.findViewById<Button>(R.id.btn_crash_log_copy).setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("crash-log", text))
            Toast.makeText(context, R.string.crash_log_copied, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.btn_crash_log_share).setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "BetterStreamflix crash log")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            runCatching {
                context.startActivity(
                    Intent.createChooser(share, context.getString(R.string.crash_log_share)),
                )
            }
        }
        view.findViewById<Button>(R.id.btn_crash_log_clear).setOnClickListener {
            if (CrashReporter.clearAll(context)) {
                Toast.makeText(context, R.string.crash_log_cleared, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
