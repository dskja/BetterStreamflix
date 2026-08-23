package com.betterstreamflix.tv

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper

/**
 * TV details builder — builds the details page for TV layouts
 * using Leanback details fragment.
 */
object TvDetailsBuilder {

    /**
     * Build a details overview row.
     */
    fun buildOverviewRow(
        context: Context,
        item: DetailsItem,
    ): DetailsOverviewRow {
        return DetailsOverviewRow(item).apply {
            title = item.title
            summary = item.overview
            // Set image drawable from URL (would use Glide in real impl)
        }
    }

    /**
     * Create a details row presenter with shared element transition.
     */
    fun createDetailsPresenter(
        context: Context,
        sharedElementHelper: FullWidthDetailsOverviewSharedElementHelper? = null,
    ): FullWidthDetailsOverviewRowPresenter {
        val presenter = FullWidthDetailsOverviewRowPresenter(TvDetailsDescriptionPresenter())

        sharedElementHelper?.let { helper ->
            presenter.setSharedElementEnterTransition(helper, "details_transition")
        }

        presenter.actionsBackgroundColor = android.graphics.Color.parseColor("#1a1a1a")
        presenter.backgroundColor = android.graphics.Color.parseColor("#0d0d0d")

        return presenter
    }

    data class DetailsItem(
        val id: String,
        val title: String,
        val overview: String,
        val posterUrl: String?,
        val backdropUrl: String?,
        val rating: Double,
        val year: String,
        val genres: List<String>,
        val runtime: Int?,
    )
}

/**
 * Custom details description presenter for Leanback.
 */
class TvDetailsDescriptionPresenter : androidx.leanback.widget.AbstractDetailsDescriptionPresenter() {
    override fun onBindDescription(
        viewHolder: androidx.leanback.widget.AbstractDetailsDescriptionPresenter.ViewHolder?,
        item: Any?,
    ) {
        val details = item as? TvDetailsBuilder.DetailsItem ?: return
        viewHolder?.apply {
            title.text = details.title
            subtitle.text = "${details.year} • ${details.rating}/10 • ${details.genres.joinToString(", ")}"
            body.text = details.overview
        }
    }
}
