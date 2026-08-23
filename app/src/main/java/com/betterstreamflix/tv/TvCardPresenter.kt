package com.betterstreamflix.tv

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.betterstreamflix.utils.AppConfig

/**
 * TV card presenter — builds and manages Leanback card views for
 * TV layout content browsing.
 */
class TvCardPresenter(
    private val imageWidth: Int = 200,
    private val imageHeight: Int = 300,
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            cardType = BaseCardView.CARD_TYPE_INFO_UNDER
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(imageWidth, imageHeight)
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardView = viewHolder.view as ImageCardView
        when (item) {
            is TvCardItem -> {
                cardView.titleText = item.title
                cardView.contentText = item.subtitle
                item.imageUrl?.let { cardView.setMainImageScaleType(android.widget.ImageView.ScaleType.FIT_CENTER) }
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.mainImage = null
        cardView.titleText = null
        cardView.contentText = null
    }

    data class TvCardItem(
        val id: String,
        val title: String,
        val subtitle: String,
        val imageUrl: String?,
        val badgeText: String?,
    )

    companion object {
        /**
         * Get optimal card dimensions for the current TV layout.
         */
        fun getOptimalCardDimensions(context: Context): Pair<Int, Int> {
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            // For TV, show 5-6 cards per row
            return when {
                screenWidth >= 1920 -> 240 to 360 // 1080p TV
                screenWidth >= 1280 -> 200 to 300 // 720p TV
                else -> 160 to 240
            }
        }
    }
}
