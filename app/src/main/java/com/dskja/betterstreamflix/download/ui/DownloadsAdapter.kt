package com.dskja.betterstreamflix.download.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.download.DownloadItemState

class DownloadsAdapter(
    private val onPlay: (DownloadRowUiModel.Item) -> Unit,
    private val onPauseResume: (DownloadRowUiModel.Item) -> Unit,
    private val onRetry: (DownloadRowUiModel.Item) -> Unit,
    private val onDelete: (DownloadRowUiModel.Item) -> Unit,
) : ListAdapter<DownloadRowUiModel, RecyclerView.ViewHolder>(Diff) {

    object Payload {
        const val PROGRESS = "progress"
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DownloadRowUiModel.Header -> TYPE_HEADER
        is DownloadRowUiModel.SeasonPack -> TYPE_PACK
        is DownloadRowUiModel.Item -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(
                inflater.inflate(
                    ExperimentalMobileDesign.layout(
                        R.layout.item_download_header,
                        R.layout.item_download_header_exp,
                    ),
                    parent,
                    false,
                )
            )
            TYPE_PACK -> PackVH(
                inflater.inflate(
                    ExperimentalMobileDesign.layout(
                        R.layout.item_download_season,
                        R.layout.item_download_season_exp,
                    ),
                    parent,
                    false,
                )
            )
            else -> ItemVH(
                inflater.inflate(
                    ExperimentalMobileDesign.layout(
                        R.layout.item_download,
                        R.layout.item_download_exp,
                    ),
                    parent,
                    false,
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DownloadRowUiModel.Header -> (holder as HeaderVH).bind(item)
            is DownloadRowUiModel.SeasonPack -> (holder as PackVH).bind(item)
            is DownloadRowUiModel.Item -> (holder as ItemVH).bind(item)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isNotEmpty() && holder is ItemVH && payloadsContainProgress(payloads)) {
            val item = getItem(position) as? DownloadRowUiModel.Item ?: return
            holder.bindProgress(item)
            holder.bindPrimaryAction(item)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tv_download_header)
        fun bind(item: DownloadRowUiModel.Header) {
            title.text = item.title
        }
    }

    inner class PackVH(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tv_download_season_title)
        private val subtitle: TextView = view.findViewById(R.id.tv_download_season_subtitle)
        private val progress: ProgressBar = view.findViewById(R.id.pb_download_season)
        fun bind(item: DownloadRowUiModel.SeasonPack) {
            val pack = item.pack
            title.text = pack.tvShowTitle
            subtitle.text = itemView.context.getString(
                R.string.downloads_season_progress,
                pack.completedEpisodes,
                pack.totalEpisodes,
            )
            progress.max = pack.totalEpisodes.coerceAtLeast(1)
            progress.progress = pack.completedEpisodes
        }
    }

    inner class ItemVH(view: View) : RecyclerView.ViewHolder(view) {
        private val poster: ImageView = view.findViewById(R.id.iv_download_poster)
        private val title: TextView = view.findViewById(R.id.tv_download_title)
        private val subtitle: TextView = view.findViewById(R.id.tv_download_subtitle)
        private val progressLine: TextView = view.findViewById(R.id.tv_download_progress)
        private val progress: ProgressBar = view.findViewById(R.id.pb_download)
        private val actionPrimary: TextView = view.findViewById(R.id.btn_download_primary)
        private val actionDelete: TextView = view.findViewById(R.id.btn_download_delete)

        fun bind(item: DownloadRowUiModel.Item) {
            title.text = item.entity.title
            subtitle.text = listOfNotNull(
                item.entity.subtitle.takeIf { it.isNotBlank() },
                item.entity.providerName,
                item.entity.serverName.takeIf { it.isNotBlank() },
                item.entity.qualityLabel.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            Glide.with(poster).load(item.entity.posterUrl).centerCrop().into(poster)
            bindProgress(item)
            bindPrimaryAction(item)
            actionDelete.setOnClickListener { onDelete(item) }
            actionPrimary.setOnClickListener {
                when (item.state) {
                    DownloadItemState.COMPLETED -> onPlay(item)
                    DownloadItemState.FAILED -> onRetry(item)
                    DownloadItemState.PAUSED,
                    DownloadItemState.QUEUED,
                    DownloadItemState.PREPARING,
                    DownloadItemState.DOWNLOADING,
                    -> onPauseResume(item)
                    DownloadItemState.REMOVING -> Unit
                }
            }
        }

        fun bindPrimaryAction(item: DownloadRowUiModel.Item) {
            actionPrimary.text = when (item.state) {
                DownloadItemState.COMPLETED -> itemView.context.getString(R.string.downloads_action_play)
                DownloadItemState.FAILED -> itemView.context.getString(R.string.downloads_action_retry)
                DownloadItemState.PAUSED -> itemView.context.getString(R.string.downloads_action_resume)
                else -> itemView.context.getString(R.string.downloads_action_pause)
            }
        }

        fun bindProgress(item: DownloadRowUiModel.Item) {
            val pct = item.entity.progressPct.coerceIn(0, 100)
            progress.isIndeterminate = item.state == DownloadItemState.PREPARING ||
                (item.state.isActive && pct <= 0 && item.entity.bytesDownloaded <= 0L)
            if (!progress.isIndeterminate) {
                progress.progress = pct
            }
            progressLine.text = item.progressText
            progressLine.contentDescription = itemView.context.getString(
                R.string.downloads_progress_a11y,
                pct,
            )
            progress.visibility = if (item.state == DownloadItemState.COMPLETED) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DownloadRowUiModel>() {
        override fun areItemsTheSame(oldItem: DownloadRowUiModel, newItem: DownloadRowUiModel): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DownloadRowUiModel, newItem: DownloadRowUiModel): Boolean =
            oldItem == newItem

        override fun getChangePayload(oldItem: DownloadRowUiModel, newItem: DownloadRowUiModel): Any? {
            if (oldItem is DownloadRowUiModel.Item && newItem is DownloadRowUiModel.Item) {
                if (oldItem.entity.state != newItem.entity.state) return null
                if (oldItem.entity.progressPct != newItem.entity.progressPct ||
                    oldItem.entity.bytesDownloaded != newItem.entity.bytesDownloaded ||
                    oldItem.entity.contentLength != newItem.entity.contentLength ||
                    oldItem.entity.speedBytesPerSec != newItem.entity.speedBytesPerSec ||
                    oldItem.entity.etaSeconds != newItem.entity.etaSeconds
                ) {
                    return Payload.PROGRESS
                }
            }
            return null
        }
    }

    companion object {
        private const val TYPE_HEADER = 1
        private const val TYPE_ITEM = 2
        private const val TYPE_PACK = 3

        private fun payloadsContainProgress(payloads: List<Any>): Boolean {
            for (payload in payloads) {
                when (payload) {
                    Payload.PROGRESS -> return true
                    is Collection<*> -> {
                        if (payload.any { it == Payload.PROGRESS }) return true
                    }
                }
            }
            return false
        }
    }
}
