package com.betterstreamflix.ui

import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

/**
 * Snackbar helper with action support for undo/retry patterns.
 */
object SnackbarHelper {

    /**
     * Show a simple snackbar.
     */
    fun show(view: View, message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(view, message, duration).show()
    }

    /**
     * Show a snackbar with an action button.
     */
    fun showWithAction(
        view: View,
        message: String,
        actionText: String,
        duration: Int = Snackbar.LENGTH_LONG,
        onAction: () -> Unit,
    ) {
        Snackbar.make(view, message, duration)
            .setAction(actionText) { onAction() }
            .show()
    }

    /**
     * Show a snackbar with an undo action.
     */
    fun showUndo(view: View, message: String, onUndo: () -> Unit) {
        showWithAction(view, message, "Undo", Snackbar.LENGTH_LONG, onUndo)
    }

    /**
     * Show a snackbar with a retry action.
     */
    fun showRetry(view: View, message: String, onRetry: () -> Unit) {
        showWithAction(view, message, "Retry", Snackbar.LENGTH_INDEFINITE, onRetry)
    }

    /**
     * Fragment extension for simple snackbar.
     */
    fun Fragment.snackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        view?.let { show(it, message, duration) }
    }

    /**
     * Fragment extension for snackbar with action.
     */
    fun Fragment.snackbarWithAction(
        message: String,
        actionText: String,
        duration: Int = Snackbar.LENGTH_LONG,
        onAction: () -> Unit,
    ) {
        view?.let { showWithAction(it, message, actionText, duration, onAction) }
    }
}
