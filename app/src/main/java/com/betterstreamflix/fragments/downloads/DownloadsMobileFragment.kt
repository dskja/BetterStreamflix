package com.betterstreamflix.fragments.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.betterstreamflix.R
import com.betterstreamflix.databinding.FragmentDownloadsMobileBinding
import com.betterstreamflix.download.DownloadFeature
import com.betterstreamflix.download.DownloadManager

class DownloadsMobileFragment : Fragment() {

    private var _binding: FragmentDownloadsMobileBinding? = null
    private val binding get() = _binding!!

    private val adapter = DownloadsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDownloadsMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnDownloadsBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.rvDownloads.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDownloads.adapter = adapter
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val tasks = DownloadFeature.list(requireContext())
        adapter.submit(tasks)
        val isEmpty = tasks.isEmpty()
        binding.tvDownloadsEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvDownloads.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class DownloadsAdapter : RecyclerView.Adapter<DownloadsAdapter.Holder>() {
        private var items: List<DownloadManager.DownloadTask> = emptyList()

        fun submit(list: List<DownloadManager.DownloadTask>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val title = view.findViewById<TextView>(android.R.id.text1)
            private val subtitle = view.findViewById<TextView>(android.R.id.text2)

            fun bind(task: DownloadManager.DownloadTask) {
                title.text = task.title
                subtitle.text = task.providerName
            }
        }
    }
}
