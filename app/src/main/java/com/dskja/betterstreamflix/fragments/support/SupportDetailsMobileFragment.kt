package com.dskja.betterstreamflix.fragments.support

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.dskja.betterstreamflix.databinding.FragmentSupportDetailsMobileBinding
import com.dskja.betterstreamflix.support.SupportLinkOpener
import com.dskja.betterstreamflix.support.SupportProvider

class SupportDetailsMobileFragment : Fragment() {

    private var _binding: FragmentSupportDetailsMobileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSupportDetailsMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnSupportDetailsBack.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.btnSupportDetailsCta.setOnClickListener {
            SupportLinkOpener.openProvider(requireContext(), SupportProvider.BUY_ME_A_COFFEE)
        }
        binding.btnSupportDetailsPatreon.setOnClickListener {
            SupportLinkOpener.openProvider(requireContext(), SupportProvider.PATREON)
        }
        binding.btnSupportDetailsSponsors.setOnClickListener {
            SupportLinkOpener.openProvider(requireContext(), SupportProvider.GITHUB_SPONSORS)
        }
        binding.btnSupportDetailsDiscord.setOnClickListener {
            SupportLinkOpener.openProvider(requireContext(), SupportProvider.DISCORD)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
