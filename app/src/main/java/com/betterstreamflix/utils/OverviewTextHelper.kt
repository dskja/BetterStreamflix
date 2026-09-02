package com.betterstreamflix.utils

import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import com.betterstreamflix.R

/**
 * Collapsible overview text for detail ViewHolders.
 */
object OverviewTextHelper {

    fun bind(textView: TextView, overview: String?, collapsedLines: Int = 4) {
        val text = overview?.trim().orEmpty()
        if (text.isBlank()) {
            textView.visibility = View.GONE
            return
        }
        textView.visibility = View.VISIBLE
        textView.movementMethod = LinkMovementMethod.getInstance()
        var expanded = false
        fun apply() {
            textView.maxLines = if (expanded) Int.MAX_VALUE else collapsedLines
            textView.text = text
        }
        apply()
        textView.post {
            val layout = textView.layout ?: return@post
            val needsToggle = layout.lineCount > collapsedLines ||
                layout.getEllipsisCount(collapsedLines - 1) > 0 ||
                text.length > 240
            if (!needsToggle) return@post
            val suffix = if (expanded) {
                textView.context.getString(R.string.overview_show_less)
            } else {
                textView.context.getString(R.string.overview_show_more)
            }
            textView.text = "$text\n$suffix"
            textView.setOnClickListener {
                expanded = !expanded
                apply()
                if (!expanded) {
                    textView.post {
                        textView.text = "$text\n${textView.context.getString(R.string.overview_show_more)}"
                    }
                }
            }
        }
    }
}
