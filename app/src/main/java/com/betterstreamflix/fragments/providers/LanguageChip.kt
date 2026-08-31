package com.betterstreamflix.fragments.providers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.betterstreamflix.R

data class LanguageChip(
    val code: String?,
    val name: String,
    var isSelected: Boolean = false,
)

class LanguageChipAdapter(
    private val onChipClicked: (LanguageChip) -> Unit,
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
