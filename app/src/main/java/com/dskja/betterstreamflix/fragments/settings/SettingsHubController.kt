package com.dskja.betterstreamflix.fragments.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.databinding.FragmentSettingsHubMobileBinding
import com.dskja.betterstreamflix.utils.ExpMotion
import com.dskja.betterstreamflix.utils.ExperimentalMobileDesign

/**
 * Overlays a Support-style cinematic hub on top of PreferenceFragmentCompat
 * when experimental design is enabled and the user is on the settings root.
 */
internal class SettingsHubController(
    private val fragment: PreferenceFragmentCompat,
    private val isAtRoot: () -> Boolean,
    private val onOpenPreferenceScreen: (key: String, title: String) -> Unit,
    private val onOpenSupport: () -> Unit,
    private val onOpenAbout: () -> Unit,
) {
    private var hubBinding: FragmentSettingsHubMobileBinding? = null
    private var cardsBound = false
    private var enterAnimated = false

    fun attach(root: View) {
        if (!ExperimentalMobileDesign.enabled()) {
            detach()
            return
        }
        val parent = root as? ViewGroup ?: return
        if (hubBinding != null) {
            updateVisibility()
            return
        }
        val binding = FragmentSettingsHubMobileBinding.inflate(
            LayoutInflater.from(fragment.requireContext()),
            parent,
            true,
        )
        hubBinding = binding
        cardsBound = false
        enterAnimated = false
        bindCards(binding)
        updateVisibility()
    }

    fun updateVisibility() {
        val binding = hubBinding
        if (!ExperimentalMobileDesign.enabled()) {
            detach()
            return
        }
        if (binding == null) return

        val visible = isAtRoot()
        binding.root.visibility = if (visible) View.VISIBLE else View.GONE
        fragment.listView?.apply {
            visibility = if (visible) View.GONE else View.VISIBLE
            if (ExperimentalMobileDesign.enabled()) {
                setBackgroundColor(ContextCompat.getColor(context, R.color.support_bg))
            }
            if (visible) {
                // Keep focus on the hub overlay (important for TV).
            } else {
                post { requestFocus() }
            }
        }
        if (visible) {
            if (!cardsBound) bindCards(binding)
            if (!enterAnimated) {
                playEnterAnimation(binding)
                enterAnimated = true
            }
            binding.cardSettingsFeatured.post {
                binding.cardSettingsFeatured.requestFocus()
            }
        } else {
            enterAnimated = false
        }
    }

    fun detach() {
        hubBinding?.root?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        hubBinding = null
        cardsBound = false
        enterAnimated = false
    }

    private fun bindCards(binding: FragmentSettingsHubMobileBinding) {
        binding.cardSettingsFeatured.setOnClickListener {
            ExpMotion.hapticTap(it)
            openTarget(
                SettingsHubTarget.PreferenceScreen(SettingsHubCategories.featuredScreenKey),
                fragment.getString(R.string.platform_settings_title),
            )
        }
        // Hide featured card if the platform screen is missing (unlikely).
        binding.cardSettingsFeatured.visibility =
            if (hasPreferenceScreen(SettingsHubCategories.featuredScreenKey)) View.VISIBLE else View.GONE

        inflateSection(binding.llSettingsHubApp, SettingsHubCategories.appCards())
        inflateSection(binding.llSettingsHubAccount, SettingsHubCategories.accountCards())
        inflateSection(binding.llSettingsHubProject, SettingsHubCategories.projectCards())
        cardsBound = true
    }

    private fun inflateSection(container: LinearLayout, cards: List<SettingsHubCard>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(fragment.requireContext())
        val visibleCards = cards.filter { card ->
            when (val target = card.target) {
                is SettingsHubTarget.PreferenceScreen -> hasPreferenceScreen(target.key)
                else -> true
            }
        }
        visibleCards.forEachIndexed { index, card ->
            val row = inflater.inflate(R.layout.item_settings_hub_card, container, false)
            row.findViewById<TextView>(R.id.tv_settings_hub_card_title).setText(card.titleRes)
            row.findViewById<TextView>(R.id.tv_settings_hub_card_summary).setText(card.summaryRes)
            row.findViewById<ImageView>(R.id.iv_settings_hub_card_icon).apply {
                setImageResource(card.iconRes)
                imageTintList = ContextCompat.getColorStateList(
                    context,
                    R.color.support_accent,
                )
            }
            row.setOnClickListener {
                ExpMotion.hapticTap(it)
                openTarget(card.target, fragment.getString(card.titleRes))
            }
            container.addView(row)
            if (ExperimentalMobileDesign.enabled()) {
                row.alpha = 0f
                row.translationY = 18f * fragment.resources.displayMetrics.density
                row.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(36L * index)
                    .setDuration(260L)
                    .start()
            }
        }
    }

    private fun hasPreferenceScreen(key: String): Boolean =
        fragment.findPreference<androidx.preference.Preference>(key) != null

    private fun openTarget(target: SettingsHubTarget, title: String) {
        when (target) {
            is SettingsHubTarget.PreferenceScreen -> onOpenPreferenceScreen(target.key, title)
            SettingsHubTarget.Support -> onOpenSupport()
            SettingsHubTarget.About -> onOpenAbout()
        }
    }

    private fun playEnterAnimation(binding: FragmentSettingsHubMobileBinding) {
        ExpMotion.startAnimation(binding.svSettingsHub, R.anim.support_fade_slide_up)
    }
}
