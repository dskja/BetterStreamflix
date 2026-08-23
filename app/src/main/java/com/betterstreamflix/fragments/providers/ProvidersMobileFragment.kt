package com.betterstreamflix.fragments.providers

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.betterstreamflix.R
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.databinding.FragmentProvidersMobileBinding
import com.betterstreamflix.models.Provider as ModelProvider
import com.betterstreamflix.providers.Provider
import com.betterstreamflix.ui.SpacingItemDecoration
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.dp
import kotlinx.coroutines.launch
import java.util.Locale

class ProvidersMobileFragment : Fragment() {

    private var _binding: FragmentProvidersMobileBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null. View has been destroyed.")

    private val viewModel by viewModels<ProvidersViewModel>()

    private val appAdapter = AppAdapter()
    private lateinit var chipAdapter: LanguageChipAdapter
    private var searchWatcher: TextWatcher? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProvidersMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeSearch()
        initializeChips()
        initializeRecyclerView()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    is ProvidersViewModel.State.Loading -> binding.isLoading.apply {
                        root.visibility = View.VISIBLE
                        pbIsLoading.visibility = View.VISIBLE
                        gIsLoadingRetry.visibility = View.GONE
                    }
                    is ProvidersViewModel.State.SuccessLoading -> {
                        displayProviders(state.providers)
                        binding.isLoading.root.visibility = View.GONE
                    }
                    is ProvidersViewModel.State.FailedLoading -> {
                        if (!isAdded) return@collect
                        binding.isLoading.apply {
                            pbIsLoading.visibility = View.GONE
                            gIsLoadingRetry.visibility = View.VISIBLE
                            btnIsLoadingRetry.setOnClickListener {
                                viewModel.setLanguageFilter(UserPreferences.providerLanguage)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun initializeSearch() {
        searchWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
        }
        binding.etProvidersSearch.addTextChangedListener(searchWatcher)
    }

    private fun initializeChips() {
        chipAdapter = LanguageChipAdapter { chip ->
            chipAdapter.selectAll(chip)
            viewModel.setLanguageFilter(chip.code)
        }
        binding.rvProvidersLanguage.apply {
            adapter = chipAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        chipAdapter.submitList(buildLanguageChips())
    }

    private fun buildLanguageChips(): List<LanguageChip> {
        val languages = Provider.providers.keys
            .distinctBy { it.language }
            .map {
                val locale = Locale.forLanguageTag(it.language)
                LanguageChip(
                    code = it.language,
                    name = locale.getDisplayLanguage(locale)
                        .replaceFirstChar { char -> char.titlecase() },
                )
            }
            .sortedBy { it.name.lowercase() }

        val allChips = mutableListOf<LanguageChip>()
        allChips.add(LanguageChip(null, getString(R.string.providers_all_languages)))
        allChips.add(LanguageChip("favorites", getString(R.string.providers_favorites)))
        allChips.addAll(languages)

        val savedLang = UserPreferences.providerLanguage
        allChips.forEach { chip ->
            chip.isSelected = when {
                chip.code == null && savedLang == null -> true
                chip.code == "favorites" && savedLang == "favorites" -> true
                chip.code == savedLang -> true
                else -> false
            }
        }

        return allChips
    }

    private fun initializeRecyclerView() {
        binding.rvProviders.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            addItemDecoration(
                SpacingItemDecoration(16.dp(requireContext()))
            )
        }
    }

    private fun displayProviders(providers: List<ModelProvider>) {
        appAdapter.submitList(providers.map { provider ->
            provider.copy(itemType = AppAdapter.Type.PROVIDER_MOBILE_ITEM)
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchWatcher?.let { binding.etProvidersSearch.removeTextChangedListener(it) }
        binding.rvProvidersLanguage.adapter = null
        binding.rvProviders.adapter = null
        _binding = null
    }
}

data class LanguageChip(
    val code: String?,
    val name: String,
    var isSelected: Boolean = false,
)

class LanguageChipAdapter(
    private val onChipClicked: (LanguageChip) -> Unit
) : RecyclerView.Adapter<LanguageChipAdapter.ChipViewHolder>() {

    private val chips = mutableListOf<LanguageChip>()

    fun submitList(list: List<LanguageChip>) {
        chips.clear()
        chips.addAll(list)
        notifyDataSetChanged()
    }

    fun selectAll(selected: LanguageChip) {
        chips.forEach { it.isSelected = it == selected }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChipViewHolder {
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_provider_chip, parent, false) as TextView
        return ChipViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ChipViewHolder, position: Int) {
        holder.bind(chips[position])
    }

    override fun getItemCount() = chips.size

    inner class ChipViewHolder(itemView: TextView) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onChipClicked(chips[pos])
                }
            }
        }

        fun bind(chip: LanguageChip) {
            (itemView as TextView).apply {
                text = chip.name
                isSelected = chip.isSelected
            }
        }
    }
}

