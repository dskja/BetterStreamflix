package com.betterstreamflix.ui

import android.app.Activity
import android.widget.Toast
import androidx.fragment.app.Fragment

/**
 * Centralized toast helper to avoid duplicate toasts and ensure consistent duration.
 */
object ToastHelper {

    private var lastToast: Toast? = null

    /**
     * Show a short toast, cancelling any previous one.
     */
    fun showShort(context: android.content.Context, message: String) {
        lastToast?.cancel()
        lastToast = Toast.makeText(context, message, Toast.LENGTH_SHORT).also { it.show() }
    }

    /**
     * Show a long toast, cancelling any previous one.
     */
    fun showLong(context: android.content.Context, message: String) {
        lastToast?.cancel()
        lastToast = Toast.makeText(context, message, Toast.LENGTH_LONG).also { it.show() }
    }

    /**
     * Show a short toast from a Fragment.
     */
    fun Fragment.toastShort(message: String) {
        showShort(requireContext(), message)
    }

    /**
     * Show a long toast from a Fragment.
     */
    fun Fragment.toastLong(message: String) {
        showLong(requireContext(), message)
    }

    /**
     * Show a short toast from an Activity.
     */
    fun Activity.toastShort(message: String) {
        showShort(this, message)
    }

    /**
     * Show a long toast from an Activity.
     */
    fun Activity.toastLong(message: String) {
        showLong(this, message)
    }
}
