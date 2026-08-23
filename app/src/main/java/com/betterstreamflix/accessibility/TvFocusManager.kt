package com.betterstreamflix.accessibility

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Focus manager for TV navigation — handles focus traversal order
 * and ensures proper focus restoration.
 */
class TvFocusManager {

    private var lastFocusedView: View? = null

    /**
     * Save the currently focused view.
     */
    fun saveFocus() {
        lastFocusedView = View::class.java.let { currentFocusView }
    }

    /**
     * Restore focus to the previously focused view.
     */
    fun restoreFocus(): Boolean {
        val view = lastFocusedView ?: return false
        if (view.isAttachedToWindow && view.visibility == View.VISIBLE) {
            view.requestFocus()
            return true
        }
        return false
    }

    /**
     * Focus the first focusable child of a RecyclerView.
     */
    fun focusFirstItem(recyclerView: RecyclerView) {
        if (recyclerView.adapter?.itemCount == 0) return
        recyclerView.post {
            val firstChild = recyclerView.layoutManager?.findViewByPosition(0)
            firstChild?.requestFocus()
        }
    }

    /**
     * Focus a specific position in a RecyclerView.
     */
    fun focusPosition(recyclerView: RecyclerView, position: Int) {
        recyclerView.post {
            recyclerView.layoutManager?.findViewByPosition(position)?.requestFocus()
        }
    }

    companion object {
        @Volatile
        private var currentFocusView: View? = null

        /**
         * Track the current focused view globally.
         */
        fun trackFocus(view: View?) {
            currentFocusView = view
        }

        /**
         * Get the last focused view.
         */
        fun getLastFocusedView(): View? = currentFocusView
    }
}
