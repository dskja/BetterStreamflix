package com.betterstreamflix.fragments.providers

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import com.betterstreamflix.R
import com.betterstreamflix.databinding.FragmentAddCustomProviderBinding
import com.betterstreamflix.providers.CustomProviderRegistry
import com.betterstreamflix.providers.Provider

class AddCustomProviderDialogFragment : AppCompatDialogFragment() {

    private var _binding: FragmentAddCustomProviderBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddCustomProviderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.drawable.bg_bottom_sheet_premium)
            setGravity(Gravity.BOTTOM)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            attributes = attributes?.apply {
                gravity = Gravity.BOTTOM
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etName.doAfterTextChanged { hideError() }
        binding.etBaseUrl.doAfterTextChanged { hideError() }
        binding.etLogo.doAfterTextChanged { hideError() }
        binding.etLanguage.doAfterTextChanged { hideError() }

        binding.btnAdd.setOnClickListener { attemptAdd() }
        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun attemptAdd() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val baseUrl = binding.etBaseUrl.text?.toString()?.trim().orEmpty()
        val logo = binding.etLogo.text?.toString()?.trim().orEmpty()
        val language = binding.etLanguage.text?.toString()?.trim().orEmpty()
        val supportsMovies = binding.cbSupportsMovies.isChecked
        val supportsTv = binding.cbSupportsTv.isChecked

        if (name.isEmpty() || baseUrl.isEmpty() || language.isEmpty()) {
            showError(getString(R.string.add_custom_provider_error))
            return
        }

        val nameAlreadyUsed = Provider.findByName(name) != null
            || CustomProviderRegistry.getAll().any { it.name.equals(name, ignoreCase = true) }
            || name.startsWith("tmdb (", ignoreCase = true) && name.endsWith(")")

        if (nameAlreadyUsed) {
            showError(getString(R.string.add_custom_provider_duplicate))
            return
        }

        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(
                ARG_NAME to name,
                ARG_BASE_URL to baseUrl,
                ARG_LOGO to logo,
                ARG_LANGUAGE to language,
                ARG_SUPPORTS_MOVIES to supportsMovies,
                ARG_SUPPORTS_TV to supportsTv,
            )
        )
        dismiss()
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvError.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "add_custom_provider"
        const val ARG_NAME = "name"
        const val ARG_BASE_URL = "base_url"
        const val ARG_LOGO = "logo"
        const val ARG_LANGUAGE = "language"
        const val ARG_SUPPORTS_MOVIES = "supports_movies"
        const val ARG_SUPPORTS_TV = "supports_tv"

        fun newInstance() = AddCustomProviderDialogFragment()
    }
}
