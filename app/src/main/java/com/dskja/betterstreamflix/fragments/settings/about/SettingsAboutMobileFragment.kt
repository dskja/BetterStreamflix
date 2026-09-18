package com.dskja.betterstreamflix.fragments.settings.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.dskja.betterstreamflix.BuildConfig
import com.dskja.betterstreamflix.databinding.FragmentAboutMobileBinding
import com.dskja.betterstreamflix.support.SupportLinkOpener
import com.dskja.betterstreamflix.support.SupportProvider
import com.dskja.betterstreamflix.support.SupportUrls

class SettingsAboutMobileFragment : Fragment() {

    private var _binding: FragmentAboutMobileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAboutMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvAboutVersion.text = getString(
            com.dskja.betterstreamflix.R.string.settings_about_version_name,
            BuildConfig.VERSION_NAME,
        )

        binding.btnAboutBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnAboutGithub.setOnClickListener {
            SupportLinkOpener.open(requireContext(), SupportUrls.GITHUB_REPOSITORY_URL)
        }
        binding.btnAboutBmc.setOnClickListener {
            SupportLinkOpener.openProvider(requireContext(), SupportProvider.BUY_ME_A_COFFEE)
        }
        binding.btnAboutSponsors.setOnClickListener {
            SupportLinkOpener.openProvider(requireContext(), SupportProvider.GITHUB_SPONSORS)
        }
        binding.btnAboutPatreon.setOnClickListener {
            SupportLinkOpener.openProvider(requireContext(), SupportProvider.PATREON)
        }
        binding.btnAboutDiscord.setOnClickListener {
            SupportLinkOpener.openProvider(requireContext(), SupportProvider.DISCORD)
        }
        binding.btnAboutTelegram.setOnClickListener {
            SupportLinkOpener.openTelegram(requireContext())
        }
        binding.btnAboutUpstream.setOnClickListener {
            SupportLinkOpener.open(requireContext(), SupportUrls.UPSTREAM_REPOSITORY_URL)
        }
        binding.btnAboutSupportHub.setOnClickListener {
            runCatching { findNavController().navigate(com.dskja.betterstreamflix.R.id.support) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
