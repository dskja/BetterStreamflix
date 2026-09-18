package com.dskja.betterstreamflix.fragments.support

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.dskja.betterstreamflix.databinding.FragmentSupportPreviewMobileBinding
import com.dskja.betterstreamflix.support.SupportUiBinder

/**
 * Internal component showcase — only reachable when Experimental UI is enabled.
 */
class SupportPreviewMobileFragment : Fragment() {

    private var _binding: FragmentSupportPreviewMobileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSupportPreviewMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnSupportPreviewBack.setOnClickListener {
            findNavController().navigateUp()
        }
        SupportUiBinder.bindProviderCards(
            requireContext(),
            binding.llPreviewProviders,
            horizontal = false,
            animate = true,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
