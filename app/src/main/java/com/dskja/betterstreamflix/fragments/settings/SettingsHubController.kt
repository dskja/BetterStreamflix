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
 * Support-style cinematic hub overlay for Settings root and Integrations
 * (screen_platform) when experimental design is enabled.
 */
internal class SettingsHubController(
    private val fragment: PreferenceFragmentCompat,
    private val currentRootKey: () -> String?,
    private val onOpenPreferenceScreen: (key: String, title: String) -> Unit,
    private val onOpenSupport: () -> Unit,
    private val onOpenAbout: () -> Unit,
) {
    private var hubBinding: FragmentSettingsHubMobileBinding? = null
    private var boundMode: HubMode? = null
    private var enterAnimated = false

    private enum class HubMode { ROOT, PLATFORM }

    fun attach(root: View) {
        if (!ExperimentalMobileDesign.enabled()) {
            detach()
            return
        }
        val parent = root as? ViewGroup ?: return
        if (hubBinding == null) {
            hubBinding = FragmentSettingsHubMobileBinding.inflate(
                LayoutInflater.from(fragment.requireContext()),
                parent,
                true,
            )
            enterAnimated = false
        }
        updateVisibility()
    }

    fun updateVisibility() {
        val binding = hubBinding
        if (!ExperimentalMobileDesign.enabled()) {
            detach()
            return
        }
        if (binding == null) return

        val key = currentRootKey()
        val mode = when (key) {
            null -> HubMode.ROOT
            "screen_platform" -> HubMode.PLATFORM
            else -> null
        }
        val visible = mode != null
        binding.root.visibility = if (visible) View.VISIBLE else View.GONE
        fragment.listView?.apply {
            visibility = if (visible) View.GONE else View.VISIBLE
            setBackgroundColor(ContextCompat.getColor(context, R.color.support_bg))
            if (!visible) post { requestFocus() }
        }
        if (mode != null) {
            val needsBind = boundMode != mode || mode == HubMode.PLATFORM
            if (needsBind) {
                bindMode(binding, mode)
                if (boundMode != mode) enterAnimated = false
                boundMode = mode
            }
            if (!enterAnimated) {
                ExpMotion.startAnimation(binding.svSettingsHub, R.anim.support_fade_slide_up)
                enterAnimated = true
            }
            binding.root.post {
                (binding.cardSettingsFeatured.takeIf { it.visibility == View.VISIBLE }
                    ?: binding.llSettingsHubApp.getChildAt(0))
                    ?.requestFocus()
            }
        } else {
            enterAnimated = false
            boundMode = null
        }
    }

    fun detach() {
        hubBinding?.root?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        hubBinding = null
        boundMode = null
        enterAnimated = false
    }

    private fun bindMode(binding: FragmentSettingsHubMobileBinding, mode: HubMode) {
        when (mode) {
            HubMode.ROOT -> {
                binding.tvSettingsHubEyebrow.setText(R.string.settings_hub_eyebrow)
                binding.tvSettingsHubTitle.setText(R.string.settings_hub_title)
                binding.tvSettingsHubSubtitle.setText(R.string.settings_hub_subtitle)
                binding.cardSettingsFeatured.visibility = View.VISIBLE
                binding.cardSettingsFeatured.setOnClickListener {
                    ExpMotion.hapticTap(it)
                    onOpenPreferenceScreen(
                        SettingsHubCategories.featuredScreenKey,
                        fragment.getString(R.string.platform_settings_title),
                    )
                }
                setSectionLabel(binding, R.id.ll_settings_hub_app, R.string.settings_hub_section_app, true)
                setSectionLabel(binding, R.id.ll_settings_hub_account, R.string.settings_hub_section_account, true)
                setSectionLabel(binding, R.id.ll_settings_hub_project, R.string.settings_hub_section_project, true)
                inflateSettingsCards(binding.llSettingsHubApp, SettingsHubCategories.appCards())
                inflateSettingsCards(binding.llSettingsHubAccount, SettingsHubCategories.accountCards())
                inflateSettingsCards(binding.llSettingsHubProject, SettingsHubCategories.projectCards())
            }
            HubMode.PLATFORM -> {
                binding.tvSettingsHubEyebrow.setText(R.string.settings_hub_featured_badge)
                binding.tvSettingsHubTitle.setText(R.string.platform_settings_title)
                binding.tvSettingsHubSubtitle.text = PlatformHubCategories.hubSubtitle(fragment.requireContext())
                binding.cardSettingsFeatured.visibility = View.GONE
                setSectionLabel(binding, R.id.ll_settings_hub_app, R.string.platform_hub_section_services, true)
                setSectionLabel(binding, R.id.ll_settings_hub_account, 0, false)
                setSectionLabel(binding, R.id.ll_settings_hub_project, 0, false)
                inflatePlatformCards(binding.llSettingsHubApp)
                binding.llSettingsHubAccount.removeAllViews()
                binding.llSettingsHubProject.removeAllViews()
            }
        }
    }

    private fun setSectionLabel(
        binding: FragmentSettingsHubMobileBinding,
        sectionListId: Int,
        titleRes: Int,
        visible: Boolean,
    ) {
        val list = binding.root.findViewById<View>(sectionListId) ?: return
        val label = findPreviousTextSibling(list)
        label?.visibility = if (visible) View.VISIBLE else View.GONE
        list.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible && titleRes != 0) {
            label?.setText(titleRes)
        }
    }

    private fun findPreviousTextSibling(view: View): TextView? {
        val parent = view.parent as? ViewGroup ?: return null
        val index = parent.indexOfChild(view)
        if (index <= 0) return null
        return parent.getChildAt(index - 1) as? TextView
    }

    private fun inflatePlatformCards(container: LinearLayout) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(fragment.requireContext())
        val context = fragment.requireContext()
        PlatformHubCategories.cards().forEachIndexed { index, card ->
            if (fragment.findPreference<androidx.preference.Preference>(card.screenKey) == null) {
                return@forEachIndexed
            }
            addCard(
                inflater = inflater,
                container = container,
                index = index,
                titleRes = card.titleRes,
                summaryText = PlatformHubCategories.liveSummary(context, card.screenKey),
                iconRes = card.iconRes,
            ) {
                onOpenPreferenceScreen(card.screenKey, fragment.getString(card.titleRes))
            }
        }
    }

    private fun inflateSettingsCards(container: LinearLayout, cards: List<SettingsHubCard>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(fragment.requireContext())
        cards.forEachIndexed { index, card ->
            val show = when (val target = card.target) {
                is SettingsHubTarget.PreferenceScreen ->
                    fragment.findPreference<androidx.preference.Preference>(target.key) != null
                else -> true
            }
            if (!show) return@forEachIndexed
            addCard(
                inflater = inflater,
                container = container,
                index = index,
                titleRes = card.titleRes,
                summaryText = fragment.getString(card.summaryRes),
                iconRes = card.iconRes,
            ) {
                when (val target = card.target) {
                    is SettingsHubTarget.PreferenceScreen ->
                        onOpenPreferenceScreen(target.key, fragment.getString(card.titleRes))
                    SettingsHubTarget.Support -> onOpenSupport()
                    SettingsHubTarget.About -> onOpenAbout()
                }
            }
        }
    }

    private fun addCard(
        inflater: LayoutInflater,
        container: LinearLayout,
        index: Int,
        titleRes: Int,
        summaryText: CharSequence,
        iconRes: Int,
        onClick: () -> Unit,
    ) {
        val row = inflater.inflate(R.layout.item_settings_hub_card, container, false)
        row.findViewById<TextView>(R.id.tv_settings_hub_card_title).setText(titleRes)
        row.findViewById<TextView>(R.id.tv_settings_hub_card_summary).text = summaryText
        row.findViewById<ImageView>(R.id.iv_settings_hub_card_icon).apply {
            setImageResource(iconRes)
            imageTintList = ContextCompat.getColorStateList(context, R.color.support_accent)
        }
        row.setOnClickListener {
            ExpMotion.hapticTap(it)
            onClick()
        }
        container.addView(row)
        if (ExperimentalMobileDesign.enabled()) {
            row.alpha = 0f
            row.translationY = 16f * fragment.resources.displayMetrics.density
            row.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(32L * index)
                .setDuration(240L)
                .start()
        }
    }
}
