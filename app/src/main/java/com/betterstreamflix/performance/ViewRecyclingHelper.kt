package com.betterstreamflix.performance

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * View recycling helper — optimizes RecyclerView performance
 * with proper view recycling and prefetch management.
 */
object ViewRecyclingHelper {

    /**
     * Set optimal RecyclerView configuration.
     */
    fun optimizeRecyclerView(
        recyclerView: RecyclerView,
        prefetchEnabled: Boolean = true,
        setItemViewCacheSize: Int = 4,
        hasFixedSize: Boolean = true,
    ) {
        recyclerView.setItemViewCacheSize(setItemViewCacheSize)
        recyclerView.setHasFixedSize(hasFixedSize)
        recyclerView.isDrawingCacheEnabled = true
        recyclerView.drawingCacheQuality = View.DRAWING_CACHE_QUALITY_LOW

        // Enable prefetch if supported
        if (prefetchEnabled) {
            recyclerView.layoutManager?.isItemPrefetchEnabled = true
        }
    }

    /**
     * Recycle a view group's children.
     */
    fun recycleViewChildren(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is ViewGroup) {
                recycleViewChildren(child)
            }
            child.background = null
        }
    }

    /**
     * Clear image views to free bitmap memory.
     */
    fun clearImageViews(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is android.widget.ImageView) {
                child.setImageDrawable(null)
                child.setImageBitmap(null)
            } else if (child is ViewGroup) {
                clearImageViews(child)
            }
        }
    }
}
