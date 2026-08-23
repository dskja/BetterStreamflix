package com.betterstreamflix.tv

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView

/**
 * TV scroll helper — manages scroll behavior and paging for TV
 * grid and row layouts.
 */
object TvScrollHelper {

    /**
     * Configure a horizontal grid view for TV.
     */
    fun configureHorizontalGrid(gridView: HorizontalGridView) {
        gridView.isFocusable = true
        gridView.isFocusableInTouchMode = true
        gridView.setItemSpacing(16)
        gridView.setRowSpacing(16)
        gridView.isHorizontalScrollBarEnabled = false
    }

    /**
     * Configure a vertical grid view for TV.
     */
    fun configureVerticalGrid(gridView: VerticalGridView) {
        gridView.isFocusable = true
        gridView.isFocusableInTouchMode = true
        gridView.setItemSpacing(16)
        gridView.isVerticalScrollBarEnabled = false
    }

    /**
     * Smooth scroll to a position.
     */
    fun smoothScrollToPosition(recyclerView: RecyclerView, position: Int) {
        recyclerView.smoothScrollToPosition(position)
    }

    /**
     * Get the number of items visible per page.
     */
    fun getVisibleItemCount(context: Context, itemWidthDp: Int): Int {
        val screenWidthDp = context.resources.configuration.screenWidthDp
        return screenWidthDp / itemWidthDp
    }

    /**
     * Calculate scroll offset for centered focus.
     */
    fun getCenterScrollOffset(
        context: Context,
        itemPosition: Int,
        itemWidth: Int,
        totalItems: Int,
    ): Int {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val visibleItems = screenWidth / itemWidth
        val centerOffset = (visibleItems / 2) * itemWidth
        return (itemPosition * itemWidth) - centerOffset
    }
}
