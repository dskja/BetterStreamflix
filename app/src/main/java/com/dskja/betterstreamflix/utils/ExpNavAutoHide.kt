package com.dskja.betterstreamflix.utils

import com.dskja.betterstreamflix.R
import androidx.recyclerview.widget.RecyclerView
import androidx.core.widget.NestedScrollView
import android.view.View
import android.view.ViewGroup
import com.dskja.betterstreamflix.activities.main.MainMobileActivity

/**
 * Hides the floating navigation pill while the user scrolls down through
 * content and brings it back on scroll-up. Attaches to the first scrollable
 * view found in a fragment's view tree. Experimental mobile shell only.
 */
object ExpNavAutoHide {

    fun attach(root: View) {
        if (!ExperimentalMobileDesign.enabled()) return
        if (root.getTag(R.id.exp_nav_autohide_tag) == true) return
        root.setTag(R.id.exp_nav_autohide_tag, true)
        findRecyclerView(root)?.let { attachRecyclerView(it) }
            ?: findNestedScrollView(root)?.let { attachScrollView(it) }
    }

    private fun attachRecyclerView(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var accumulated = 0
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                accumulated += dy
                when {
                    accumulated > 48 -> {
                        accumulated = 0
                        setHidden(rv, true)
                    }
                    accumulated < -32 -> {
                        accumulated = 0
                        setHidden(rv, false)
                    }
                }
            }
        })
    }

    private fun attachScrollView(scrollView: NestedScrollView) {
        var lastY = 0
        scrollView.setOnScrollChangeListener { v, _, scrollY, _, _ ->
            val delta = scrollY - lastY
            lastY = scrollY
            when {
                delta > 24 -> setHidden(v, true)
                delta < -16 -> setHidden(v, false)
                scrollY <= 24 -> setHidden(v, false)
            }
        }
    }

    private fun setHidden(from: View, hidden: Boolean) {
        (from.context.toActivity() as? MainMobileActivity)
            ?.setExperimentalNavHidden(hidden)
    }

    private fun findRecyclerView(view: View): RecyclerView? {
        if (view is RecyclerView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findRecyclerView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun findNestedScrollView(view: View): NestedScrollView? {
        if (view is NestedScrollView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findNestedScrollView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
