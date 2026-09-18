package com.dskja.betterstreamflix.support

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.databinding.ItemSupportProviderCardBinding
import com.dskja.betterstreamflix.utils.ExperimentalMobileDesign

object SupportUiBinder {

    fun bindProviderCards(
        context: Context,
        container: LinearLayout,
        horizontal: Boolean = false,
        animate: Boolean = ExperimentalMobileDesign.enabled(),
    ) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(context)
        SupportProvider.entries.forEachIndexed { index, provider ->
            val card = ItemSupportProviderCardBinding.inflate(inflater, container, false)
            card.ivSupportProviderIcon.setImageResource(provider.iconRes)
            card.tvSupportProviderTitle.setText(provider.titleRes)
            card.tvSupportProviderDescription.setText(provider.descriptionRes)
            card.btnSupportProviderCta.setText(provider.ctaRes)
            val open = View.OnClickListener {
                SupportLinkOpener.openProvider(context, provider)
            }
            card.root.setOnClickListener(open)
            card.btnSupportProviderCta.setOnClickListener(open)
            applyFocusScale(card.root)

            val lp = if (horizontal) {
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = context.resources.getDimensionPixelSize(R.dimen.support_card_spacing)
                }
            } else {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (index > 0) topMargin = context.resources.getDimensionPixelSize(R.dimen.support_card_spacing)
                }
            }
            container.addView(card.root, lp)

            if (animate) {
                val anim = AnimationUtils.loadAnimation(context, R.anim.support_fade_slide_up)
                anim.startOffset = (index * 40L)
                card.root.startAnimation(anim)
            }
        }
    }

    fun applyFocusScale(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.03f else 1f
            v.animate().scaleX(scale).scaleY(scale).setDuration(140).start()
        }
    }
}
