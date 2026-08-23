package com.betterstreamflix.ui

import android.view.View
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView animation helper — configures custom item animations
 * and scroll effects.
 */
object RecyclerViewAnimationHelper {

    /**
     * Configure fade-in item animator.
     */
    fun setupFadeInAnimator(recyclerView: RecyclerView) {
        recyclerView.itemAnimator = object : DefaultItemAnimator() {
            override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
                holder.itemView.alpha = 0f
                holder.itemView.animate().alpha(1f).setDuration(300).start()
                return super.animateAdd(holder)
            }
        }
    }

    /**
     * Configure scale-in item animator.
     */
    fun setupScaleInAnimator(recyclerView: RecyclerView) {
        recyclerView.itemAnimator = object : DefaultItemAnimator() {
            override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
                holder.itemView.scaleX = 0f
                holder.itemView.scaleY = 0f
                holder.itemView.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(250)
                    .start()
                return super.animateAdd(holder)
            }
        }
    }

    /**
     * Configure slide-in from bottom animator.
     */
    fun setupSlideInAnimator(recyclerView: RecyclerView) {
        recyclerView.itemAnimator = object : DefaultItemAnimator() {
            override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
                holder.itemView.translationY = 200f
                holder.itemView.alpha = 0f
                holder.itemView.animate()
                    .translationY(0f).alpha(1f)
                    .setDuration(300)
                    .start()
                return super.animateAdd(holder)
            }
        }
    }

    /**
     * Disable item animations.
     */
    fun disableAnimations(recyclerView: RecyclerView) {
        recyclerView.itemAnimator = null
    }

    /**
     * Set custom animation durations.
     */
    fun setAnimationDurations(recyclerView: RecyclerView, addDuration: Long, changeDuration: Long, moveDuration: Long) {
        (recyclerView.itemAnimator as? DefaultItemAnimator)?.apply {
            this.addDuration = addDuration
            this.changeDuration = changeDuration
            this.moveDuration = moveDuration
        }
    }
}
