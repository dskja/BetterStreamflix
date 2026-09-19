package com.dskja.betterstreamflix.support

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.dskja.betterstreamflix.R

/**
 * Binds Impact goals + FAQ rows into Support hub containers.
 */
object SupportHubBinder {

    fun bindImpact(context: Context, container: LinearLayout?) {
        container ?: return
        container.removeAllViews()
        val inflater = LayoutInflater.from(context)
        SupportContent.impactGoals.forEachIndexed { index, goal ->
            val row = inflater.inflate(R.layout.item_support_impact, container, false)
            row.findViewById<TextView>(R.id.tv_support_impact_title).setText(goal.titleRes)
            row.findViewById<TextView>(R.id.tv_support_impact_body).setText(goal.bodyRes)
            row.findViewById<ProgressBar>(R.id.pb_support_impact).progress =
                goal.progressPercent.coerceIn(0, 100)
            row.findViewById<TextView>(R.id.tv_support_impact_percent).text =
                context.getString(R.string.support_impact_percent, goal.progressPercent)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (index > 0) {
                    topMargin = context.resources.getDimensionPixelSize(R.dimen.support_card_spacing)
                }
            }
            container.addView(row, lp)
            SupportUiBinder.applyFocusScale(row)
        }
    }

    fun bindFaq(context: Context, container: LinearLayout?) {
        container ?: return
        container.removeAllViews()
        val inflater = LayoutInflater.from(context)
        SupportContent.faq.forEachIndexed { index, item ->
            val row = inflater.inflate(R.layout.item_support_faq, container, false)
            val question = row.findViewById<TextView>(R.id.tv_support_faq_question)
            val answer = row.findViewById<TextView>(R.id.tv_support_faq_answer)
            question.setText(item.questionRes)
            answer.setText(item.answerRes)
            answer.visibility = View.GONE
            row.setOnClickListener {
                val open = answer.visibility != View.VISIBLE
                answer.visibility = if (open) View.VISIBLE else View.GONE
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (index > 0) {
                    topMargin = context.resources.getDimensionPixelSize(R.dimen.support_card_spacing)
                }
            }
            container.addView(row, lp)
            SupportUiBinder.applyFocusScale(row)
        }
    }
}
