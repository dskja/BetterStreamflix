package com.betterstreamflix.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

/**
 * Dialog helper for confirmation dialogs with consistent styling.
 */
object DialogHelper {

    /**
     * Show a confirmation dialog with Yes/No buttons.
     */
    fun showConfirmation(
        context: Context,
        title: String,
        message: String,
        positiveButtonText: String = "Yes",
        negativeButtonText: String = "No",
        onPositive: () -> Unit,
        onNegative: (() -> Unit)? = null,
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { _, _ -> onPositive() }
            .setNegativeButton(negativeButtonText) { _, _ -> onNegative?.invoke() }
            .show()
    }

    /**
     * Show a simple info dialog with a single OK button.
     */
    fun showInfo(
        context: Context,
        title: String,
        message: String,
        onDismiss: () -> Unit = {},
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> onDismiss() }
            .show()
    }

    /**
     * Show a dialog with a list of options.
     */
    fun <T> showOptions(
        context: Context,
        title: String,
        options: List<T>,
        optionLabel: (T) -> String,
        onSelect: (T) -> Unit,
    ) {
        val labels = options.map(optionLabel).toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(labels) { _, which -> onSelect(options[which]) }
            .show()
    }

    /**
     * Fragment extension for confirmation dialog.
     */
    fun Fragment.confirm(
        title: String,
        message: String,
        onConfirm: () -> Unit,
    ) {
        showConfirmation(requireContext(), title, message, onPositive = onConfirm)
    }
}
