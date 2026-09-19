package com.dskja.betterstreamflix.fragments.support

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.dskja.betterstreamflix.databinding.FragmentSupportTvBinding
import com.dskja.betterstreamflix.support.SupportHubBinder
import com.dskja.betterstreamflix.support.SupportLinkOpener
import com.dskja.betterstreamflix.support.SupportPromptPolicy
import com.dskja.betterstreamflix.support.SupportProvider
import com.dskja.betterstreamflix.support.SupportUiBinder
import com.dskja.betterstreamflix.ui.support.SupportThanksDialog

class SupportTvFragment : Fragment() {

    private var _binding: FragmentSupportTvBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSupportTvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SupportPromptPolicy.markHubVisited()

        binding.btnSupportBack.setOnClickListener {
            findNavController().navigateUp()
        }

        val hero = binding.includeSupportHero
        hero.btnSupportHeroPrimary.setOnClickListener {
            SupportLinkOpener.openProvider(requireContext(), SupportProvider.BUY_ME_A_COFFEE)
        }
        // TV has no details destination — scroll to the inline “why” card instead.
        hero.btnSupportHeroSecondary.setOnClickListener {
            binding.svSupport.post {
                binding.tvSupportWhy?.let { why ->
                    binding.svSupport.smoothScrollTo(0, why.top)
                    why.requestFocus()
                }
            }
        }
        hero.btnSupportHeroSecondary.setText(com.dskja.betterstreamflix.R.string.support_hero_cta_secondary)
        SupportUiBinder.applyFocusScale(hero.btnSupportHeroPrimary)
        SupportUiBinder.applyFocusScale(hero.btnSupportHeroSecondary)

        SupportUiBinder.bindProviderCards(
            requireContext(),
            binding.llSupportProviders,
            horizontal = true,
            animate = false,
        )
        SupportHubBinder.bindImpact(requireContext(), binding.llSupportImpact)
        SupportHubBinder.bindFaq(requireContext(), binding.llSupportFaq)

        hero.btnSupportHeroPrimary.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (SupportLinkOpener.consumeAppreciationPending()) {
            runCatching { SupportThanksDialog(requireContext()).show() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
