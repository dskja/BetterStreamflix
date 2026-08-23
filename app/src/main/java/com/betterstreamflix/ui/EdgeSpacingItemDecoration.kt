package com.betterstreamflix.ui

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Edge spacing helper for RecyclerViews — adds proper spacing
 * for TV and mobile layouts.
 */
class EdgeSpacingItemDecoration(
    private val context: Context,
    private val horizontalSpacingDp: Int = 16,
    private val verticalSpacingDp: Int = 8,
    private val edgePaddingDp: Int = 24,
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val horizontalPx = dpToPx(horizontalSpacingDp)
        val verticalPx = dpToPx(verticalSpacingDp)
        val edgePx = dpToPx(edgePaddingDp)

        val position = parent.getChildAdapterPosition(view)
        val isFirst = position == 0
        val isLast = position == state.itemCount - 1

        outRect.left = if (isFirst) edgePx else horizontalPx / 2
        outRect.right = if (isLast) edgePx else horizontalPx / 2
        outRect.top = verticalPx / 2
        outRect.bottom = verticalPx / 2
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
