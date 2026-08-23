package com.betterstreamflix.polish

import android.content.Context
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.core.view.isVisible

/**
 * UI state manager — manages loading, content, error, and empty states
 * for screens.
 */
class UiStateManager(
    private val loadingView: View? = null,
    private val contentView: View? = null,
    private val errorView: View? = null,
    private val emptyView: View? = null,
) {
    enum class State { LOADING, CONTENT, ERROR, EMPTY }

    private var currentState: State = State.LOADING

    /**
     * Show loading state.
     */
    fun showLoading() {
        currentState = State.LOADING
        loadingView?.isVisible = true
        contentView?.isVisible = false
        errorView?.isVisible = false
        emptyView?.isVisible = false
    }

    /**
     * Show content state.
     */
    fun showContent() {
        currentState = State.CONTENT
        loadingView?.isVisible = false
        contentView?.isVisible = true
        errorView?.isVisible = false
        emptyView?.isVisible = false
    }

    /**
     * Show error state.
     */
    fun showError(message: String? = null) {
        currentState = State.ERROR
        loadingView?.isVisible = false
        contentView?.isVisible = false
        errorView?.isVisible = true
        emptyView?.isVisible = false
    }

    /**
     * Show empty state.
     */
    fun showEmpty() {
        currentState = State.EMPTY
        loadingView?.isVisible = false
        contentView?.isVisible = false
        errorView?.isVisible = false
        emptyView?.isVisible = true
    }

    /**
     * Get the current state.
     */
    fun getCurrentState(): State = currentState

    /**
     * Set state with a fade animation.
     */
    fun setStateWithAnimation(state: State, context: Context) {
        val fadeOut = AnimationUtils.loadAnimation(context, android.R.anim.fade_out)
        val fadeIn = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)

        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationRepeat(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                when (state) {
                    State.LOADING -> showLoading()
                    State.CONTENT -> showContent()
                    State.ERROR -> showError()
                    State.EMPTY -> showEmpty()
                }
                (contentView ?: loadingView ?: errorView)?.startAnimation(fadeIn)
            }
        })

        (contentView ?: loadingView ?: errorView)?.startAnimation(fadeOut)
    }
}
